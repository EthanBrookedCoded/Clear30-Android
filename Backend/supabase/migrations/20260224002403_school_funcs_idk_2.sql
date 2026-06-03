-- Drop existing portal_users policies first
DROP POLICY IF EXISTS "Allow users to find their invited row by email" ON schools.portal_users;
DROP POLICY IF EXISTS "Allow users to claim their invited row" ON schools.portal_users;
DROP POLICY IF EXISTS "portal_users_select" ON schools.portal_users;
DROP POLICY IF EXISTS "portal_users_update" ON schools.portal_users;
-- Drop any others that show up from: SELECT policyname FROM pg_policies WHERE schemaname='schools' AND tablename='portal_users';

-- SELECT: read your own row, your school's team, or unclaimed rows matching your email
CREATE POLICY "portal_users_select" ON schools.portal_users
FOR SELECT USING (
  auth_id = auth.uid()
  OR school_id IN (
    SELECT pu.school_id FROM schools.portal_users pu WHERE pu.auth_id = auth.uid()
  )
  OR (
    auth_id IS NULL
    AND email = (SELECT email FROM auth.users WHERE id = auth.uid())
  )
);

-- UPDATE: your own row, or claiming an unclaimed invite row
CREATE POLICY "portal_users_update" ON schools.portal_users
FOR UPDATE USING (
  auth_id = auth.uid()
  OR (
    auth_id IS NULL
    AND email = (SELECT email FROM auth.users WHERE id = auth.uid())
  )
) WITH CHECK (
  auth_id = auth.uid()
);
