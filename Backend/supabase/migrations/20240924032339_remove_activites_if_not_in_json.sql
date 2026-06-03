set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.update_group(group_data json)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    var_group_id UUID;
    var_member_record JSON;
    activity_record_json JSON;
    note_record JSON;
    member_id TEXT;
    note_id TEXT;
    activity_id TEXT;
BEGIN
    -- Update the groups table
    var_group_id := (group_data->>'id')::UUID;

    -- 1. DELETE missing members, notes, and activities
    -- Delete members that are not present in the updated JSON
    DELETE FROM group_members
    WHERE group_id = var_group_id
    AND user_id NOT IN (
        SELECT (member_record->>'memberID')::TEXT
        FROM json_array_elements(group_data->'members') AS member_record
    );

    -- Delete notes for members that are not present in the updated JSON
    DELETE FROM group_notes
    WHERE group_id = var_group_id
    AND to_member_id NOT IN (
        SELECT (member_record->>'memberID')::TEXT
        FROM json_array_elements(group_data->'members') AS member_record
    );

    -- Delete activities that are not present in the updated JSON
    DELETE FROM group_activity
    WHERE group_id = var_group_id
    AND user_id NOT IN (
        SELECT (activity_record->>'memberID')::TEXT
        FROM json_array_elements(group_data->'activity') AS activity_record
    );

    -- Delete all activities that are no longer in the JSON
    DELETE FROM group_activity
    WHERE group_id = var_group_id
    AND (group_id, user_id, timestamp) NOT IN (
        SELECT 
            var_group_id,  -- Ensure the correct group_id is used in the comparison
            (activity_record->>'memberID')::TEXT,  -- user_id from the JSON
            (activity_record->>'timestamp')::TIMESTAMP  -- timestamp from the JSON
        FROM json_array_elements(group_data->'activity') AS activity_record
    );

    -- 2. Insert/Update users before any group operations
    FOR var_member_record IN SELECT * FROM json_array_elements(group_data->'members')
    LOOP
        member_id := var_member_record->>'memberID';  -- No UUID cast, keeping it as TEXT

        -- Properly handle daysSober as a boolean[] array, with true, false, and null values
        INSERT INTO users (id, name, emoji, days_sober, initial_frequency, show_in_group_rank)
        VALUES (
            member_id, 
            var_member_record->>'name', 
            var_member_record->>'emoji', 
            ARRAY(
                SELECT 
                    CASE 
                        WHEN value = 'true' THEN TRUE
                        WHEN value = 'false' THEN FALSE
                        WHEN value = 'null' THEN NULL
                        ELSE NULL  -- Handle any unexpected values as NULL
                    END
                FROM json_array_elements_text(var_member_record->'daysSober') AS value
            )::boolean[],  -- Cast to boolean[] with proper handling for true, false, and null
            (var_member_record->>'initialFrequency')::NUMERIC, 
            (var_member_record->>'showInRank')::BOOLEAN
        )
        ON CONFLICT (id) DO UPDATE 
        SET name = EXCLUDED.name,
            emoji = EXCLUDED.emoji,
            days_sober = EXCLUDED.days_sober,
            initial_frequency = EXCLUDED.initial_frequency,
            show_in_group_rank = EXCLUDED.show_in_group_rank;
    END LOOP;

    -- 3. Update or Insert the group details after deletions and user insertions
    INSERT INTO groups (id, name, hue)
    VALUES (var_group_id, group_data->>'name', (group_data->>'hue')::NUMERIC)
    ON CONFLICT (id) DO UPDATE 
    SET name = EXCLUDED.name,
        hue = EXCLUDED.hue;

    -- 4. Insert/Update members and their related notes
    FOR var_member_record IN SELECT * FROM json_array_elements(group_data->'members')
    LOOP
        member_id := var_member_record->>'memberID';  -- No UUID cast, keeping it as TEXT

        -- Update or Insert the member in the group_members table
        INSERT INTO group_members (group_id, user_id)
        VALUES (var_group_id, member_id)  -- Explicitly using var_group_id to avoid ambiguity
        ON CONFLICT (group_id, user_id) DO NOTHING;

        -- Loop through notes and update group_notes
        FOR note_record IN SELECT * FROM json_array_elements(var_member_record->'notes')
        LOOP
            -- Update or Insert notes
            INSERT INTO group_notes (group_id, to_member_id, from_member_id, message, timestamp)
            VALUES (
                var_group_id,  -- Explicitly using var_group_id to avoid ambiguity
                member_id,  -- to_member_id is TEXT
                note_record->>'fromMemberID',  -- from_member_id is also TEXT
                note_record->>'message', 
                (note_record->>'timestamp')::TIMESTAMP
            )
            ON CONFLICT (group_id, to_member_id, from_member_id) DO UPDATE 
            SET message = EXCLUDED.message,
                timestamp = EXCLUDED.timestamp;
        END LOOP;
    END LOOP;

    -- 5. Insert/Update activities
    FOR activity_record_json IN SELECT * FROM json_array_elements(group_data->'activity')
    LOOP
        activity_id := activity_record_json->>'memberID';  -- user_id is TEXT
        
        -- Update or Insert activity
        INSERT INTO group_activity (group_id, user_id, activity, timestamp)
        VALUES (
            var_group_id,  -- Explicitly using var_group_id to avoid ambiguity
            activity_id,  -- user_id is TEXT
            (activity_record_json->>'type')::group_activity_type, 
            (activity_record_json->>'timestamp')::TIMESTAMP
        )
        ON CONFLICT (group_id, user_id, timestamp) DO UPDATE 
        SET activity = EXCLUDED.activity;
    END LOOP;

END;$function$
;


