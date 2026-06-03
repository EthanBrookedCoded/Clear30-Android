-- Exclude portal users (school admins/staff) from all school analytics RPCs.
-- Portal users are identified by matching email in schools.portal_users for the given school.

-- 1. get_school_analytics
CREATE OR REPLACE FUNCTION schools.get_school_analytics(p_school_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment'
AS $function$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  RETURN (
    WITH all_school_users AS (
      SELECT COUNT(DISTINCT u.id)::bigint AS total_enrolled
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
    ),
    school_users AS (
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND jsonb_typeof(u.day_info) = 'array'
        AND u.day_info != '[]'::jsonb
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
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
$function$;

-- 2. get_school_analytics_for_period
CREATE OR REPLACE FUNCTION schools.get_school_analytics_for_period(p_school_id text, p_from_date date, p_to_date date)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment'
AS $function$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  RETURN (
    WITH all_school_users AS (
      SELECT COUNT(DISTINCT u.id)::bigint AS total_enrolled
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.created_at::date <= p_to_date
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
    ),
    school_users AS (
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND jsonb_typeof(u.day_info) = 'array'
        AND u.day_info != '[]'::jsonb
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
    ),
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
$function$;

-- 3. get_school_enrollment_trend
CREATE OR REPLACE FUNCTION schools.get_school_enrollment_trend(p_school_id text, p_interval text DEFAULT 'weekly'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment'
AS $function$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;
  IF p_interval = 'monthly' THEN
    RETURN (SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb) FROM (
      SELECT date_trunc('month', u.created_at) AS bucket,
        jsonb_build_object('label', to_char(date_trunc('month', u.created_at), 'Mon YYYY'), 'students', COUNT(*)) AS row
      FROM public.users u JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
      GROUP BY 1) t);
  ELSE
    RETURN (SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb) FROM (
      SELECT date_trunc('week', u.created_at) AS bucket,
        jsonb_build_object('label', to_char(date_trunc('week', u.created_at), 'MM/DD'), 'students', COUNT(*)) AS row
      FROM public.users u JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
      GROUP BY 1) t);
  END IF;
END; $function$;

-- 4. get_school_checkin_trend
CREATE OR REPLACE FUNCTION schools.get_school_checkin_trend(p_school_id text, p_interval text DEFAULT 'weekly'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment'
AS $function$
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
          AND u.day_info != '[]'::jsonb
          AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
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
          AND u.day_info != '[]'::jsonb
          AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
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
END; $function$;

-- 5. get_school_assessment_stats
CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats(p_school_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment', 'programs'
AS $function$
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
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
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
$function$;

-- 6. get_school_assessment_stats_for_period
CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats_for_period(p_school_id text, p_from_date date, p_to_date date)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public', 'payment', 'programs'
AS $function$
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
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
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
$function$;
