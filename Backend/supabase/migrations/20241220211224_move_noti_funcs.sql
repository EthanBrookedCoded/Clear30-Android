set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.add_dr_fred_message_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'comms'
AS $function$
DECLARE
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
   INSERT INTO comms.notifications (user_id, title, body, timestamp)
   VALUES (
       NEW.user_id,
       '👨‍⚕️ Dr. Fred sent you a message!',
       NEW.text,  -- The message content
       NOW()
   );

   RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.add_group_note_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'comms'
AS $function$
DECLARE
    -- Declare variables to hold the data we'll fetch
    to_user_fcm_token TEXT;
    from_user_name TEXT;
    from_user_emoji TEXT;
BEGIN
    -- Check if the 'to_member_id' has an fcm_token
    SELECT fcm_token INTO to_user_fcm_token
    FROM users
    WHERE id = NEW.to_member_id;

    -- If there is no fcm_token, just return and do nothing
    IF to_user_fcm_token IS NULL THEN
        RETURN NULL;
    END IF;

    -- Fetch the 'name' and 'emoji' of the 'from_member_id'
    SELECT name, emoji INTO from_user_name, from_user_emoji
    FROM users
    WHERE id = NEW.from_member_id;

    -- Insert a notification into the notifications table
    INSERT INTO comms.notifications (user_id, title, body, timestamp)
    VALUES (
        NEW.to_member_id, 
        from_user_emoji || ' ' || from_user_name || ' sent you a note!',  -- Format the title
        NEW.message,  -- The body is the note's message content
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.add_group_ping_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'comms'
AS $function$
DECLARE
   -- Declare variables to hold the data we'll fetch
   to_user_fcm_token TEXT;
   from_user_name TEXT;
   from_user_emoji TEXT;
BEGIN
   -- Check if the 'to_member_id' has an fcm_token
   SELECT fcm_token INTO to_user_fcm_token
   FROM users
   WHERE id = NEW.to_user_id;

   -- If there is no fcm_token, just return and do nothing
   IF to_user_fcm_token IS NULL THEN
       RETURN NULL;
   END IF;

   -- Fetch the 'name' and 'emoji' of the 'from_member_id'
   SELECT name, emoji INTO from_user_name, from_user_emoji
   FROM users
   WHERE id = NEW.from_user_id;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, timestamp)
   VALUES (
       NEW.to_user_id, 
       from_user_emoji || ' ' || from_user_name || ' check in!',  -- Format the title
       from_user_name || ' wants you to check in on Clear30!',  -- The body is the note's message content
       NOW()  -- Set the current timestamp
   );

   RETURN NEW;
END;
$function$
;

CREATE TRIGGER schedule_dr_fred_message_notification AFTER INSERT ON comms.dr_fred FOR EACH ROW EXECUTE FUNCTION comms.add_dr_fred_message_notification();


drop trigger if exists "schedule_group_note_notification" on "groups"."group_notes";

drop trigger if exists "schedule_group_ping_notification" on "groups"."group_pings";

CREATE TRIGGER schedule_group_note_notification AFTER INSERT ON groups.group_notes FOR EACH ROW EXECUTE FUNCTION comms.add_group_note_notification();

CREATE TRIGGER schedule_group_ping_notification AFTER INSERT ON groups.group_pings FOR EACH ROW EXECUTE FUNCTION comms.add_group_ping_notification();


drop function if exists "public"."add_note_notification"();

drop function if exists "public"."add_ping_notification"();


