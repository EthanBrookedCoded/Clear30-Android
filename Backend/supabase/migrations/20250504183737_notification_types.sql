set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.add_group_message_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    -- Declare variables to hold the data we'll fetch
    receiver_record RECORD;
    sender_name TEXT;
    sender_emoji TEXT;
    group_name TEXT;
    message_preview TEXT;
BEGIN
    -- Get all users in the group except the sender
    FOR receiver_record IN (
        SELECT 
            u.id,
            u.fcm_token,
            u.notification_settings
        FROM users u
        INNER JOIN groups.group_members gm ON gm.user_id = u.id
        WHERE gm.group_id = NEW.group_id
        AND u.id != NEW.user_id
    ) LOOP
        -- Skip users without FCM token
        IF receiver_record.fcm_token IS NULL THEN
            CONTINUE;
        END IF;

        -- Skip users who have disabled group notifications
        IF receiver_record.notification_settings IS NULL OR 
           receiver_record.notification_settings->'options' IS NULL OR
           NOT COALESCE((receiver_record.notification_settings->'options'->>'Group Notifications')::boolean, false) THEN
            CONTINUE;
        END IF;

        -- Get the sender's info
        SELECT name, emoji INTO sender_name, sender_emoji
        FROM users
        WHERE id = NEW.user_id;

        -- Get the group name
        SELECT name INTO group_name
        FROM groups.groups
        WHERE id = NEW.group_id;

        -- Create message preview (first 30 chars)
        message_preview := SUBSTRING(NEW.message, 1, 30);
        
        -- Insert a notification into the notifications table
        INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
        VALUES (
            receiver_record.id, 
            'Message from ' || sender_emoji || ' ' || sender_name || ' in ' || group_name,
            message_preview,
            '{"type": "groupChatMessage"}'::jsonb,
            NOW()
        );
    END LOOP;

    RETURN NEW;
END;$function$
;

CREATE OR REPLACE FUNCTION comms.add_group_note_notification()
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
    WHERE id = NEW.to_member_id;

    -- Check if the user exists
    IF to_user_record IS NULL THEN
        RETURN NULL;
    END IF;

    -- Check if FCM token exists
    IF to_user_record.fcm_token IS NULL THEN
        RETURN NULL;
    END IF;

    -- Check if group notifications are enabled
    -- Note: Accessing the nested "options" object first, then "Group Notifications"
    IF to_user_record.notification_settings IS NULL OR 
       to_user_record.notification_settings->'options' IS NULL OR
       NOT COALESCE((to_user_record.notification_settings->'options'->>'Group Notifications')::boolean, false) THEN
        RETURN NULL;
    END IF;

    -- If we got here, both conditions are met, so fetch the sender's info
    SELECT name, emoji INTO from_user_name, from_user_emoji
    FROM users
    WHERE id = NEW.from_member_id;

    -- Insert a notification into the notifications table
    INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
    VALUES (
        NEW.to_member_id, 
        from_user_emoji || ' ' || from_user_name || ' sent you a note!',  -- Format the title
        NEW.message,  -- The body is the note's message content
        '{"type": "groupNote"}'::jsonb,
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;$function$
;


