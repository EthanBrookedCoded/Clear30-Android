
set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.popin_request_clear()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
  v_user_id text;
BEGIN
  -- Get the user ID from the authenticated user
  SELECT id INTO v_user_id
  FROM public.users
  WHERE auth_id = auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Update all unprocessed pop-in notifications for this user
  UPDATE comms.silent_notifications
  SET processed = true
  WHERE 
    user_id = v_user_id
    AND processed = false
    AND metadata->>'type' = 'popInRequest';
  RETURN;
END;$function$
;


