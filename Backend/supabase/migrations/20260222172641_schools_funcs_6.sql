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
