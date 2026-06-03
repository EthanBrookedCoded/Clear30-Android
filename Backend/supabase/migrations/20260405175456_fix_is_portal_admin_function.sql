-- Drop the broken is_portal_admin() and replace with is_portal_user()
-- The old function referenced a non-existent 'is_admin' column.
-- It was only used in the schools SELECT RLS to let portal users see all school rows.

-- Drop the policy first since it depends on is_portal_admin()
DROP POLICY IF EXISTS "Allow select for anyone" ON schools.schools;

DROP FUNCTION IF EXISTS schools.is_portal_admin();

CREATE FUNCTION schools.is_portal_user()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = schools, public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM schools.portal_users WHERE auth_id = auth.uid()
  );
$$;

GRANT EXECUTE ON FUNCTION schools.is_portal_user() TO anon, authenticated, service_role;

-- Recreate the policy with the renamed function
CREATE POLICY "Allow select for anyone" ON schools.schools
  FOR SELECT
  USING (
    show_in_leaderboard = true
    OR schools.is_platform_admin()
    OR schools.is_portal_user()
  );
