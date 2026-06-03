CREATE OR REPLACE FUNCTION schools.log_distribution_click(p_school_id text, p_link_key text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = schools
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied';
  END IF;
  INSERT INTO schools.tracking_link_clicks (school_id, link_key)
  VALUES (p_school_id, p_link_key);
END;
$$;
