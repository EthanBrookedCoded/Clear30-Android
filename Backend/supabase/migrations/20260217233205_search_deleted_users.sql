-- Function to search deleted users (admin only)
-- Bypasses RLS "Disable all access" policy on deleted_users table
CREATE OR REPLACE FUNCTION search_deleted_users(search_term text)
RETURNS TABLE (
  id text,
  name text,
  email text,
  phone_number text,
  logging_id jsonb,
  deleted_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Admin check
  IF NOT admin_check() THEN
    RAISE EXCEPTION 'Unauthorized: admin access required';
  END IF;

  RETURN QUERY
  SELECT
    du.id,
    du.data->>'name' AS name,
    du.data->>'email' AS email,
    du.data->>'phone_number' AS phone_number,
    du.data->'logging_id' AS logging_id,
    du.deleted_at
  FROM deleted_users du
  WHERE
    -- Match by ID
    du.id = search_term
    -- Match by name (case-insensitive)
    OR du.data->>'name' ILIKE '%' || search_term || '%'
    -- Match by email (case-insensitive)
    OR du.data->>'email' ILIKE '%' || search_term || '%'
    -- Match by phone number
    OR du.data->>'phone_number' ILIKE '%' || search_term || '%'
    -- Match by logging_id array (check if search_term is contained in the array)
    OR du.data->'logging_id' @> to_jsonb(search_term)
  LIMIT 10;
END;
$$;

-- Grant execute to authenticated users (admin_check inside handles authorization)
GRANT EXECUTE ON FUNCTION search_deleted_users(text) TO authenticated;
