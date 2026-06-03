-- Fix: The UPDATE WITH CHECK policy was preventing soft-deletes because
-- setting deleted_at caused the row to violate the SELECT policy's
-- deleted_at IS NULL condition during PostgREST's row validation.
-- Change WITH CHECK to true since the USING clause already validates
-- the user has an active role.

DROP POLICY "Users can update creators" ON marketing.creators;

CREATE POLICY "Users can update creators" ON marketing.creators
  FOR UPDATE
  TO authenticated
  USING (( SELECT (marketing.get_user_role() IS NOT NULL)))
  WITH CHECK (true);
