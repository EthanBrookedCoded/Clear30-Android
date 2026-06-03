set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.delete_user()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    _user_id text;
    _auth_id uuid;
    _user_exists boolean;
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
    
    -- Get the auth_id from public.users before deleting
    SELECT auth_id
    INTO _auth_id
    FROM public.users
    WHERE id = _user_id;
    
    -- Check if auth_id exists
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User does not have an auth_id';
    END IF;
    
    -- Delete from public.users first
    DELETE FROM public.users
    WHERE id = _user_id;
    
    -- Delete from auth.users using the auth_id
    DELETE FROM auth.users
    WHERE id = _auth_id;
    
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error deleting user account: %', SQLERRM;
END;$function$
;


