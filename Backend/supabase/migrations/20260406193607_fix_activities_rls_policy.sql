-- Fix RLS policy on community.activities
-- The previous policy compared recipient_id/actor_id (which store public.users.id)
-- against auth.uid()::text (the auth UUID). These are different values, so the policy
-- blocked all direct table reads/updates, breaking checkActivity and setActivityIsRead.

DROP POLICY IF EXISTS "Users can see their own activities" ON community.activities;
CREATE POLICY "Users can see their own activities"
    ON community.activities
    FOR ALL
    USING (
        actor_id = (select get_user_id())
        OR recipient_id = (select get_user_id())
    );
