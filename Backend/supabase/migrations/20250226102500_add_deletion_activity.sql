-- This migration adds deletion activity tracking to the existing activity system
-- It builds upon:
-- 1. migrations/20250221113136_add_activities.sql - which created the activities table and initial triggers
-- 2. migrations/20250207000002_add_delete_post_rpc.sql - which created the deleted_posts_log table
-- 3. migrations/20250224064339_add_activity_feed_rpc.sql - which created the get_activity_feed function

-- Add 'deleted' action to the enum
ALTER TYPE community.activity_action ADD VALUE IF NOT EXISTS 'deleted' AFTER 'replied';

-- Create function for post deletion activities
CREATE OR REPLACE FUNCTION community.create_post_deletion_activity()
RETURNS trigger AS $$
BEGIN
    -- Create activity for the post author (they deleted their own post)
    INSERT INTO community.activities (
        actor_id,
        recipient_id,
        entity_type,
        entity_id,
        action
    ) VALUES (
        OLD.user_id,
        OLD.user_id,
        'post'::community.activity_entity_type,
        OLD.id,
        'deleted'::community.activity_action
    );
    
    -- Note: The post is already being logged in deleted_posts_log by the delete_post RPC
    
    RETURN OLD;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent post deletion
        RAISE WARNING 'Error creating deletion activity for post %: %', OLD.id, SQLERRM;
        RETURN OLD;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create function for comment deletion activities
CREATE OR REPLACE FUNCTION community.create_comment_deletion_activity()
RETURNS trigger AS $$
DECLARE
    v_post_author_id text;
    v_post_title text;
BEGIN
    -- Get post information
    SELECT p.user_id, p.title INTO v_post_author_id, v_post_title
    FROM community.posts p
    WHERE p.id = OLD.post_id;
    
    -- Create activity for the comment author (they deleted their own comment)
    INSERT INTO community.activities (
        actor_id,
        recipient_id,
        entity_type,
        entity_id,
        action
    ) VALUES (
        OLD.user_id,
        OLD.user_id,
        'comment'::community.activity_entity_type,
        OLD.id,
        'deleted'::community.activity_action
    );
    
    -- If the comment author is not the post author, notify the post author
    IF OLD.user_id <> v_post_author_id THEN
        INSERT INTO community.activities (
            actor_id,
            recipient_id,
            entity_type,
            entity_id,
            action
        ) VALUES (
            OLD.user_id,
            v_post_author_id,
            'comment'::community.activity_entity_type,
            OLD.id,
            'deleted'::community.activity_action
        );
    END IF;
    
    RETURN OLD;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent comment deletion
        RAISE WARNING 'Error creating deletion activity for comment %: %', OLD.id, SQLERRM;
        RETURN OLD;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create triggers
DROP TRIGGER IF EXISTS before_post_deleted ON community.posts;
CREATE TRIGGER before_post_deleted
    BEFORE DELETE ON community.posts
    FOR EACH ROW
    EXECUTE FUNCTION community.create_post_deletion_activity();

DROP TRIGGER IF EXISTS before_comment_deleted ON community.comments;
CREATE TRIGGER before_comment_deleted
    BEFORE DELETE ON community.comments
    FOR EACH ROW
    EXECUTE FUNCTION community.create_comment_deletion_activity();

-- Update the existing activity feed RPC to handle deletion messages
-- We're only adding the 'deleted' case to the CASE statement, not recreating the entire function
CREATE OR REPLACE FUNCTION community.get_activity_feed(
    start_range int,
    end_range int
)
RETURNS TABLE (
    id uuid,
    created_at timestamptz,
    is_read boolean,
    message text,
    entity_type community.activity_entity_type,
    entity_id uuid,
    post_id uuid,
    action community.activity_action
) 
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = community, public
AS $$
DECLARE
    v_user_id text;
BEGIN
    -- Get the authenticated user's ID
    SELECT u.id INTO v_user_id
    FROM public.users u
    WHERE u.auth_id = auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    RETURN QUERY
    WITH post_titles AS (
        -- Get titles for both existing and deleted posts
        SELECT 
            p.id AS post_id,
            p.title AS title
        FROM community.posts p
        UNION ALL
        SELECT 
            dl.id AS post_id,
            dl.title AS title
        FROM community.deleted_posts_log dl
    )
    SELECT 
        a.id,
        a.created_at,
        a.is_read,
        CASE 
            WHEN a.action = 'created' THEN 
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 
                        'You created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                    ELSE 
                        actor.name || ' created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                END
            WHEN a.action = 'commented' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You commented on your post saying "' || c.body || '"'
                    ELSE actor.name || ' commented on your post saying "' || c.body || '"'
                END
            WHEN a.action = 'replied' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You replied to your comment saying "' || c.body || '"'
                    ELSE actor.name || ' replied to your comment saying "' || c.body || '"'
                END
            WHEN a.action = 'deleted' THEN
                CASE 
                    WHEN a.entity_type = 'post' THEN
                        CASE 
                            WHEN a.actor_id = a.recipient_id THEN 
                                'You deleted your post "' || 
                                COALESCE(
                                    (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                                    'Untitled'
                                ) || '"'
                            ELSE 
                                actor.name || ' deleted their post "' || 
                                COALESCE(
                                    (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                                    'Untitled'
                                ) || '"'
                        END
                    WHEN a.entity_type = 'comment' THEN
                        CASE 
                            WHEN a.actor_id = a.recipient_id THEN 'You deleted your comment'
                            ELSE actor.name || ' deleted their comment on your post'
                        END
                END
        END,
        a.entity_type,
        a.entity_id,
        COALESCE(c.post_id, p.id, 
            -- For deleted posts, the entity_id is the post id
            CASE WHEN a.entity_type = 'post' AND a.action = 'deleted' THEN a.entity_id ELSE NULL END
        ) as post_id,
        a.action
    FROM community.activities a
    JOIN public.users actor ON actor.id = a.actor_id
    LEFT JOIN community.posts p ON 
        (a.entity_type = 'post' AND a.entity_id = p.id)
    LEFT JOIN community.comments c ON 
        (a.entity_type = 'comment' AND a.entity_id = c.id)
    WHERE a.recipient_id = v_user_id
    ORDER BY a.created_at DESC
    OFFSET start_range LIMIT (end_range - start_range + 1);
END;
$$;

-- Add helpful comments
COMMENT ON FUNCTION community.create_post_deletion_activity IS 'Creates an activity record when a post is deleted';
COMMENT ON FUNCTION community.create_comment_deletion_activity IS 'Creates activity records when a comment is deleted'; 