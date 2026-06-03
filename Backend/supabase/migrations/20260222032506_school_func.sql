CREATE OR REPLACE FUNCTION schools.get_platform_admin_self()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = schools, public
AS $$
BEGIN
  RETURN (
    SELECT row_to_json(pa)::jsonb
    FROM schools.platform_admins pa
    WHERE pa.auth_id = auth.uid()
  );
END;
$$;
