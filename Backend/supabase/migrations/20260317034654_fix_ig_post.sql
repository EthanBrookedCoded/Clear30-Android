-- Fix: is_portal_admin() checks a non-existent is_admin column, always returns false.
-- Replace with get_portal_school_id() so any portal user can update their own school.

DROP POLICY IF EXISTS portal_admins_update_own_school ON schools.schools;

CREATE POLICY portal_users_update_own_school ON schools.schools
  FOR UPDATE
  USING (
    school_id = schools.get_portal_school_id()
    OR schools.is_platform_admin()
  )
  WITH CHECK (
    school_id = schools.get_portal_school_id()
    OR schools.is_platform_admin()
  );
