-- Create enums for activity types
CREATE TYPE community.activity_entity_type AS ENUM (
    'post',
    'comment'
);

CREATE TYPE community.activity_action AS ENUM (
    'created',
    'commented',
    'replied'
);

-- Create activities table
CREATE TABLE community.activities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id text NOT NULL REFERENCES public.users(id),
    recipient_id text REFERENCES public.users(id),
    entity_type community.activity_entity_type NOT NULL,
    entity_id uuid NOT NULL,
    action community.activity_action NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    is_read boolean DEFAULT false NOT NULL
);

-- Add indexes for better query performance
CREATE INDEX idx_activities_recipient_unread ON community.activities(recipient_id) WHERE NOT is_read;
CREATE INDEX idx_activities_actor ON community.activities(actor_id);
CREATE INDEX idx_activities_entity ON community.activities(entity_type, entity_id);
CREATE INDEX idx_activities_created_at ON community.activities(created_at DESC);

-- Create function for post activities
CREATE FUNCTION community.create_post_activity()
RETURNS trigger AS $$
BEGIN
    INSERT INTO community.activities (
        actor_id,
        entity_type,
        entity_id,
        action
    ) VALUES (
        NEW.user_id,
        'post'::community.activity_entity_type,
        NEW.id,
        'created'::community.activity_action
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create function for comment activities
CREATE FUNCTION community.create_comment_activity()
RETURNS trigger AS $$
DECLARE
    v_post_author_id text;
    v_parent_comment_author_id text;
BEGIN
    -- Don't create activity if the actor would be the recipient
    IF NEW.parent_comment_id IS NULL THEN
        -- Direct comment on post
        SELECT user_id INTO v_post_author_id
        FROM community.posts
        WHERE id = NEW.post_id;

        -- Only create activity if the commenter is not the post author
        IF NEW.user_id <> v_post_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_post_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'commented'::community.activity_action
            );
        END IF;
    ELSE
        -- Reply to comment
        SELECT user_id INTO v_parent_comment_author_id
        FROM community.comments
        WHERE id = NEW.parent_comment_id;

        -- Only create activity if the replier is not the parent comment author
        IF NEW.user_id <> v_parent_comment_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_parent_comment_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'replied'::community.activity_action
            );
        END IF;
    END IF;

    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent comment creation
        RAISE WARNING 'Error creating activity for comment %: %', NEW.id, SQLERRM;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create triggers
CREATE TRIGGER after_post_created
    AFTER INSERT ON community.posts
    FOR EACH ROW
    EXECUTE FUNCTION community.create_post_activity();

CREATE TRIGGER after_comment_created
    AFTER INSERT ON community.comments
    FOR EACH ROW
    EXECUTE FUNCTION community.create_comment_activity();

-- Add RLS policies
ALTER TABLE community.activities ENABLE ROW LEVEL SECURITY;

-- Users can see activities where they are the recipient or actor
CREATE POLICY "Users can see their own activities"
    ON community.activities
    FOR ALL
    USING (
        actor_id = auth.uid()::text
        OR recipient_id = auth.uid()::text
        OR recipient_id IS NULL  -- Allow viewing broadcast activities (post creations)
    );

-- Grant permissions
GRANT SELECT ON community.activities TO authenticated;
GRANT SELECT ON community.activities TO anon;

-- Add helpful comments
COMMENT ON TABLE community.activities IS 'Stores activity events for posts and comments';
COMMENT ON COLUMN community.activities.entity_type IS 'Type of entity this activity relates to (post or comment)';
COMMENT ON COLUMN community.activities.entity_id IS 'ID of the related entity (post_id or comment_id)';
COMMENT ON COLUMN community.activities.action IS 'Type of action (created, commented, replied)';
COMMENT ON COLUMN community.activities.actor_id IS 'User who performed the action';
COMMENT ON COLUMN community.activities.recipient_id IS 'User who should be notified of this activity (null for broadcast activities)'; 