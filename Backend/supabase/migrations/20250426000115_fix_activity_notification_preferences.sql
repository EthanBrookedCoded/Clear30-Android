-- This migration fixes the notification preferences check in the 
-- community.create_notification_from_activity function
-- Adds debug logging to help diagnose issues with notification settings

set check_function_bodies = off;

-- Drop the old trigger first
DROP TRIGGER IF EXISTS after_activity_created ON community.activities;

-- Update the function to improve notification preferences check
CREATE OR REPLACE FUNCTION community.create_notification_from_activity()
RETURNS trigger AS $$
DECLARE
    v_message text;
    v_title text;
    v_post_title text;
    v_actor_name text;
    v_actor_emoji text;
    v_comment_body text;
    v_notification_settings jsonb;
    v_community_notifications_enabled boolean;
    v_debug_info jsonb;
BEGIN
    -- Only proceed if:
    -- 1. There is a recipient (not a broadcast activity)
    -- 2. The recipient is not the same as the actor (don't notify yourself)
    -- 3. The action is either 'commented' or 'replied' (not for post creation)
    IF NEW.recipient_id IS NULL OR 
       NEW.recipient_id = NEW.actor_id OR 
       NEW.action NOT IN ('commented', 'replied') THEN
        RETURN NEW;
    END IF;
    
    -- Check user's notification settings
    SELECT notification_settings INTO v_notification_settings
    FROM public.users
    WHERE id = NEW.recipient_id;

    -- Create debug info
    v_debug_info := jsonb_build_object(
        'recipient_id', NEW.recipient_id,
        'raw_settings', v_notification_settings
    );
    
    -- Check if community notifications are enabled
    -- Structure: {"all": bool, "options": {"Community Notifications": bool, ...}}
    IF v_notification_settings IS NULL THEN
        -- Default to enabled if no settings are specified
        v_community_notifications_enabled := true;
        v_debug_info := v_debug_info || jsonb_build_object('reason', 'settings_null');
    ELSE
        -- FIRST check if the specific Community Notifications option exists and is set
        IF (v_notification_settings->'options'->>'Community Notifications') IS NOT NULL THEN
            -- Always respect the specific setting if it exists
            v_community_notifications_enabled := (v_notification_settings->'options'->>'Community Notifications')::boolean;
            v_debug_info := v_debug_info || jsonb_build_object('reason', 'using_specific_setting');
        ELSE
            -- If specific setting doesn't exist, fall back to the "all" setting
            v_community_notifications_enabled := COALESCE((v_notification_settings->'all')::boolean, true);
            v_debug_info := v_debug_info || jsonb_build_object('reason', 'using_all_setting');
        END IF;
    END IF;
    
    -- Add the final decision to debug info
    v_debug_info := v_debug_info || jsonb_build_object('notifications_enabled', v_community_notifications_enabled);
    
    -- Log the debug info for diagnosing issues
    RAISE LOG 'Notification preferences check: %', v_debug_info;
    
    -- Exit if community notifications are disabled for this user
    IF NOT v_community_notifications_enabled THEN
        RETURN NEW;
    END IF;
    
    -- Get the actor's info for the notification
    SELECT name, emoji 
    INTO v_actor_name, v_actor_emoji
    FROM public.users 
    WHERE id = NEW.actor_id;
    
    -- Set the notification title based on action type
    IF NEW.action = 'commented' THEN
        -- Get the post title
        SELECT p.title INTO v_post_title
        FROM community.posts p
        WHERE p.id = (
            SELECT c.post_id 
            FROM community.comments c 
            WHERE c.id = NEW.entity_id
        );
        
        -- Get the comment body
        SELECT body INTO v_comment_body
        FROM community.comments
        WHERE id = NEW.entity_id;
        
        v_title := v_actor_emoji || ' ' || v_actor_name || ' commented on your post';
        v_message := v_comment_body;
    ELSIF NEW.action = 'replied' THEN
        -- Get the comment body
        SELECT body INTO v_comment_body
        FROM community.comments
        WHERE id = NEW.entity_id;
        
        v_title := v_actor_emoji || ' ' || v_actor_name || ' replied to your comment';
        v_message := v_comment_body;
    ELSE
        -- Fallback (shouldn't reach here due to the conditions above)
        v_title := 'New activity in Clear30';
        v_message := 'Check your activity feed for details';
    END IF;
    
    -- Insert into comms.notifications table
    INSERT INTO comms.notifications (
        user_id, 
        title, 
        body,
        metadata,
        timestamp
    ) VALUES (
        NEW.recipient_id,
        v_title,
        v_message,
        jsonb_build_object(
            'type', 'community',
            'activity_id', NEW.id,
            'entity_type', NEW.entity_type,
            'entity_id', NEW.entity_id,
            'action', NEW.action,
            'debug', v_debug_info
        ),
        NOW()
    );
    
    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent activity creation
        RAISE WARNING 'Error creating notification for activity %: %', NEW.id, SQLERRM;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Recreate the trigger on community.activities
CREATE TRIGGER after_activity_created
    AFTER INSERT ON community.activities
    FOR EACH ROW
    EXECUTE FUNCTION community.create_notification_from_activity();

-- Update the helpful comment
COMMENT ON FUNCTION community.create_notification_from_activity IS 
'Creates a notification in comms.notifications when a new activity is created, respecting the user''s notification preferences.
Always checks specific notification categories first before falling back to the "all" setting.'; 