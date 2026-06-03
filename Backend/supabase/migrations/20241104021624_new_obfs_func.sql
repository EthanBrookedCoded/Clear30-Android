set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_obfuscated_events()
 RETURNS TABLE(anonymized_user_id integer, event_timestamp timestamp without time zone, event_name text, event_extra_data jsonb)
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN QUERY
    WITH user_mapping AS (
        SELECT
            user_id,
            ROW_NUMBER() OVER (ORDER BY user_id) AS unique_id
        FROM (
            SELECT DISTINCT user_id
            FROM public.events
            WHERE user_id NOT IN (
                '9142467144',
                '7C4C513AA8994A308250C8E64261F80F',
                '9178640066',
                '9175320623'
            )
        ) AS distinct_users
    )
    SELECT
        um.unique_id::INTEGER AS anonymized_user_id,
        e.timestamp AS event_timestamp,
        e.event AS event_name,
        e.extra_data AS event_extra_data
    FROM public.events e
    JOIN user_mapping um ON e.user_id = um.user_id
    ORDER BY e.timestamp DESC;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_obfuscated_events(start_date timestamp with time zone DEFAULT NULL::timestamp with time zone, end_date timestamp with time zone DEFAULT NULL::timestamp with time zone, limit_num integer DEFAULT 1000, offset_num integer DEFAULT 0)
 RETURNS TABLE(user_id integer, event_timestamp timestamp with time zone, event text, extra_data jsonb)
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
BEGIN
  RETURN QUERY
  WITH user_mapping AS (
    SELECT
      distinct_users.user_id,
      ROW_NUMBER() OVER (ORDER BY distinct_users.user_id) AS unique_id
    FROM (
      SELECT DISTINCT user_id
      FROM public.events
      WHERE user_id NOT IN ('9142467144', '7C4C513AA8994A308250C8E64261F80F', '9178640066', '9175320623')
    ) AS distinct_users
  )
  SELECT
    um.unique_id AS user_id,
    e.timestamp as event_timestamp,
    e.event,
    e.extra_data
  FROM public.events e
  JOIN user_mapping um ON e.user_id = um.user_id
  WHERE (start_date IS NULL OR e.timestamp >= start_date)
    AND (end_date IS NULL OR e.timestamp <= end_date)
  ORDER BY e.timestamp DESC
  LIMIT limit_num
  OFFSET offset_num;
END;
$function$
;


