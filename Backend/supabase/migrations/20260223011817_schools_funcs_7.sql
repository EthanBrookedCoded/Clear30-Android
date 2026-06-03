-- Date-filtered analytics for portal reports
-- Filters check-in data from users.day_info to the specified date range
CREATE OR REPLACE FUNCTION schools.get_school_analytics_for_period(
  p_school_id text,
  p_from_date date,
  p_to_date date
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO 'schools', 'public', 'payment'
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  RETURN (
    WITH all_school_users AS (
      -- Users enrolled by end of period
      SELECT COUNT(DISTINCT u.id)::bigint AS total_enrolled
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.created_at::date <= p_to_date
    ),
    school_users AS (
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND jsonb_typeof(u.day_info) = 'array'
        AND u.day_info != '[]'::jsonb
    ),
    -- day_info is [date_string, checkin_obj, date_string, checkin_obj, ...]
    -- Extract pairs and filter by date range
    checkin_pairs AS (
      SELECT
        su.id,
        su.created_at,
        ((su.day_info->(g.i)) #>> '{}')::date AS checkin_date,
        su.day_info->(g.i + 1) AS checkin_obj
      FROM school_users su,
        LATERAL generate_series(0, GREATEST(jsonb_array_length(su.day_info) - 2, 0), 2) AS g(i)
      WHERE jsonb_typeof(su.day_info->(g.i)) = 'string'
        AND jsonb_typeof(su.day_info->(g.i + 1)) = 'object'
        AND ((su.day_info->(g.i)) #>> '{}')::date >= p_from_date
        AND ((su.day_info->(g.i)) #>> '{}')::date <= p_to_date
    ),
    checkin_stats AS (
      SELECT
        id,
        created_at,
        COUNT(*) AS total_checkins,
        COUNT(*) FILTER (
          WHERE (checkin_obj->>'sober')::boolean = true
        ) AS sober_days
      FROM checkin_pairs
      GROUP BY id, created_at
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
      'active_last_30d',  COUNT(*)
    )
    FROM checkin_stats
  );
END;
$$;

-- Date-filtered assessment stats for portal reports
-- Returns full responses objects so the client controls which fields to aggregate
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

-- Also update the all-time version to return full responses
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
