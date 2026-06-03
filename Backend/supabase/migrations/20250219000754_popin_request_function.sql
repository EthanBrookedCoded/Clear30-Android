
create policy "Disable all access"
on "comms"."silent_notifications"
as permissive
for all
to public
using (false);



set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.popin_request_schedule(scheduled_for timestamp with time zone)
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
  
  -- Insert the notification
  INSERT INTO comms.silent_notifications
    (
      user_id, 
      scheduled_for, 
      metadata, 
      processed
    )
  VALUES
    (
      v_user_id, 
      popin_request_schedule.scheduled_for, 
      '{"type": "popInRequest"}'::jsonb, 
      false
    );
    
  RETURN;
END;$function$
;


