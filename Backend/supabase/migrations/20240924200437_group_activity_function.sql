set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.update_group_activity(group_id uuid, user_id text, activity_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    WITH json_activities AS (
        -- Step 1: Unnest the JSONB array into a table format for easy comparison
        SELECT 
            -- Associate all activities with the provided user_id
            update_group_activity.user_id AS user_id,
            -- Map string values to the corresponding enum type
            CASE
                WHEN activity->>'type' = 'smoked' THEN 'smoked'::group_activity_type
                WHEN activity->>'type' = 'sober' THEN 'sober'::group_activity_type
                WHEN activity->>'type' = 'joined' THEN 'joined'::group_activity_type
                WHEN activity->>'type' = 'message' THEN 'message'::group_activity_type
                ELSE NULL
            END AS activity_type,
            (activity->>'timestamp')::timestamptz AS activity_timestamp
        FROM jsonb_array_elements(activity_data) AS activity
    ),
    -- Step 2: Delete activities for the specified user that are in the table but not in the provided JSON
    delete_activities AS (
        DELETE FROM group_activity ga
        WHERE ga.group_id = update_group_activity.group_id
          AND ga.user_id = update_group_activity.user_id
          AND NOT EXISTS (
              SELECT 1 FROM json_activities ja
              WHERE ja.user_id = ga.user_id
                AND ja.activity_type = ga.activity
                AND ja.activity_timestamp = ga.timestamp
          )
        RETURNING *
    )
    -- Step 3: Insert activities from JSON if they don't already exist in the table
    INSERT INTO group_activity (group_id, user_id, activity, timestamp)
    SELECT update_group_activity.group_id, ja.user_id, ja.activity_type, ja.activity_timestamp
    FROM json_activities ja
    WHERE ja.activity_type IS NOT NULL  -- Only insert valid activity types
      AND NOT EXISTS (
        SELECT 1 FROM group_activity ga
        WHERE ga.group_id = update_group_activity.group_id
          AND ga.user_id = update_group_activity.user_id
          AND ga.activity = ja.activity_type
          AND ga.timestamp = ja.activity_timestamp
    );
END;$function$
;


