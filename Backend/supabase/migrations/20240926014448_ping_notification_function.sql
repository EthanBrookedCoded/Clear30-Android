set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.add_ping_notification()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$DECLARE
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
    INSERT INTO notifications (user_id, title, body, timestamp)
    VALUES (
        NEW.to_user_id, 
        from_user_emoji || ' ' || from_user_name || ' check in!',  -- Format the title
        from_user_name || ' wants you to check in on Clear30!',  -- The body is the note's message content
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;$function$
;

create policy "Disable public access"
on "public"."group_pings"
as permissive
for all
to public
using (false);


CREATE TRIGGER send_ping_notification AFTER INSERT ON public.group_pings FOR EACH ROW EXECUTE FUNCTION add_ping_notification();


