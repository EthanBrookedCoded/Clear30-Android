set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.add_dr_fred_message_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'comms'
AS $function$DECLARE
   to_user_fcm_token TEXT;
BEGIN
   -- Only trigger for outbound messages
   IF NOT NEW.outbound THEN
       RETURN NEW;
   END IF;

   -- Check if the user has an fcm_token
   SELECT fcm_token INTO to_user_fcm_token
   FROM users
   WHERE id = NEW.user_id;

   -- If there is no fcm_token, just return and do nothing
   IF to_user_fcm_token IS NULL THEN
       RETURN NEW;
   END IF;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
   VALUES (
       NEW.user_id,
       '👨‍⚕️ Dr. Fred sent you a message!',
       NEW.text,  -- The message content
       '{"type": "drFred"}'::jsonb,
       NOW()
   );

   RETURN NEW;
END;$function$
;


