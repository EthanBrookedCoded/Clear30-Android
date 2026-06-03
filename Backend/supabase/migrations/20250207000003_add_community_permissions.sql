-- Grant schema usage to all roles
GRANT USAGE ON SCHEMA community TO anon;
GRANT USAGE ON SCHEMA community TO authenticated;
GRANT USAGE ON SCHEMA community TO authenticator;

-- Grant base SELECT permissions on all community tables
GRANT SELECT ON community.posts TO anon;
GRANT SELECT ON community.post_tags TO anon;
GRANT SELECT ON community.tags TO anon;
GRANT SELECT ON community.reactions TO anon;
GRANT SELECT ON community.comments TO anon;
GRANT SELECT ON community.reported_posts TO anon;

GRANT SELECT ON community.posts TO authenticated;
GRANT SELECT ON community.post_tags TO authenticated;
GRANT SELECT ON community.tags TO authenticated;
GRANT SELECT ON community.reactions TO authenticated;
GRANT SELECT ON community.comments TO authenticated;
GRANT SELECT ON community.reported_posts TO authenticated;

GRANT SELECT ON community.posts TO authenticator;
GRANT SELECT ON community.post_tags TO authenticator;
GRANT SELECT ON community.tags TO authenticator;
GRANT SELECT ON community.reactions TO authenticator;
GRANT SELECT ON community.comments TO authenticator;
GRANT SELECT ON community.reported_posts TO authenticator;

-- Grant additional permissions for specific operations (to authenticated and authenticator)
GRANT INSERT, UPDATE ON community.posts TO authenticated;
GRANT INSERT, DELETE ON community.reactions TO authenticated;
GRANT INSERT ON community.comments TO authenticated;
GRANT INSERT ON community.post_tags TO authenticated;
GRANT INSERT ON community.reported_posts TO authenticated;

GRANT INSERT, UPDATE ON community.posts TO authenticator;
GRANT INSERT, DELETE ON community.reactions TO authenticator;
GRANT INSERT ON community.comments TO authenticator;
GRANT INSERT ON community.post_tags TO authenticator;
GRANT INSERT ON community.reported_posts TO authenticator;

-- Grant USAGE on sequences (needed for INSERT operations)
GRANT USAGE ON ALL SEQUENCES IN SCHEMA community TO authenticated;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA community TO authenticator;

-- Add comment for documentation
COMMENT ON SCHEMA community IS 'Schema for community-related features including posts, comments, reactions, and tags. Anonymous users can view content, while authenticated users can create and update their own content.'; 