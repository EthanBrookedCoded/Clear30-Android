CREATE OR REPLACE FUNCTION schools.get_school_analytics(p_school_id text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER STABLE
SET search_path = schools, public, payment AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;
  RETURN (
    WITH school_users AS (
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND jsonb_typeof(u.day_info) = 'array'
        AND jsonb_array_length(u.day_info) > 0
    ),
    checkin_stats AS (
      SELECT
        su.id, su.created_at,
        COUNT(*) FILTER (WHERE jsonb_typeof(elem) = 'object') AS total_checkins,
        COUNT(*) FILTER (WHERE jsonb_typeof(elem) = 'object' AND (elem->>'sober')::boolean = true) AS sober_days,
        MAX(CASE WHEN jsonb_typeof(elem) = 'string' THEN (elem #>> '{}')::date ELSE NULL END) AS last_checkin_date
      FROM school_users su, jsonb_array_elements(su.day_info) AS elem
      GROUP BY su.id, su.created_at
    )
    SELECT jsonb_build_object(
      'total_students',   COUNT(*),
      'total_checkins',   COALESCE(SUM(total_checkins), 0),
      'total_sober_days', COALESCE(SUM(sober_days), 0),
      'active_last_30d',  COUNT(*) FILTER (WHERE last_checkin_date >= CURRENT_DATE - INTERVAL '30 days')
    ) FROM checkin_stats
  );
END; $$;


CREATE OR REPLACE FUNCTION schools.get_school_checkin_trend(p_school_id text, p_interval text DEFAULT 'weekly')
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER STABLE
SET search_path = schools, public, payment AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;
  IF p_interval = 'monthly' THEN
    RETURN (
      WITH school_users AS (
        SELECT u.id, u.day_info FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
          AND jsonb_typeof(u.day_info) = 'array'
          AND jsonb_array_length(u.day_info) > 0
      ), indexed AS (
        SELECT su.id, elem, jsonb_typeof(elem) AS elem_type,
          LAG(elem) OVER (PARTITION BY su.id ORDER BY idx) AS prev_elem
        FROM school_users su, jsonb_array_elements(su.day_info) WITH ORDINALITY AS t(elem, idx)
      ), pairs AS (
        SELECT (prev_elem #>> '{}')::date AS checkin_date, (elem->>'sober')::boolean AS sober
        FROM indexed WHERE elem_type = 'object' AND prev_elem IS NOT NULL
      )
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT date_trunc('month', checkin_date) AS bucket,
          jsonb_build_object('label', to_char(date_trunc('month', checkin_date), 'Mon YYYY'),
            'checkIns', COUNT(*), 'soberDays', COUNT(*) FILTER (WHERE sober = true)) AS row
        FROM pairs WHERE checkin_date IS NOT NULL GROUP BY 1
      ) t
    );
  ELSE
    RETURN (
      WITH school_users AS (
        SELECT u.id, u.day_info FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
          AND jsonb_typeof(u.day_info) = 'array'
          AND jsonb_array_length(u.day_info) > 0
      ), indexed AS (
        SELECT su.id, elem, jsonb_typeof(elem) AS elem_type,
          LAG(elem) OVER (PARTITION BY su.id ORDER BY idx) AS prev_elem
        FROM school_users su, jsonb_array_elements(su.day_info) WITH ORDINALITY AS t(elem, idx)
      ), pairs AS (
        SELECT (prev_elem #>> '{}')::date AS checkin_date, (elem->>'sober')::boolean AS sober
        FROM indexed WHERE elem_type = 'object' AND prev_elem IS NOT NULL
      )
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT date_trunc('week', checkin_date) AS bucket,
          jsonb_build_object('label', to_char(date_trunc('week', checkin_date), 'MM/DD'),
            'checkIns', COUNT(*), 'soberDays', COUNT(*) FILTER (WHERE sober = true)) AS row
        FROM pairs WHERE checkin_date IS NOT NULL GROUP BY 1
      ) t
    );
  END IF;
END; $$;
