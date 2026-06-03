CREATE POLICY "Allow users to find their invited row by email"
ON schools.portal_users
FOR SELECT
USING (
  auth_id IS NULL
  AND email = (SELECT email FROM auth.users WHERE id = auth.uid())
);

CREATE POLICY "Allow users to claim their invited row"
ON schools.portal_users
FOR UPDATE
USING (
  auth_id IS NULL
  AND email = (SELECT email FROM auth.users WHERE id = auth.uid())
)
WITH CHECK (
  auth_id = auth.uid()
);
