-- ─────────────────────────────────────────────────────────────
-- Migration 003: Assessment Analytics + Enhanced Dashboard Stats
--
-- 1. Updates get_school_analytics: adds total_enrolled + avg_sober_rate
-- 2. New get_school_assessment_stats: pie chart data from clear30 assessments
-- 3. New get_tracking_link_stats_trend: time-bucketed tracking clicks
-- ─────────────────────────────────────────────────────────────

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. Update get_school_analytics
--    Adds:
--      total_enrolled  — all users matching school domain (any check-in status)
--      avg_sober_rate  — avg % of check-in days that were sober (0-100 int)
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 2. New: get_school_assessment_stats
--    Returns pie-chart-ready percentages from each user's first
--    clear30 assessment. Aggregates:
--      triggers         (Trigger field, multi-select)
--      break_reasons    (Break-Reason field, multi-select)
--      use_goals        (Goal30 field, single-select)
--      break_intentions (Then-What field, single-select)
--      days_using       (Days-Using field, single-select, normalized)
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats(p_school_id text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = schools, public, payment, programs
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RETURN '{}'::jsonb;
  END IF;

  RETURN (
    WITH school_user_ids AS (
      SELECT DISTINCT u.id::text AS id
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
    ),
    -- First clear30 assessment per user (baseline)
    first_assessments AS (
      SELECT DISTINCT ON (par.user_id)
        par.user_id,
        par.responses
      FROM programs.program_assessment_responses par
      WHERE par.assessment = 'clear30'
        AND par.user_id IN (SELECT id FROM school_user_ids)
      ORDER BY par.user_id, par.timestamp ASC
    ),

    -- ── Triggers (multi-select) ───────────────────────────────
    trigger_raw AS (
      SELECT elem AS val
      FROM first_assessments,
        jsonb_array_elements_text(
          CASE WHEN jsonb_typeof(responses->'Trigger') = 'array'
               THEN responses->'Trigger'
               ELSE jsonb_build_array(responses->'Trigger')
          END
        ) AS elem
      WHERE responses ? 'Trigger'
        AND responses->'Trigger' != 'null'::jsonb
    ),
    trigger_top5 AS (
      SELECT val, COUNT(*) AS cnt
      FROM trigger_raw WHERE val IS NOT NULL
      GROUP BY val ORDER BY cnt DESC LIMIT 5
    ),

    -- ── Break Reasons (multi-select) ─────────────────────────
    reason_raw AS (
      SELECT elem AS val
      FROM first_assessments,
        jsonb_array_elements_text(
          CASE WHEN jsonb_typeof(responses->'Break-Reason') = 'array'
               THEN responses->'Break-Reason'
               ELSE jsonb_build_array(responses->'Break-Reason')
          END
        ) AS elem
      WHERE responses ? 'Break-Reason'
        AND responses->'Break-Reason' != 'null'::jsonb
    ),
    reason_top5 AS (
      SELECT val, COUNT(*) AS cnt
      FROM reason_raw WHERE val IS NOT NULL
      GROUP BY val ORDER BY cnt DESC LIMIT 5
    ),

    -- ── Use Goals: Goal30 (single-select) ────────────────────
    goal_raw AS (
      SELECT
        CASE WHEN jsonb_typeof(responses->'Goal30') = 'array'
             THEN responses->'Goal30'->>0
             ELSE responses->>'Goal30'
        END AS val
      FROM first_assessments
      WHERE responses ? 'Goal30'
        AND responses->'Goal30' != 'null'::jsonb
    ),
    goal_top5 AS (
      SELECT val, COUNT(*) AS cnt
      FROM goal_raw WHERE val IS NOT NULL
      GROUP BY val ORDER BY cnt DESC LIMIT 5
    ),

    -- ── Break Intentions: Then-What (single-select) ──────────
    intention_raw AS (
      SELECT
        CASE WHEN jsonb_typeof(responses->'Then-What') = 'array'
             THEN responses->'Then-What'->>0
             ELSE responses->>'Then-What'
        END AS val
      FROM first_assessments
      WHERE responses ? 'Then-What'
        AND responses->'Then-What' != 'null'::jsonb
    ),
    intention_top5 AS (
      SELECT val, COUNT(*) AS cnt
      FROM intention_raw WHERE val IS NOT NULL
      GROUP BY val ORDER BY cnt DESC LIMIT 5
    ),

    -- ── Days Using: Days-Using (single-select, normalized) ───
    days_raw AS (
      SELECT
        CASE
          WHEN jsonb_typeof(responses->'Days-Using') = 'array'
          THEN responses->'Days-Using'->>0
          ELSE responses->>'Days-Using'
        END AS raw_val
      FROM first_assessments
      WHERE responses ? 'Days-Using'
        AND responses->'Days-Using' != 'null'::jsonb
    ),
    days_normalized AS (
      SELECT
        CASE raw_val
          WHEN '6-7 days a week' THEN '6–7 days/week'
          WHEN '4-5 days a week' THEN '4–5 days/week'
          WHEN '2-3 days a week' THEN '2–3 days/week'
          WHEN 'About once a week or less' THEN 'Once/week or less'
          WHEN '7' THEN '6–7 days/week'
          WHEN '6' THEN '6–7 days/week'
          WHEN '5' THEN '4–5 days/week'
          WHEN '4' THEN '4–5 days/week'
          WHEN '3' THEN '2–3 days/week'
          WHEN '2' THEN '2–3 days/week'
          WHEN '1' THEN 'Once/week or less'
          ELSE raw_val
        END AS val
      FROM days_raw WHERE raw_val IS NOT NULL
    ),
    days_top AS (
      SELECT val, COUNT(*) AS cnt
      FROM days_normalized WHERE val IS NOT NULL
      GROUP BY val ORDER BY cnt DESC
    )

    SELECT jsonb_build_object(
      'triggers', (
        SELECT COALESCE(
          jsonb_agg(jsonb_build_object(
            'name', val,
            'value', ROUND(cnt * 100.0 / NULLIF(SUM(cnt) OVER (), 0))
          ) ORDER BY cnt DESC),
          '[]'::jsonb
        )
        FROM trigger_top5
      ),
      'break_reasons', (
        SELECT COALESCE(
          jsonb_agg(jsonb_build_object(
            'name', val,
            'value', ROUND(cnt * 100.0 / NULLIF(SUM(cnt) OVER (), 0))
          ) ORDER BY cnt DESC),
          '[]'::jsonb
        )
        FROM reason_top5
      ),
      'use_goals', (
        SELECT COALESCE(
          jsonb_agg(jsonb_build_object(
            'name', val,
            'value', ROUND(cnt * 100.0 / NULLIF(SUM(cnt) OVER (), 0))
          ) ORDER BY cnt DESC),
          '[]'::jsonb
        )
        FROM goal_top5
      ),
      'break_intentions', (
        SELECT COALESCE(
          jsonb_agg(jsonb_build_object(
            'name', val,
            'value', ROUND(cnt * 100.0 / NULLIF(SUM(cnt) OVER (), 0))
          ) ORDER BY cnt DESC),
          '[]'::jsonb
        )
        FROM intention_top5
      ),
      'days_using', (
        SELECT COALESCE(
          jsonb_agg(jsonb_build_object(
            'name', val,
            'value', ROUND(cnt * 100.0 / NULLIF(SUM(cnt) OVER (), 0))
          ) ORDER BY cnt DESC),
          '[]'::jsonb
        )
        FROM days_top
      )
    )
  );
END;
$$;


-- ─────────────────────────────────────────────────────────────
-- 3. New: get_tracking_link_stats_trend
--    Returns time-bucketed click counts per link_key for
--    the resource downloads chart.
--    Format: [{label: "MM/DD", key1: N, key2: N, ...}]
-- ─────────────────────────────────────────────────────────────

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
          date_trunc('month', clicked_at) AS bucket,
          jsonb_build_object('label', to_char(date_trunc('month', clicked_at), 'Mon YYYY'))
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
          date_trunc('week', clicked_at) AS bucket,
          jsonb_build_object('label', to_char(date_trunc('week', clicked_at), 'MM/DD'))
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

COMMIT;
