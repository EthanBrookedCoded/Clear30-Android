drop function if exists "public"."get_obfuscated_events"();

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_obfuscated_events()
 RETURNS TABLE(anonymized_user_id integer, event_timestamp timestamp with time zone, event_name text, event_extra_data json)
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


