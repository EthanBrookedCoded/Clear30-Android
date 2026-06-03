alter table "programs"."program_assessment_responses" drop constraint "program_assessment_responses_user_id_fkey";

alter table "programs"."program_assessment_responses" alter column "user_id" drop not null;

alter table "programs"."program_assessment_responses" add constraint "program_assessment_responses_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "programs"."program_assessment_responses" validate constraint "program_assessment_responses_user_id_fkey";


alter table "public"."deleted_users" add column "assessment_responses" jsonb;

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


