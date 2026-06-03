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

CREATE OR REPLACE FUNCTION schools.get_school_analytics(p_school_id text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = schools, public, payment
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  RETURN (
    WITH all_school_users AS (
      -- All users whose email matches the school domain
      SELECT COUNT(DISTINCT u.id)::bigint AS total_enrolled
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
    ),
    school_users AS (
      -- Users with at least one check-in entry
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND jsonb_typeof(u.day_info) = 'array'
        AND u.day_info != '[]'::jsonb
    ),
    checkin_stats AS (
      SELECT
        su.id,
        su.created_at,
        COUNT(*) FILTER (WHERE jsonb_typeof(elem) = 'object') AS total_checkins,
        COUNT(*) FILTER (
          WHERE jsonb_typeof(elem) = 'object'
            AND (elem->>'sober')::boolean = true
        ) AS sober_days,
        MAX(
          CASE WHEN jsonb_typeof(elem) = 'string'
            THEN (elem #>> '{}')::date
            ELSE NULL
          END
        ) AS last_checkin_date
      FROM school_users su,
        jsonb_array_elements(su.day_info) AS elem
      GROUP BY su.id, su.created_at
    )
    SELECT jsonb_build_object(
      'total_students',   COUNT(*),
      'total_enrolled',   (SELECT total_enrolled FROM all_school_users),
      'total_checkins',   COALESCE(SUM(total_checkins), 0),
      'total_sober_days', COALESCE(SUM(sober_days), 0),
      'avg_sober_rate',   COALESCE(
                            ROUND(AVG(sober_days::float / NULLIF(total_checkins, 0)) * 100)::int,
                            0
                          ),
      'active_last_30d',  COUNT(*) FILTER (
                            WHERE last_checkin_date >= CURRENT_DATE - INTERVAL '30 days'
                          )
    )
    FROM checkin_stats
  );
END;
$$;

CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats(p_school_id text)
RETURNS jsonb
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO 'schools', 'public', 'payment', 'programs'
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RETURN '[]'::jsonb;
  END IF;

  RETURN (
    WITH school_user_ids AS (
      SELECT DISTINCT u.id::text AS id
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
    ),
    first_assessments AS (
      SELECT DISTINCT ON (par.user_id)
        par.responses
      FROM programs.program_assessment_responses par
      WHERE par.assessment = 'clear30'
        AND par.user_id IN (SELECT id FROM school_user_ids)
      ORDER BY par.user_id, par.timestamp ASC
    )
    SELECT COALESCE(jsonb_agg(responses), '[]'::jsonb)
    FROM first_assessments
  );
END;
$$;

CREATE OR REPLACE FUNCTION schools.get_tracking_link_stats(p_school_id text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER STABLE
SET search_path = schools, public AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;
  RETURN (SELECT COALESCE(jsonb_object_agg(link_key, click_count), '{}'::jsonb)
    FROM (SELECT link_key, COUNT(*) AS click_count FROM schools.tracking_link_clicks
      WHERE school_id = p_school_id GROUP BY link_key) t);
END; $$;

CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats_for_period(
  p_school_id text,
  p_from_date date,
  p_to_date date
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO 'schools', 'public', 'payment', 'programs'
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RETURN '[]'::jsonb;
  END IF;

  RETURN (
    WITH school_user_ids AS (
      SELECT DISTINCT u.id::text AS id
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
    ),
    period_assessments AS (
      SELECT DISTINCT ON (par.user_id)
        par.responses
      FROM programs.program_assessment_responses par
      WHERE par.assessment = 'clear30'
        AND par.user_id IN (SELECT id FROM school_user_ids)
        AND par.timestamp >= p_from_date::timestamptz
        AND par.timestamp < (p_to_date + INTERVAL '1 day')::timestamptz
      ORDER BY par.user_id, par.timestamp ASC
    )
    SELECT COALESCE(jsonb_agg(responses), '[]'::jsonb)
    FROM period_assessments
  );
END;
$$;

