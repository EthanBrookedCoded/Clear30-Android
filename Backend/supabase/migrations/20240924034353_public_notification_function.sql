set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.send_notification_public(user_id text, title text, body text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_notifications_count INT;
BEGIN
    -- Check the number of notifications for the user in the last minute
    SELECT COUNT(*)
    INTO recent_notifications_count
    FROM notifications
    WHERE notifications.user_id = send_notification_public.user_id 
    AND timestamp >= NOW() - INTERVAL '1 minute';

    -- If there are more than 30 notifications in the last minute, skip the insert
    IF recent_notifications_count < 30 THEN
        -- Insert a new row into the notifications table
        INSERT INTO notifications (user_id, title, body)
        VALUES (
            send_notification_public.user_id,  -- Explicitly use the function's user_id
            send_notification_public.title,    -- Explicitly use the function's title
            send_notification_public.body     -- Explicitly use the function's body
        );
    END IF;
END;$function$
;


