-- 1. Re-grant permissions on platform_admins
grant delete, insert, references, select, trigger, truncate, update on table "schools"."platform_admins" to "anon";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."platform_admins" to "authenticated";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."platform_admins" to "service_role";

-- 2. Re-grant permissions on portal_reports
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_reports" to "anon";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_reports" to "authenticated";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_reports" to "service_role";

-- 3. Re-grant permissions on portal_users
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_users" to "anon";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_users" to "authenticated";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_users" to "service_role";

-- 4. Re-grant permissions on tracking_link_clicks
grant delete, insert, references, select, trigger, truncate, update on table "schools"."tracking_link_clicks" to "anon";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."portal_reports" to "authenticated";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."tracking_link_clicks" to "authenticated";
grant delete, insert, references, select, trigger, truncate, update on table "schools"."tracking_link_clicks" to "service_role";

-- 5. Drop the new policy and restore the old one
drop policy if exists "portal_users_select_own_school" on "schools"."schools";

  create policy "Allow select for authed"
  on "schools"."schools"
  as permissive
  for select
  to public
using (((( SELECT auth.uid() AS uid) IS NOT NULL) AND (show_in_leaderboard = true)));

-- 7. Drop the functions that were created/replaced by this migration
-- (you'll restore the old versions from your previous migration files)
drop function if exists "schools"."get_all_schools_for_admin"();
-- drop function if exists "schools"."get_portal_school_id"();
drop function if exists "schools"."get_school_analytics"(text);
drop function if exists "schools"."get_school_checkin_trend"(text, text);
drop function if exists "schools"."get_school_enrollment_trend"(text, text);
drop function if exists "schools"."get_tracking_link_stats"(text);


CREATE OR REPLACE FUNCTION schools.get_all_schools_for_admin()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public'
AS $function$
BEGIN
  IF NOT schools.is_platform_admin() THEN
    RETURN '[]'::jsonb;
  END IF;
  RETURN (
    SELECT COALESCE(
      jsonb_agg(row_to_json(s) ORDER BY s.short_name),
      '[]'::jsonb
    )
    FROM schools.schools s
  );
END;
$function$
;

CREATE OR REPLACE FUNCTION schools.get_portal_school_id()
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public'
AS $function$
  SELECT school_id
  FROM schools.portal_users
  WHERE auth_id = auth.uid()
  LIMIT 1;
$function$
;

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
END; $function$
;


CREATE OR REPLACE FUNCTION schools.get_school_enrollment_trend(p_school_id text, p_interval text DEFAULT 'weekly')
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER STABLE
SET search_path = schools, public, payment AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;
  IF p_interval = 'monthly' THEN
    RETURN (SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb) FROM (
      SELECT date_trunc('month', u.created_at) AS bucket,
        jsonb_build_object('label', to_char(date_trunc('month', u.created_at), 'Mon YYYY'), 'students', COUNT(*)) AS row
      FROM public.users u JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id GROUP BY 1) t);
  ELSE
    RETURN (SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb) FROM (
      SELECT date_trunc('week', u.created_at) AS bucket,
        jsonb_build_object('label', to_char(date_trunc('week', u.created_at), 'MM/DD'), 'students', COUNT(*)) AS row
      FROM public.users u JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id GROUP BY 1) t);
  END IF;
END; $$;

CREATE OR REPLACE FUNCTION schools.get_tracking_link_stats_trend(
  p_school_id text,
  p_interval  text DEFAULT 'weekly'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = schools, public
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  IF p_interval = 'monthly' THEN
    RETURN (
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          bucket,
          jsonb_build_object('label', to_char(bucket, 'Mon YYYY'))
            || jsonb_object_agg(link_key, cnt) AS row
        FROM (
          SELECT
            date_trunc('month', clicked_at) AS bucket,
            link_key,
            COUNT(*) AS cnt
          FROM schools.tracking_link_clicks
          WHERE school_id = p_school_id
          GROUP BY 1, 2
        ) t
        GROUP BY bucket
      ) t2
    );
  ELSE
    RETURN (
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          bucket,
          jsonb_build_object('label', to_char(bucket, 'MM/DD'))
            || jsonb_object_agg(link_key, cnt) AS row
        FROM (
          SELECT
            date_trunc('week', clicked_at) AS bucket,
            link_key,
            COUNT(*) AS cnt
          FROM schools.tracking_link_clicks
          WHERE school_id = p_school_id
          GROUP BY 1, 2
        ) t
        GROUP BY bucket
      ) t2
    );
  END IF;
END;
$$;
