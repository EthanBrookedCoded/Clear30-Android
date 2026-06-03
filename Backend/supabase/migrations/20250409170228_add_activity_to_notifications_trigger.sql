-- This migration adds a trigger to send push notifications when new activities are created
-- It connects the community.activities table to the comms.notifications table
-- Only notifications for comments and replies (not post creation) are sent
-- The user only gets notifications for activities where they are the recipient but not the actor

set check_function_bodies = off;

-- Create function to generate notifications from activities
CREATE OR REPLACE FUNCTION community.create_notification_from_activity()
RETURNS trigger AS $$
DECLARE
    v_message text;
    v_title text;
    v_post_title text;
    v_actor_name text;
    v_actor_emoji text;
    v_comment_body text;
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
            'action', NEW.action
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

-- Create the trigger on community.activities
CREATE TRIGGER after_activity_created
    AFTER INSERT ON community.activities
    FOR EACH ROW
    EXECUTE FUNCTION community.create_notification_from_activity();

-- Add helpful comment
COMMENT ON FUNCTION community.create_notification_from_activity IS 
'Creates a notification in comms.notifications when a new activity is created that should notify a user';
