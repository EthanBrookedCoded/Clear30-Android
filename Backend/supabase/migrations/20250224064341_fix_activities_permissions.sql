-- Grant schema usage
GRANT USAGE ON SCHEMA community TO authenticated;

-- Grant table permissions
GRANT SELECT, UPDATE ON community.activities TO authenticated;

-- Update RLS policy
DROP POLICY IF EXISTS "Users can see their own activities" ON community.activities;
CREATE POLICY "Users can see their own activities"
    ON community.activities
    FOR ALL
    USING (
        actor_id = auth.uid()::text
        OR recipient_id = auth.uid()::text
    ); 