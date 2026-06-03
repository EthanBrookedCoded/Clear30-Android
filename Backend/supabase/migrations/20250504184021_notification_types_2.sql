set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.add_group_ping_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'comms'
AS $function$DECLARE
   -- Declare variables to hold the data we'll fetch
   to_user_record RECORD;
   from_user_name TEXT;
   from_user_emoji TEXT;
BEGIN
   -- Get the user record with all necessary information in one query
   SELECT 
       fcm_token,
       notification_settings,
       id
   INTO to_user_record
   FROM users
   WHERE id = NEW.to_user_id;

   -- Check if the user exists
   IF to_user_record IS NULL THEN
       RETURN NULL;
   END IF;

   -- Check if FCM token exists
   IF to_user_record.fcm_token IS NULL THEN
       RETURN NULL;
   END IF;

   -- Check if check-in notifications are enabled
   -- Note: Accessing the nested "options" object first, then "Check In Notifications"
   IF to_user_record.notification_settings IS NULL OR 
      to_user_record.notification_settings->'options' IS NULL OR
      NOT COALESCE((to_user_record.notification_settings->'options'->>'Check In Notifications')::boolean, false) THEN
       RETURN NULL;
   END IF;

   -- If we got here, both conditions are met, so fetch the sender's info
   SELECT name, emoji INTO from_user_name, from_user_emoji
   FROM users
   WHERE id = NEW.from_user_id;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
   VALUES (
       NEW.to_user_id, 
       from_user_emoji || ' ' || from_user_name || ' check in!',
       from_user_name || ' wants you to check in on Clear30!',
       '{"type": "groupChatMessage"}'::jsonb,
       NOW()  -- Set the current timestamp
   );

   RETURN NEW;
END;$function$
;


