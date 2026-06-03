drop policy "Allow select for authed" on "schools"."schools";

revoke delete on table "schools"."platform_admins" from "anon";

revoke insert on table "schools"."platform_admins" from "anon";

revoke references on table "schools"."platform_admins" from "anon";

revoke select on table "schools"."platform_admins" from "anon";

revoke trigger on table "schools"."platform_admins" from "anon";

revoke truncate on table "schools"."platform_admins" from "anon";

revoke update on table "schools"."platform_admins" from "anon";

revoke delete on table "schools"."platform_admins" from "authenticated";

revoke insert on table "schools"."platform_admins" from "authenticated";

revoke references on table "schools"."platform_admins" from "authenticated";

revoke select on table "schools"."platform_admins" from "authenticated";

revoke trigger on table "schools"."platform_admins" from "authenticated";

revoke truncate on table "schools"."platform_admins" from "authenticated";

revoke update on table "schools"."platform_admins" from "authenticated";

revoke delete on table "schools"."platform_admins" from "service_role";

revoke insert on table "schools"."platform_admins" from "service_role";

revoke references on table "schools"."platform_admins" from "service_role";

revoke select on table "schools"."platform_admins" from "service_role";

revoke trigger on table "schools"."platform_admins" from "service_role";

revoke truncate on table "schools"."platform_admins" from "service_role";

revoke update on table "schools"."platform_admins" from "service_role";

revoke delete on table "schools"."portal_reports" from "anon";

revoke insert on table "schools"."portal_reports" from "anon";

revoke references on table "schools"."portal_reports" from "anon";

revoke select on table "schools"."portal_reports" from "anon";

revoke trigger on table "schools"."portal_reports" from "anon";

revoke truncate on table "schools"."portal_reports" from "anon";

revoke update on table "schools"."portal_reports" from "anon";

revoke delete on table "schools"."portal_reports" from "authenticated";

revoke insert on table "schools"."portal_reports" from "authenticated";

revoke references on table "schools"."portal_reports" from "authenticated";

revoke select on table "schools"."portal_reports" from "authenticated";

revoke trigger on table "schools"."portal_reports" from "authenticated";

revoke truncate on table "schools"."portal_reports" from "authenticated";

revoke update on table "schools"."portal_reports" from "authenticated";

revoke delete on table "schools"."portal_reports" from "service_role";

revoke insert on table "schools"."portal_reports" from "service_role";

revoke references on table "schools"."portal_reports" from "service_role";

revoke select on table "schools"."portal_reports" from "service_role";

revoke trigger on table "schools"."portal_reports" from "service_role";

revoke truncate on table "schools"."portal_reports" from "service_role";

revoke update on table "schools"."portal_reports" from "service_role";

revoke delete on table "schools"."portal_users" from "anon";

revoke insert on table "schools"."portal_users" from "anon";

revoke references on table "schools"."portal_users" from "anon";

revoke select on table "schools"."portal_users" from "anon";

revoke trigger on table "schools"."portal_users" from "anon";

revoke truncate on table "schools"."portal_users" from "anon";

revoke update on table "schools"."portal_users" from "anon";

revoke delete on table "schools"."portal_users" from "authenticated";

revoke insert on table "schools"."portal_users" from "authenticated";

revoke references on table "schools"."portal_users" from "authenticated";

revoke select on table "schools"."portal_users" from "authenticated";

revoke trigger on table "schools"."portal_users" from "authenticated";

revoke truncate on table "schools"."portal_users" from "authenticated";

revoke update on table "schools"."portal_users" from "authenticated";

revoke delete on table "schools"."portal_users" from "service_role";

revoke insert on table "schools"."portal_users" from "service_role";

revoke references on table "schools"."portal_users" from "service_role";

revoke select on table "schools"."portal_users" from "service_role";

revoke trigger on table "schools"."portal_users" from "service_role";

revoke truncate on table "schools"."portal_users" from "service_role";

revoke update on table "schools"."portal_users" from "service_role";

revoke delete on table "schools"."tracking_link_clicks" from "anon";

revoke insert on table "schools"."tracking_link_clicks" from "anon";

revoke references on table "schools"."tracking_link_clicks" from "anon";

revoke select on table "schools"."tracking_link_clicks" from "anon";

revoke trigger on table "schools"."tracking_link_clicks" from "anon";

revoke truncate on table "schools"."tracking_link_clicks" from "anon";

revoke update on table "schools"."tracking_link_clicks" from "anon";

revoke delete on table "schools"."tracking_link_clicks" from "authenticated";

revoke insert on table "schools"."tracking_link_clicks" from "authenticated";

revoke references on table "schools"."tracking_link_clicks" from "authenticated";

revoke select on table "schools"."tracking_link_clicks" from "authenticated";

revoke trigger on table "schools"."tracking_link_clicks" from "authenticated";

revoke truncate on table "schools"."tracking_link_clicks" from "authenticated";

revoke update on table "schools"."tracking_link_clicks" from "authenticated";

revoke delete on table "schools"."tracking_link_clicks" from "service_role";

revoke insert on table "schools"."tracking_link_clicks" from "service_role";

revoke references on table "schools"."tracking_link_clicks" from "service_role";

revoke select on table "schools"."tracking_link_clicks" from "service_role";

revoke trigger on table "schools"."tracking_link_clicks" from "service_role";

revoke truncate on table "schools"."tracking_link_clicks" from "service_role";

revoke update on table "schools"."tracking_link_clicks" from "service_role";

drop function if exists "schools"."get_platform_admin_self"();

drop function if exists "schools"."get_school_analytics_for_period"(p_school_id text, p_from_date date, p_to_date date);

drop function if exists "schools"."get_school_assessment_stats"(p_school_id text);

drop function if exists "schools"."get_school_assessment_stats_for_period"(p_school_id text, p_from_date date, p_to_date date);

drop function if exists "schools"."get_tracking_link_stats_trend"(p_school_id text, p_interval text);

drop function if exists "schools"."log_distribution_click"(p_school_id text, p_link_key text);

alter table "schools"."schools" drop column "license_renewal_date";

alter table "schools"."schools" drop column "logo_wide_url";

alter table "schools"."schools" drop column "reddit_flair";

alter table "schools"."schools" drop column "sf_symbol_icon";

alter table "schools"."schools" drop column "student_population";

set check_function_bodies = off;

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
    WITH school_users AS (
      SELECT u.id, u.created_at, u.day_info
      FROM public.users u
      JOIN payment.domain_allowlist dal
        ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.day_info IS NOT NULL
        AND jsonb_array_length(u.day_info) > 0
    ),
    checkin_stats AS (
      SELECT
        su.id,
        su.created_at,
        COUNT(*) FILTER (
          WHERE jsonb_typeof(elem) = 'object'
        ) AS total_checkins,
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
      'total_checkins',   COALESCE(SUM(total_checkins), 0),
      'total_sober_days', COALESCE(SUM(sober_days), 0),
      'active_last_30d',  COUNT(*) FILTER (
                            WHERE last_checkin_date >= CURRENT_DATE - INTERVAL '30 days'
                          )
    )
    FROM checkin_stats
  );
END;
$function$
;

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
        SELECT u.id, u.day_info
        FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
          AND u.day_info IS NOT NULL
          AND jsonb_array_length(u.day_info) > 0
      ),
      indexed AS (
        SELECT
          su.id,
          elem,
          jsonb_typeof(elem)                                         AS elem_type,
          LAG(elem) OVER (PARTITION BY su.id ORDER BY idx)          AS prev_elem
        FROM school_users su,
          jsonb_array_elements(su.day_info) WITH ORDINALITY AS t(elem, idx)
      ),
      pairs AS (
        SELECT
          (prev_elem #>> '{}')::date   AS checkin_date,
          (elem->>'sober')::boolean    AS sober
        FROM indexed
        WHERE elem_type = 'object' AND prev_elem IS NOT NULL
      )
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          date_trunc('month', checkin_date) AS bucket,
          jsonb_build_object(
            'label',     to_char(date_trunc('month', checkin_date), 'Mon YYYY'),
            'checkIns',  COUNT(*),
            'soberDays', COUNT(*) FILTER (WHERE sober = true)
          ) AS row
        FROM pairs
        WHERE checkin_date IS NOT NULL
        GROUP BY 1
      ) t
    );
  ELSE
    RETURN (
      WITH school_users AS (
        SELECT u.id, u.day_info
        FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
          AND u.day_info IS NOT NULL
          AND jsonb_array_length(u.day_info) > 0
      ),
      indexed AS (
        SELECT
          su.id,
          elem,
          jsonb_typeof(elem)                                         AS elem_type,
          LAG(elem) OVER (PARTITION BY su.id ORDER BY idx)          AS prev_elem
        FROM school_users su,
          jsonb_array_elements(su.day_info) WITH ORDINALITY AS t(elem, idx)
      ),
      pairs AS (
        SELECT
          (prev_elem #>> '{}')::date   AS checkin_date,
          (elem->>'sober')::boolean    AS sober
        FROM indexed
        WHERE elem_type = 'object' AND prev_elem IS NOT NULL
      )
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          date_trunc('week', checkin_date) AS bucket,
          jsonb_build_object(
            'label',     to_char(date_trunc('week', checkin_date), 'MM/DD'),
            'checkIns',  COUNT(*),
            'soberDays', COUNT(*) FILTER (WHERE sober = true)
          ) AS row
        FROM pairs
        WHERE checkin_date IS NOT NULL
        GROUP BY 1
      ) t
    );
  END IF;
END;
$function$
;

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
    RETURN (
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          date_trunc('month', u.created_at) AS bucket,
          jsonb_build_object(
            'label',    to_char(date_trunc('month', u.created_at), 'Mon YYYY'),
            'students', COUNT(*)
          ) AS row
        FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
        GROUP BY 1
      ) t
    );
  ELSE
    RETURN (
      SELECT COALESCE(jsonb_agg(row ORDER BY bucket), '[]'::jsonb)
      FROM (
        SELECT
          date_trunc('week', u.created_at) AS bucket,
          jsonb_build_object(
            'label',    to_char(date_trunc('week', u.created_at), 'MM/DD'),
            'students', COUNT(*)
          ) AS row
        FROM public.users u
        JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
        WHERE dal.school_id = p_school_id
        GROUP BY 1
      ) t
    );
  END IF;
END;
$function$
;

CREATE OR REPLACE FUNCTION schools.get_tracking_link_stats(p_school_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public'
AS $function$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RAISE EXCEPTION 'Access denied: not a portal user for school %', p_school_id;
  END IF;

  RETURN (
    SELECT COALESCE(
      jsonb_object_agg(link_key, click_count),
      '{}'::jsonb
    )
    FROM (
      SELECT link_key, COUNT(*) AS click_count
      FROM schools.tracking_link_clicks
      WHERE school_id = p_school_id
      GROUP BY link_key
    ) t
  );
END;
$function$
;

CREATE OR REPLACE FUNCTION schools.is_platform_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public'
AS $function$
  SELECT EXISTS (
    SELECT 1 FROM schools.platform_admins WHERE auth_id = auth.uid()
  )
$function$
;

CREATE OR REPLACE FUNCTION schools.is_portal_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'schools', 'public'
AS $function$
  SELECT COALESCE(
    (SELECT is_admin FROM schools.portal_users WHERE auth_id = auth.uid() LIMIT 1),
    false
  );
$function$
;


  create policy "portal_users_select_own_school"
  on "schools"."schools"
  as permissive
  for select
  to public
using (((school_id = schools.get_portal_school_id()) OR schools.is_platform_admin()));



