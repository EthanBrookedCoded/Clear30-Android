-- Add original_user_id column to store the user_id for matching assessments
-- This allows us to preserve the user_id even after the foreign key is set to NULL
alter table "programs"."program_assessment_responses" 
add column "original_user_id" text;

-- Create index for better query performance when matching by original_user_id
create index "program_assessment_responses_original_user_id_idx" 
on "programs"."program_assessment_responses" ("original_user_id");

-- Update the delete_user function to copy user_id to original_user_id before deletion
set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.delete_user()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    _user_id text;
    _auth_id uuid;
    _user_exists boolean;
    _user_data jsonb;
    _assessment_responses jsonb;
BEGIN
    -- Get the current user ID
    _user_id := public.get_user_id();
    
    -- Check if user exists
    IF _user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;
    
    -- Verify user exists in public.users
    SELECT EXISTS (
        SELECT 1 FROM public.users WHERE id = _user_id
    ) INTO _user_exists;
    
    IF NOT _user_exists THEN
        RAISE EXCEPTION 'User not found in public.users table';
    END IF;
    
    -- Get the auth_id and capture ALL user data as JSON
    SELECT auth_id, row_to_json(users.*)::jsonb
    INTO _auth_id, _user_data
    FROM public.users
    WHERE id = _user_id;
    
    -- Check if auth_id exists
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User does not have an auth_id';
    END IF;
    
    -- Copy user_id to original_user_id for all assessment responses before deletion
    -- This preserves the user_id for matching purposes even after the foreign key sets it to NULL
    UPDATE programs.program_assessment_responses
    SET original_user_id = user_id
    WHERE user_id = _user_id AND original_user_id IS NULL;
    
    -- Collect all assessment responses for this user as JSON
    SELECT COALESCE(json_agg(row_to_json(ar.*)), '[]'::json)
    INTO _assessment_responses
    FROM programs.program_assessment_responses ar
    WHERE ar.user_id = _user_id;
    
    -- Archive user data and assessment responses before deletion
    INSERT INTO public.deleted_users (id, data, assessment_responses)
    VALUES (_user_id, _user_data, _assessment_responses);
    
    -- Delete from public.users first
    -- This will trigger SET NULL on program_assessment_responses.user_id
    -- but original_user_id will still contain the user_id for matching
    DELETE FROM public.users
    WHERE id = _user_id;
    
    -- Delete from auth.users using the auth_id
    DELETE FROM auth.users
    WHERE id = _auth_id;
    
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error deleting user account: %', SQLERRM;
END;
$function$
;
