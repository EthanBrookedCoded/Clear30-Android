-- Add RPC function for getting activity feed
CREATE OR REPLACE FUNCTION community.get_activity_feed(
    start_range int,                    -- Start of range (inclusive)
    end_range int                       -- End of range (inclusive)
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
    SELECT 
        a.id,
        a.created_at,
        a.is_read,
        CASE 
            WHEN a.action = 'created' THEN 
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You created a new post "' || p.title || '"'
                    ELSE actor.name || ' created a new post "' || p.title || '"'
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
        END,
        a.entity_type,
        a.entity_id,
        COALESCE(c.post_id, p.id) as post_id,
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

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION community.get_activity_feed TO authenticated;

-- Add helpful comment
COMMENT ON FUNCTION community.get_activity_feed IS 'Returns paginated activity feed for the authenticated user with formatted messages'; 