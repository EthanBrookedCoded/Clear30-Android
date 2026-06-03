-- Update delete_user function to also delete peer support data
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
    VALUES (_user_id, _user_data, _assessment_responses)
    ON CONFLICT (id) DO UPDATE
    SET
        data = EXCLUDED.data,
        assessment_responses = EXCLUDED.assessment_responses,
        deleted_at = NOW();

    -- Delete peer support data for this user
    DELETE FROM comms.peer_messages WHERE user_id = _user_id;
    DELETE FROM comms.peer_conversations WHERE user_id = _user_id;

    -- Delete from public.users only
    -- This will trigger SET NULL on program_assessment_responses.user_id
    -- but original_user_id will still contain the user_id for matching
    DELETE FROM public.users
    WHERE id = _user_id;

    -- DO NOT delete from auth.users - this allows users to log back in
    -- When they sign up again, create_user() will create a new record with
    -- id = _auth_id::text, preserving the same user_id

EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error deleting user account: %', SQLERRM;
END;
$function$;
