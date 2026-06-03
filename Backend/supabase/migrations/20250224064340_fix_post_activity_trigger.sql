-- Drop existing trigger and function
DROP TRIGGER IF EXISTS after_post_created ON community.posts;
DROP FUNCTION IF EXISTS community.create_post_activity();

-- Recreate function with recipient_id
CREATE FUNCTION community.create_post_activity()
RETURNS trigger AS $$
BEGIN
    INSERT INTO community.activities (
        actor_id,
        recipient_id,      -- Adding recipient_id: user sees their own post creation
        entity_type,
        entity_id,
        action
    ) VALUES (
        NEW.user_id,
        NEW.user_id,      -- Same user is both actor and recipient
        'post'::community.activity_entity_type,
        NEW.id,
        'created'::community.activity_action
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Recreate trigger
CREATE TRIGGER after_post_created
    AFTER INSERT ON community.posts
    FOR EACH ROW
    EXECUTE FUNCTION community.create_post_activity();

-- Add helpful comment
COMMENT ON FUNCTION community.create_post_activity IS 'Creates an activity record when a post is created, setting the creator as both actor and recipient'; 