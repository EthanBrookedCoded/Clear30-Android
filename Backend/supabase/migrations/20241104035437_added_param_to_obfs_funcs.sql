drop function if exists "public"."get_obfuscated_events"();

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_obfuscated_events(excluded_users text[] DEFAULT ARRAY[]::text[])
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
            WHERE CASE 
                WHEN array_length(excluded_users, 1) > 0 THEN user_id NOT IN (SELECT unnest(excluded_users))
                ELSE true
            END
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

CREATE OR REPLACE FUNCTION public.get_obfuscated_events_count(excluded_users text[] DEFAULT ARRAY[]::text[])
 RETURNS integer
 LANGUAGE plpgsql
AS $function$
DECLARE
    total_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total_count
    FROM public.events
    WHERE CASE 
        WHEN array_length(excluded_users, 1) > 0 THEN user_id NOT IN (SELECT unnest(excluded_users))
        ELSE true
    END;
    
    RETURN total_count;
END;
$function$
;


