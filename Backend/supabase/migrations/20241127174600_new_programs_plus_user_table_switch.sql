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
-- Generate series of dates from min to max date
date_series AS (
    SELECT 
        generate_series(
            min(date_key)::date,
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
        min(ds.series_date) as start_date
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
                days_sober[i+1] as is_sober
            FROM generate_series(0, COALESCE(array_length(days_sober, 1) - 1, 0)) as i
        ),
        array_elements AS (
            SELECT 
                jsonb_build_array(
                    to_char(date_value, 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                    CASE 
                        WHEN is_sober IS NULL THEN
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
                    to_char(start_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
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


alter table "public"."users" add column "day_info" jsonb;

alter table "public"."users" alter column "days_sober" drop not null;

alter table "public"."users" alter column "initial_frequency" drop not null;

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_group(group_id uuid)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'daysSober', COALESCE(
                            (SELECT days_sober FROM programs.convert_day_info_to_legacy(u.day_info)),
                            ARRAY[]::boolean[]
                        ),
                        'initialFrequency', 0.5,
                        'showInRank', u.show_in_group_rank,
                        'startDate', (SELECT start_date FROM programs.convert_day_info_to_legacy(u.day_info)),
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', date_trunc('second', gn.timestamp)  -- Truncate to seconds
                                )
                            )
                            FROM groups.group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', date_trunc('second', ga.timestamp)  -- Truncate to seconds
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json)
        )
        FROM groups.groups g
        WHERE g.id = group_id
    );
END;$function$
;

CREATE OR REPLACE FUNCTION public.get_group(group_id uuid, user_id text)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Step 1: Check if the user is part of the group
    IF NOT EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.group_id = get_group.group_id
          AND gm.user_id = get_group.user_id
    ) THEN
        -- Raise a custom exception with a custom SQLSTATE
        RAISE EXCEPTION 'User % is not part of the group %', get_group.user_id, get_group.group_id
            USING ERRCODE = 'P0001';
    END IF;

    -- Step 2: Return group details if the user is part of the group
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'daysSober', COALESCE(
                            (SELECT days_sober FROM programs.convert_day_info_to_legacy(u.day_info)),
                            ARRAY[]::boolean[]
                        ),
                        'initialFrequency', 0.5,
                        'showInRank', u.show_in_group_rank,
                        'startDate', (SELECT start_date FROM programs.convert_day_info_to_legacy(u.day_info)),
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', date_trunc('second', gn.timestamp)
                                )
                            )
                            FROM groups.group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', date_trunc('second', ga.timestamp)  -- Truncate to seconds
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json),
            'subscribed', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'subscribedTo', gs.subscribed_to
                    )
                ), '[]'::json)
                FROM groups.group_subscriptions gs
                WHERE gs.group_id = get_group.group_id
                AND gs.user_id = get_group.user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = get_group.group_id
    );
END;$function$
;

CREATE OR REPLACE FUNCTION public.upsert_user(user_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    existing_user users%ROWTYPE;
BEGIN
    -- Get existing user data if it exists
    SELECT * INTO existing_user FROM users WHERE id = user_data->>'id';

    -- Insert the record with default values, or do nothing if it exists
    INSERT INTO users (
        id,
        name,
        emoji,
        day_info,
        fcm_token,
        show_in_group_rank
    )
    VALUES (
        user_data->>'id',
        COALESCE(user_data->>'name', existing_user.name),
        COALESCE(user_data->>'emoji', existing_user.emoji),
        CASE
            WHEN user_data ? 'day_info' THEN
                (user_data->'day_info')
            WHEN user_data ? 'days_sober' THEN
                programs.convert_legacy_to_day_info(
                    -- Getting start date
                    -- 1. Supplied
                    -- 2. Existing start date (prob not exist)
                    -- 3. Existing day info start date
                    COALESCE(
                        (user_data->>'start_date')::timestamptz,
                        existing_user.start_date,
                        (SELECT start_date FROM programs.convert_day_info_to_legacy(existing_user.day_info))
                    ),
                    ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
                )
            ELSE
                existing_user.day_info
        END,
        COALESCE(user_data->>'fcm_token', existing_user.fcm_token),
        COALESCE((user_data->>'show_in_group_rank')::boolean, existing_user.show_in_group_rank)
    )
    ON CONFLICT (id)
    DO UPDATE
    SET name = COALESCE(user_data->>'name', users.name),
        emoji = COALESCE(user_data->>'emoji', users.emoji),
        day_info = CASE 
            WHEN user_data ? 'day_info' THEN
                (user_data->'day_info')
            WHEN user_data ? 'days_sober' THEN
                programs.convert_legacy_to_day_info(
                    COALESCE(
                        (user_data->>'start_date')::timestamptz,
                        existing_user.start_date,
                        (SELECT start_date FROM programs.convert_day_info_to_legacy(existing_user.day_info))
                    ),
                    ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
                )
            ELSE 
                users.day_info
        END,
        fcm_token = COALESCE(user_data->>'fcm_token', users.fcm_token),
        show_in_group_rank = COALESCE((user_data->>'show_in_group_rank')::boolean, users.show_in_group_rank);
END;$function$
;
