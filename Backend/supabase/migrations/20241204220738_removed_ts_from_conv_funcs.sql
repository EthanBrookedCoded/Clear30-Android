set check_function_bodies = off;

CREATE OR REPLACE FUNCTION programs.convert_day_info_to_legacy(program_info jsonb)
 RETURNS TABLE(start_date timestamp with time zone, days_sober boolean[])
 LANGUAGE sql
AS $function$WITH RECURSIVE 
-- First, convert array to key-value pairs
parsed_json AS (
    SELECT 
        (program_info->>(i*2))::timestamp with time zone as date_key,
        program_info->(i*2 + 1)->>'sober' as sober_status
    FROM generate_series(0, jsonb_array_length(program_info)/2 - 1) as i
    WHERE i*2 < jsonb_array_length(program_info)
),
-- Get sorted dates
dates AS (
    SELECT 
        date_key,
        sober_status
    FROM parsed_json
    ORDER BY date_key
),
-- Generate series of dates from min + 1 day to max date
date_series AS (
    SELECT 
        generate_series(
            (min(date_key)::date + interval '1 day')::date,  -- Add 1 day to minimum date
            max(date_key)::date,
            '1 day'::interval
        )::date as series_date
    FROM dates
),
-- Create the final array with proper ordering and null handling
final_array AS (
    SELECT 
        array_agg(
            CASE 
                -- If no entry exists for this date (left join returned null), use null
                WHEN d.date_key IS NULL THEN NULL
                -- If entry exists but no sober status, use null
                WHEN d.sober_status IS NULL THEN NULL
                WHEN d.sober_status = 'true' THEN TRUE
                WHEN d.sober_status = 'false' THEN FALSE
                ELSE NULL
            END
            ORDER BY ds.series_date
        ) as days_sober,
        (SELECT min(date_key) FROM dates) as start_date  -- Changed this line to get actual first logged date
    FROM date_series ds
    LEFT JOIN dates d ON ds.series_date = d.date_key::date
)
SELECT 
    start_date::timestamp with time zone,
    days_sober
FROM final_array;$function$
;

CREATE OR REPLACE FUNCTION programs.convert_legacy_to_day_info(start_date timestamp with time zone, days_sober boolean[])
 RETURNS jsonb
 LANGUAGE plpgsql
AS $function$BEGIN
    -- Return null if start_date is null
    IF start_date IS NULL THEN
        RETURN NULL;
    END IF;

    RETURN (
        WITH RECURSIVE
        date_array AS (
            -- Generate array of dates starting from start_date
            SELECT 
                i,
                (start_date + (i * interval '1 day'))::timestamp with time zone as date_value,
                -- Shift the days_sober array by 1 to start from the day after start_date
                CASE 
                    WHEN i = 0 THEN NULL  -- Start date has no sober information
                    ELSE days_sober[i]    -- Use i instead of i+1 to shift array access
                END as is_sober
            FROM generate_series(0, COALESCE(array_length(days_sober, 1), 0)) as i
        ),
        array_elements AS (
            SELECT 
                jsonb_build_array(
                    to_char(date_value::date, 'YYYY-MM-DD'),  -- Convert to date and format
                    CASE 
                        WHEN i = 0 OR is_sober IS NULL THEN
                            jsonb_build_object(
                                'customCheckIn', '[]'::jsonb,
                                'loggedSymptoms', '[]'::jsonb
                            )
                        ELSE
                            jsonb_build_object(
                                'sober', is_sober,
                                'customCheckIn', '[]'::jsonb,
                                'loggedSymptoms', '[]'::jsonb
                            )
                    END
                ) as element_pair
            FROM date_array
            WHERE i = 0 OR is_sober IS NOT NULL
        )
        SELECT 
            COALESCE(
                jsonb_agg(value),
                jsonb_build_array(
                    -- Fallback if somehow we get no results
                    to_char(start_date::date, 'YYYY-MM-DD'),  -- Convert to date and format
                    jsonb_build_object(
                        'customCheckIn', '[]'::jsonb,
                        'loggedSymptoms', '[]'::jsonb
                    )
                )
            )
        FROM (
            SELECT jsonb_array_elements(element_pair) as value
            FROM array_elements
        ) sub
    );
END;$function$
;


