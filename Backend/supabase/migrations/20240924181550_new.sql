alter table "public"."users" add column "start_date" timestamp with time zone;

alter table "public"."users" alter column "show_in_group_rank" drop not null;

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.add_group_mem(user_id text, group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Insert a new row into the group_members table
    INSERT INTO group_members (user_id, group_id)
    VALUES (user_id, group_id)
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO group_activity (group_id, user_id, activity)
    VALUES (group_id, user_id, 'joined');
END;$function$
;

CREATE OR REPLACE FUNCTION public.rem_group_mem(user_id text, group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Step 1: Remove all activities related to the user and group
    DELETE FROM group_activity ga
    WHERE ga.user_id = rem_group_mem.user_id
      AND ga.group_id = rem_group_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM group_members gm
    WHERE gm.user_id = rem_group_mem.user_id
      AND gm.group_id = rem_group_mem.group_id;

    -- Step 3: Remove all related group notes (sent or received by the user)
    DELETE FROM group_notes gn
    WHERE gn.group_id = rem_group_mem.group_id
      AND (gn.to_member_id = rem_group_mem.user_id OR gn.from_member_id = rem_group_mem.user_id);

    -- Step 4: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = rem_group_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM delete_group(rem_group_mem.group_id);
    END IF;
END;$function$
;

CREATE OR REPLACE FUNCTION public.upsert_group(
    group_data jsonb,
    user_id text  -- The user's ID to check if they are in the group
)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    -- If an 'id' is provided, check if the group exists and the user is part of the group
    IF group_data ? 'id' THEN
        -- Check if the group exists
        IF EXISTS (SELECT 1 FROM groups WHERE id = (group_data->>'id')::uuid) THEN
            -- Check if the user is part of the group
            IF NOT EXISTS (
                SELECT 1
                FROM group_members gm
                WHERE gm.group_id = (group_data->>'id')::uuid
                  AND gm.user_id = upsert_group.user_id
            ) THEN
                -- Raise an exception if the user is not part of the group
                RAISE EXCEPTION 'User % is not a member of the group %', upsert_group.user_id, group_data->>'id';
            END IF;
        END IF;

        -- Insert or update the group with the provided 'id'
        INSERT INTO groups (
            id, 
            name, 
            hue
        )
        VALUES (
            (group_data->>'id')::uuid, -- Use the provided 'id'
            group_data->>'name',        -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        )
        ON CONFLICT (id)
        DO UPDATE
        SET name = COALESCE(group_data->>'name', groups.name),
            hue = COALESCE((group_data->>'hue')::numeric, groups.hue);

    ELSE
        -- Insert without 'id', letting Supabase generate the UUID
        INSERT INTO groups (
            name, 
            hue
        )
        VALUES (
            group_data->>'name', -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        );
    END IF;
END;
$function$
;


CREATE OR REPLACE FUNCTION public.upsert_user(user_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Insert the record with default values, or do nothing if it exists
    INSERT INTO users (
        id, 
        name, 
        emoji, 
        days_sober, 
        initial_frequency, 
        fcm_token, 
        show_in_group_rank, 
        start_date
    )
    VALUES (
        user_data->>'id', -- 'id' must always be provided
        COALESCE(user_data->>'name', (SELECT name FROM users WHERE id = user_data->>'id')), -- Use existing name if not provided
        COALESCE(user_data->>'emoji', (SELECT emoji FROM users WHERE id = user_data->>'id')), -- Use existing emoji if not provided
        CASE 
            WHEN user_data ? 'days_sober' THEN
                ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
            ELSE (SELECT days_sober FROM users WHERE id = user_data->>'id') 
        END, -- Use existing days_sober if not provided
        COALESCE((user_data->>'initial_frequency')::numeric, (SELECT initial_frequency FROM users WHERE id = user_data->>'id')), -- Use existing initial_frequency if not provided
        COALESCE(user_data->>'fcm_token', (SELECT fcm_token FROM users WHERE id = user_data->>'id')), -- Use existing fcm_token if not provided
        COALESCE((user_data->>'show_in_group_rank')::boolean, (SELECT show_in_group_rank FROM users WHERE id = user_data->>'id')), -- Use existing show_in_group_rank if not provided
        COALESCE((user_data->>'start_date')::timestamptz, (SELECT start_date FROM users WHERE id = user_data->>'id')) -- Use existing start_date if not provided
    )
    ON CONFLICT (id)
    DO UPDATE
    SET name = COALESCE(user_data->>'name', users.name),
        emoji = COALESCE(user_data->>'emoji', users.emoji),
        days_sober = COALESCE(
            CASE 
                WHEN user_data ? 'days_sober' THEN
                    ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
                ELSE users.days_sober
            END,
            users.days_sober
        ),
        initial_frequency = COALESCE((user_data->>'initial_frequency')::numeric, users.initial_frequency),
        fcm_token = COALESCE(user_data->>'fcm_token', users.fcm_token),
        show_in_group_rank = COALESCE((user_data->>'show_in_group_rank')::boolean, users.show_in_group_rank),
        start_date = COALESCE((user_data->>'start_date')::timestamptz, users.start_date);
END;$function$
;


