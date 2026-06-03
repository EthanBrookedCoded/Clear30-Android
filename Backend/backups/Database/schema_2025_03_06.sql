

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "comms";


ALTER SCHEMA "comms" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_cron" WITH SCHEMA "pg_catalog";






CREATE SCHEMA IF NOT EXISTS "groups";


ALTER SCHEMA "groups" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "library";


ALTER SCHEMA "library" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";






CREATE SCHEMA IF NOT EXISTS "payment";


ALTER SCHEMA "payment" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pgsodium";






CREATE SCHEMA IF NOT EXISTS "programs";


ALTER SCHEMA "programs" OWNER TO "postgres";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE SCHEMA IF NOT EXISTS "schools";


ALTER SCHEMA "schools" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "symptoms";


ALTER SCHEMA "symptoms" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "views";


ALTER SCHEMA "views" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgjwt" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE TYPE "comms"."direction" AS ENUM (
    'inbound',
    'outbound'
);


ALTER TYPE "comms"."direction" OWNER TO "postgres";


CREATE TYPE "public"."group_activity_type" AS ENUM (
    'joined',
    'smoked',
    'sober',
    'message'
);


ALTER TYPE "public"."group_activity_type" OWNER TO "postgres";


COMMENT ON TYPE "public"."group_activity_type" IS 'Type of group activity';



CREATE TYPE "public"."school_activity_repeat_rate" AS ENUM (
    'weekly'
);


ALTER TYPE "public"."school_activity_repeat_rate" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_dr_fred_message_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$DECLARE
   to_user_fcm_token TEXT;
BEGIN
   -- Only trigger for outbound messages
   IF NOT NEW.outbound THEN
       RETURN NEW;
   END IF;

   -- Check if the user has an fcm_token
   SELECT fcm_token INTO to_user_fcm_token
   FROM users
   WHERE id = NEW.user_id;

   -- If there is no fcm_token, just return and do nothing
   IF to_user_fcm_token IS NULL THEN
       RETURN NEW;
   END IF;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
   VALUES (
       NEW.user_id,
       '👨‍⚕️ Dr. Fred sent you a message!',
       NEW.text,  -- The message content
       '{"type": "drFred"}'::jsonb,
       NOW()
   );

   RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."add_dr_fred_message_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_group_note_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$
DECLARE
    -- Declare variables to hold the data we'll fetch
    to_user_fcm_token TEXT;
    from_user_name TEXT;
    from_user_emoji TEXT;
BEGIN
    -- Check if the 'to_member_id' has an fcm_token
    SELECT fcm_token INTO to_user_fcm_token
    FROM users
    WHERE id = NEW.to_member_id;

    -- If there is no fcm_token, just return and do nothing
    IF to_user_fcm_token IS NULL THEN
        RETURN NULL;
    END IF;

    -- Fetch the 'name' and 'emoji' of the 'from_member_id'
    SELECT name, emoji INTO from_user_name, from_user_emoji
    FROM users
    WHERE id = NEW.from_member_id;

    -- Insert a notification into the notifications table
    INSERT INTO comms.notifications (user_id, title, body, timestamp)
    VALUES (
        NEW.to_member_id, 
        from_user_emoji || ' ' || from_user_name || ' sent you a note!',  -- Format the title
        NEW.message,  -- The body is the note's message content
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;
$$;


ALTER FUNCTION "comms"."add_group_note_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_group_ping_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$
DECLARE
   -- Declare variables to hold the data we'll fetch
   to_user_fcm_token TEXT;
   from_user_name TEXT;
   from_user_emoji TEXT;
BEGIN
   -- Check if the 'to_member_id' has an fcm_token
   SELECT fcm_token INTO to_user_fcm_token
   FROM users
   WHERE id = NEW.to_user_id;

   -- If there is no fcm_token, just return and do nothing
   IF to_user_fcm_token IS NULL THEN
       RETURN NULL;
   END IF;

   -- Fetch the 'name' and 'emoji' of the 'from_member_id'
   SELECT name, emoji INTO from_user_name, from_user_emoji
   FROM users
   WHERE id = NEW.from_user_id;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, timestamp)
   VALUES (
       NEW.to_user_id, 
       from_user_emoji || ' ' || from_user_name || ' check in!',  -- Format the title
       from_user_name || ' wants you to check in on Clear30!',  -- The body is the note's message content
       NOW()  -- Set the current timestamp
   );

   RETURN NEW;
END;
$$;


ALTER FUNCTION "comms"."add_group_ping_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_silent_notification"() RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN
  -- Insert due silent notifications into the notifications table
  WITH due_silent_notifications AS (
    SELECT 
      id,
      user_id,
      metadata,
      scheduled_for
    FROM comms.silent_notifications
    WHERE 
      processed = false 
      AND scheduled_for <= NOW()
  ),
  inserted_notifications AS (
    INSERT INTO comms.notifications (
      user_id,
      title,
      body,
      metadata,
      silent
    )
    SELECT
      user_id,
      '',
      '',
      metadata,
      true
    FROM due_silent_notifications
    RETURNING id
  )
  -- Mark processed silent notifications
  UPDATE comms.silent_notifications
  SET processed = true
  WHERE id IN (SELECT id FROM due_silent_notifications);
END;$$;


ALTER FUNCTION "comms"."add_silent_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."convert_day_info_to_legacy"("program_info" "jsonb") RETURNS TABLE("start_date" timestamp with time zone, "days_sober" boolean[])
    LANGUAGE "sql"
    SET "search_path" TO 'programs'
    AS $$WITH RECURSIVE 
parsed_json AS (
    SELECT 
        (program_info->>(i*2))::timestamp with time zone as date_key,
        program_info->(i*2 + 1)->>'sober' as sober_status
    FROM generate_series(0, jsonb_array_length(program_info)/2 - 1) as i
    WHERE i*2 < jsonb_array_length(program_info)
),
dates AS (
    SELECT 
        date_key,
        sober_status
    FROM parsed_json
    ORDER BY date_key
),
date_series AS (
    SELECT 
        generate_series(
            (min(date_key)::date + interval '1 day')::date,  -- Add 1 day to minimum date
            max(date_key)::date,
            '1 day'::interval
        )::date as series_date
    FROM dates
),
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
FROM final_array;$$;


ALTER FUNCTION "programs"."convert_day_info_to_legacy"("program_info" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."convert_legacy_to_day_info"("start_date" timestamp with time zone, "days_sober" boolean[]) RETURNS "jsonb"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'programs'
    AS $$BEGIN
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
END;$$;


ALTER FUNCTION "programs"."convert_legacy_to_day_info"("start_date" timestamp with time zone, "days_sober" boolean[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."migrate_clear30_messages"() RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
DECLARE
    next_id bigint;
BEGIN
    -- Get the next available ID from the production table
    SELECT COALESCE(MAX(id), 0) + 1 INTO next_id 
    FROM programs.program_messages;

    -- Delete existing clear30 messages from production
    DELETE FROM programs.program_messages 
    WHERE program = 'clear30';

    -- Insert messages from QA to production with new IDs
    INSERT INTO programs.program_messages (
        id,  -- Now explicitly setting id
        day,
        title,
        subtitle,
        body,
        program,
        question_id,
        question_response,
        resources,
        claire_prompts,
        journal_prompts,
        meditation,
        page_info,
        stage,
        guide_id
    )
    SELECT 
        next_id + row_number() OVER (ORDER BY id) - 1,  -- Generate new sequential IDs
        day,
        title,
        subtitle,
        body,
        program,
        question_id,
        question_response,
        resources,
        claire_prompts,
        journal_prompts,
        meditation,
        page_info,
        stage,
        guide_id
    FROM programs.program_messages_qa
    WHERE program = 'clear30';

    -- Reset the sequence to the next available ID
    PERFORM setval(
        'programs.program_messages_id_seq', 
        (SELECT MAX(id) FROM programs.program_messages), 
        true
    );

EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Error migrating clear30 messages: %', SQLERRM;
END;
$$;


ALTER FUNCTION "programs"."migrate_clear30_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id)
    VALUES (user_id, group_id)
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (group_id, user_id, 'joined');
END;$$;


ALTER FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Check if both to_member_id and from_member_id are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.to_member_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.from_member_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_notes (group_id, to_member_id, from_member_id, message, timestamp)
        VALUES (add_group_note.group_id, add_group_note.to_member_id, add_group_note.from_member_id, add_group_note.message, NOW());

        -- Insert a new row into the group_activity table with the "note" activity
        INSERT INTO groups.group_activity (group_id, user_id, activity)
        VALUES (add_group_note.group_id, add_group_note.from_member_id, 'message');
    END IF;
END;$$;


ALTER FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.to_user_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.from_user_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_pings (group_id, from_user_id, to_user_id)
        VALUES (add_group_ping.group_id, add_group_ping.from_user_id, add_group_ping.to_user_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."admin_check"() RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  uid uuid;
  is_admin boolean;
begin
  -- Get the current user ID from the JWT using auth.uid()
  select auth.uid() into uid;
  
  if uid is null then
    return false;
  end if;

  select exists(select 1 from public.admins where auth_id = uid) into is_admin;
  return is_admin;
end;
$$;


ALTER FUNCTION "public"."admin_check"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."create_user"("user_data" "json") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    _auth_id uuid;
    _phone_number text;
    _email text;
    _user_exists boolean;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Get phone number from AUTH users table
    SELECT phone, email::text
    INTO _phone_number, _email
    FROM auth.users
    WHERE id = _auth_id;

    -- First check: Look for existing user by auth_id
    SELECT EXISTS (
        SELECT 1 FROM public.users WHERE auth_id = _auth_id
    ) INTO _user_exists;

    IF _user_exists THEN
        -- Update existing user with non-null values (auth_id match case)
        UPDATE public.users
        SET
            name = COALESCE(user_data->>'name', name),
            emoji = COALESCE(user_data->>'emoji', emoji),
            day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
            fcm_token = CASE 
                WHEN user_data->>'fcm_token' is not null THEN user_data->>'fcm_token'
                ELSE fcm_token
            END,
            adjust_token = CASE 
                WHEN user_data->>'adjust_token' is not null THEN user_data->>'adjust_token'
                ELSE adjust_token
            END
        WHERE auth_id = _auth_id;

        PERFORM public.sms_clear();
    ELSE
        -- Second check: If ID provided, look for existing user by ID with null auth_id
        IF user_data->>'id' IS NOT NULL THEN
            SELECT EXISTS (
                SELECT 1 
                FROM public.users 
                WHERE id = user_data->>'id' 
                AND auth_id IS NULL
            ) INTO _user_exists;
            
            IF _user_exists THEN
                -- Update existing user with non-null values and set auth_id (ID match case)
                UPDATE public.users
                SET
                    auth_id = _auth_id,
                    name = COALESCE(user_data->>'name', name),
                    emoji = COALESCE(user_data->>'emoji', emoji),
                    day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
                    fcm_token = CASE 
                        WHEN user_data->>'fcm_token' is not null THEN user_data->>'fcm_token'
                        ELSE fcm_token
                    END,
                    adjust_token = CASE 
                        WHEN user_data->>'adjust_token' is not null THEN user_data->>'adjust_token'
                        ELSE adjust_token
                    END,
                    phone_number = _phone_number,
                    email = _email
                WHERE id = user_data->>'id';
            ELSE
                -- Insert new user
                INSERT INTO public.users (
                    id,
                    auth_id,
                    name,
                    emoji,
                    day_info,
                    fcm_token,
                    adjust_token,
                    phone_number,
                    email
                )
                VALUES (
                    COALESCE(user_data->>'id', _auth_id::text),
                    _auth_id,
                    user_data->>'name',
                    user_data->>'emoji',
                    (user_data->'day_info')::jsonb,
                    user_data->>'fcm_token',
                    user_data->>'adjust_token',
                    _phone_number,
                    _email
                );
            END IF;
        ELSE
            -- Insert new user (no ID provided case)
            INSERT INTO public.users (
                id,
                auth_id,
                name,
                emoji,
                day_info,
                fcm_token,
                adjust_token,
                phone_number,
                email
            )
            VALUES (
                _auth_id::text,
                _auth_id,
                user_data->>'name',
                user_data->>'emoji',
                (user_data->'day_info')::jsonb,
                user_data->>'fcm_token',
                user_data->>'adjust_token',
                _phone_number,
                _email
            );
        END IF;
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$$;


ALTER FUNCTION "public"."create_user"("user_data" "json") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."delete_group"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE groups.group_notes.group_id = delete_group.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE groups.group_activity.group_id = delete_group.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE groups.group_members.group_id = delete_group.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.groups.id = delete_group.group_id;

END;$$;


ALTER FUNCTION "public"."delete_group"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."dr_fred_get_messages"() RETURNS "text"[]
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_messages text[];
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Get messages and store them in array
    SELECT array_agg(text ORDER BY created_at)
    INTO v_messages
    FROM comms.dr_fred
    WHERE user_id = v_user_id
    AND outbound = true;  -- Only get messages from Dr. Fred (outbound = true)

    -- Return empty array if no messages found
    RETURN COALESCE(v_messages, ARRAY[]::text[]);
END;$$;


ALTER FUNCTION "public"."dr_fred_get_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."dr_fred_send_message"("message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Insert the message
    INSERT INTO comms.dr_fred (
        user_id,
        text,
        outbound
    ) VALUES (
        v_user_id,
        message,
        false
    );
END;$$;


ALTER FUNCTION "public"."dr_fred_send_message"("message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_check_in_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'check_in_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if the cache already has a value for "check_in_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSONB result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_check_in  -- Updated table name
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_check_in_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_claire_prompt"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    claire_prompt_value TEXT;
BEGIN
    -- Query to get the value of the row where the key is 'claire_prompt'
    SELECT value INTO claire_prompt_value
    FROM library.one_offs
    WHERE key = 'claire_prompt';

    -- Return the retrieved value
    RETURN claire_prompt_value;
END;
$$;


ALTER FUNCTION "public"."get_claire_prompt"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_generic_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$ 
DECLARE 
    result JSONB; 
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    SELECT 
    (
        get_school_data(umich_school_id) || 
        '{"school_id": "generic_demo", "short_name": "Group", "long_name": "Your Group", "reddit_flair": null, "sf_symbol_icon": "person.3.fill"}'::jsonb
    ) INTO result; 
    
    RETURN result;
END; 
$$;


ALTER FUNCTION "public"."get_generic_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_get_back_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'get_back_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "get_back_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_get_back  -- Updated schema reference
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_get_back_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
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
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        ),
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
END;$$;


ALTER FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[] DEFAULT ARRAY[]::"text"[]) RETURNS TABLE("anonymized_user_id" integer, "event_timestamp" timestamp with time zone, "event_name" "text", "event_extra_data" "json")
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[] DEFAULT ARRAY[]::"text"[]) RETURNS integer
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_payment_settings"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    payment_settings TEXT;
BEGIN
    -- Retrieve the string value for the key "payment_settings" from the library.one_offs table
    SELECT value
    INTO payment_settings
    FROM library.one_offs
    WHERE key = 'payment_settings';

    -- Return the value as JSONB
    RETURN payment_settings::JSONB;
END;
$$;


ALTER FUNCTION "public"."get_payment_settings"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_data"("school_id" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $_$DECLARE
    result JSONB;
    cached_result JSONB;
BEGIN
    -- Step 1: Check if there's a cached result for this school_id
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = school_id;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    WITH aggregated_resources AS (
        SELECT r.school_id,
               r.section_title,
               jsonb_agg(
                   jsonb_build_object(
                       'title', r.title,
                       'subtitle', r.subtitle,
                       'description', r.description,
                       'phone_number', r.phone_number,
                       'location', r.location,
                       'link', r.link,
                       'badge', r.badge
                   )
               ) AS resources_array
        FROM schools.school_resources r
        WHERE r.school_id = $1
        GROUP BY r.school_id, r.section_title
    ),
    final_resources AS (
        SELECT ar.school_id,
               jsonb_object_agg(ar.section_title, ar.resources_array) AS resources
        FROM aggregated_resources ar
        GROUP BY ar.school_id
    ),
    aggregated_messages AS (
        SELECT jsonb_agg(
               jsonb_build_object(
                   'day', m.day,
                   'title', m.title,
                   'subtitle', m.subtitle,
                   'message', m.message,
                   'meditation_name', (
                       SELECT key
                       FROM jsonb_object_keys(m.meditation) key
                       LIMIT 1
                   ),
                   'meditation_link', (
                       SELECT m.meditation->key
                       FROM jsonb_object_keys(m.meditation) key
                       LIMIT 1
                   ),
                   'prompts', COALESCE(m.claire_prompts, '{}'::jsonb),
                   'journal_prompts', COALESCE(m.journal_prompts, '[]'::jsonb),
                   'resources', COALESCE(m.resources, '{}'::jsonb)
               )
           ) AS messages_array
        FROM schools.school_messages m
        WHERE m.school_id = $1
    )
    
    -- Step 4: Build the JSON result
    SELECT jsonb_build_object(
        'school_id', s.school_id,
        'short_name', s.short_name,
        'long_name', s.long_name,
        'reddit_flair', COALESCE(s.reddit_flair, NULL),
        'color1', s.color1,
        'color2', s.color2,
        'activities', COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'title', a.title,
                    'org_name', a.org_name,
                    'location', a.location,
                    'subtitle', a.subtitle,
                    'description', a.description,
                    'date_time', a.date_time,
                    'repeats', a.repeats,
                    'link', a.link,
                    'facilitated_by', a.facilitated_by,
                    'phone_number', a.phone_number,
                    'email', a.email,
                    'sub_links', a.sub_links
                )
            ) FILTER (WHERE a.school_id IS NOT NULL), '[]'::jsonb
        ),
        'resources', COALESCE(fr.resources, '{}'::jsonb),
        'messages', COALESCE(am.messages_array, '[]'::jsonb)
    )
    INTO result
    FROM schools.schools s
    LEFT JOIN schools.school_activities a ON s.school_id = a.school_id
    LEFT JOIN final_resources fr ON s.school_id = fr.school_id
    LEFT JOIN aggregated_messages am ON TRUE  -- Ensure that all messages for this school are selected
    WHERE s.school_id = $1
    GROUP BY s.school_id, fr.resources, am.messages_array;

    -- Step 5: Insert the computed result into the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES ($1, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();
    
    -- Step 6: Return the computed result
    RETURN result;
END;$_$;


ALTER FUNCTION "public"."get_school_data"("school_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$ 
DECLARE 
    result JSONB; 
    cached_result JSONB;
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    SELECT 
    (
        get_school_data(umich_school_id) || 
        '{"school_id": "school_demo", "short_name": "Your School", "long_name": "Your School", "reddit_flair": null}'::jsonb
    ) INTO result; 
    
    RETURN result;
END; 
$$;


ALTER FUNCTION "public"."get_school_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_slip_up_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'slip_up_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "slip_up_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_slip_up  -- Updated schema reference
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_slip_up_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_symptom_infos"() RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    result TEXT := '{';  -- Initialize result as a JSON string starting with an opening brace
    first_entry BOOLEAN := TRUE;  -- To track whether it's the first entry to avoid trailing commas
    record RECORD;
    tip_record RECORD;  -- Declare tip_record as a RECORD
    section_record RECORD;  -- Declare section_record as a RECORD
    reddit_record RECORD;  -- Declare reddit_record as a RECORD
    prompt_record RECORD;  -- Declare prompt_record as a RECORD
    tips_array TEXT;
    sections_array TEXT;
    reddits TEXT;
    prompts TEXT;
    first_tip BOOLEAN;
    first_section BOOLEAN;
    first_reddit BOOLEAN;
    first_prompt BOOLEAN;
    cached_result JSONB;  -- Holds the cached JSON from the cache table
    cache_key CONSTANT TEXT := 'symptom_infos';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if the cache already has a value for "symptom_infos"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSON result
    -- Loop through each symptom and gather the colors, tips, reddit threads, and prompts for that symptom
    FOR record IN 
        SELECT 
            s.emoji || ' ' || s.symptom AS symptom_key, 
            s.symptom, 
            s.color1, 
            s.color2,
            COALESCE(s.resources, '{}'::jsonb) as resources,
            COALESCE(s.claire_prompts, '{}'::jsonb) as claire_prompts
        FROM symptoms.symptoms s
        LEFT JOIN symptoms.symptom_tips st ON s.symptom = st.symptom
        GROUP BY s.emoji, s.symptom, st.color1, st.color2, s.resources, s.claire_prompts
        ORDER BY s.symptom
    LOOP
        -- Initialize tips array for the current symptom
        tips_array := '[';
        first_tip := TRUE;

        -- Get all tips for the current symptom by matching the symptom name
        FOR tip_record IN
            SELECT st.id, st.title, st.color1, st.color2
            FROM symptoms.symptom_tips st
            WHERE st.symptom = record.symptom
        LOOP
            -- Initialize sections array for the current tip
            sections_array := '[';
            first_section := TRUE;

            -- Get all sections for the current tip
            FOR section_record IN
                SELECT sts.heading, sts.content, sts.example
                FROM symptoms.symptom_tip_sections sts
                WHERE sts.symptom_tip = tip_record.id
            LOOP
                -- Handle commas between sections
                IF NOT first_section THEN
                    sections_array := sections_array || ', ';
                END IF;

                -- Add each section with heading, content, and possibly example, using to_json to escape text
                sections_array := sections_array || '{"heading": ' || to_json(section_record.heading) || ', "content": ' || to_json(section_record.content);

                -- Add example if it's not NULL
                IF section_record.example IS NOT NULL THEN
                    sections_array := sections_array || ', "example": ' || to_json(section_record.example);
                END IF;

                -- Close the section object
                sections_array := sections_array || '}';

                -- Mark that this is no longer the first section
                first_section := FALSE;
            END LOOP;

            -- Close the sections array
            sections_array := sections_array || ']';

            -- Handle commas between tips
            IF NOT first_tip THEN
                tips_array := tips_array || ', ';
            END IF;

            -- Add each tip to the tips array with title, colors, and sections, using to_json to escape text
            tips_array := tips_array || '{"title": ' || to_json(tip_record.title) || ', "color1": ' || to_json(tip_record.color1) || ', "color2": ' || to_json(tip_record.color2) || ', "sections": ' || sections_array || '}';

            -- Mark that this is no longer the first tip
            first_tip := FALSE;
        END LOOP;

        -- Close the tips array
        tips_array := tips_array || ']';

        -- Handle commas between symptom entries
        IF NOT first_entry THEN
            result := result || ', ';
        END IF;

        -- Add the symptom's colors, tips, reddits, and prompts to the result
        -- Note: Using the new columns directly as they're already in JSON format
        result := result || '"' || record.symptom_key || '": {"color1": ' || 
            to_json(record.color1) || ', "color2": ' || 
            to_json(record.color2) || ', "tips": ' || 
            tips_array || ', "reddits": ' || 
            record.resources::text || ', "prompts": ' || 
            record.claire_prompts::text || '}';

        -- Mark that this is no longer the first entry
        first_entry := FALSE;
    END LOOP;

    -- Close the JSON object with a closing brace
    result := result || '}';

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result::JSON, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSON
    RETURN result::JSON;
END;$$;


ALTER FUNCTION "public"."get_symptom_infos"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_symptom_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'symptom_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "symptom_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    WITH aggregated_symptoms AS (
        SELECT s.emoji || ' ' || s.symptom AS symptom_key,
               jsonb_agg(sm.message) AS messages_array
        FROM symptoms.symptom_messages sm
        JOIN symptoms.symptoms s ON sm.symptom = s.symptom
        GROUP BY s.emoji, s.symptom
    )
    SELECT jsonb_object_agg(symptom_key, jsonb_build_object('messages', messages_array))
    INTO result
    FROM aggregated_symptoms;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_symptom_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_user_id"() RETURNS "text"
    LANGUAGE "plpgsql"
    AS $$BEGIN
  RETURN (SELECT id FROM users WHERE auth_id = auth.uid());
END;$$;


ALTER FUNCTION "public"."get_user_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_mem"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id)
    VALUES (current_user_id, group_add_mem.group_id)
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (group_add_mem.group_id, current_user_id, 'joined');
END;$$;


ALTER FUNCTION "public"."group_add_mem"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Check if both to_member_id and from_member_id (current user) are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_note.group_id
          AND gm.user_id = to_member_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_note.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_notes (
            group_id, 
            to_member_id, 
            from_member_id, 
            message, 
            timestamp
        )
        VALUES (
            group_id, 
            to_member_id, 
            current_user_id, 
            message, 
            NOW()
        );

        -- Insert a new row into the group_activity table with the "note" activity
        INSERT INTO groups.group_activity (
            group_id, 
            user_id, 
            activity
        )
        VALUES (
            group_id, 
            current_user_id, 
            'message'
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Check if both to_user_id and current user are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_ping.group_id
          AND gm.user_id = to_user_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_ping.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Insert the ping into the group_pings table
        INSERT INTO groups.group_pings (
            group_id, 
            from_user_id, 
            to_user_id
        )
        VALUES (
            group_id, 
            current_user_id, 
            to_user_id
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_delete"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE groups.group_notes.group_id = group_delete.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE groups.group_activity.group_id = group_delete.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE groups.group_members.group_id = group_delete.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.groups.id = group_delete.group_id;

END;$$;


ALTER FUNCTION "public"."group_delete"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_get"("group_id" "uuid") RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Step 1: Check if the user is part of the group
    IF NOT EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.group_id = group_get.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Raise a custom exception with a custom SQLSTATE
        RAISE EXCEPTION 'User % is not part of the group %', current_user_id, group_get.group_id
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
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        ),
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
                WHERE gs.group_id = group_get.group_id
                AND gs.user_id = current_user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = group_get.group_id
    );
END;$$;


ALTER FUNCTION "public"."group_get"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the current user's ID from their auth ID
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- Check if the current user is in the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_rem_mem.group_id
        AND gm.user_id = current_user_id
    ) THEN
        RAISE EXCEPTION 'You must be a member of the group to remove members';
    END IF;

    -- Step 1: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = group_rem_mem.user_id
      AND ga.group_id = group_rem_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = group_rem_mem.user_id
      AND gm.group_id = group_rem_mem.group_id;

    -- Step 3: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = group_rem_mem.user_id
      AND gs.group_id = group_rem_mem.group_id;

    -- Step 4: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = group_rem_mem.group_id
      AND (gn.to_member_id = group_rem_mem.user_id OR gn.from_member_id = group_rem_mem.user_id);

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_rem_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM group_delete(group_rem_mem.group_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    WITH json_activities AS (
        -- Step 1: Unnest the JSONB array into a table format for easy comparison
        SELECT 
            -- Associate all activities with the current user
            current_user_id AS user_id,
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
    -- Step 2: Delete activities for the current user that are in the table but not in the provided JSON
    delete_activities AS (
        DELETE FROM groups.group_activity ga
        WHERE ga.group_id = group_update_activity.group_id
          AND ga.user_id = current_user_id
          AND NOT EXISTS (
              SELECT 1 FROM json_activities ja
              WHERE ja.user_id = ga.user_id
                AND ja.activity_type = ga.activity
                AND ja.activity_timestamp = ga.timestamp
          )
        RETURNING *
    )
    -- Step 3: Insert activities from JSON if they don't already exist in the table
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    SELECT group_update_activity.group_id, ja.user_id, ja.activity_type, ja.activity_timestamp
    FROM json_activities ja
    WHERE ja.activity_type IS NOT NULL  -- Only insert valid activity types
      AND NOT EXISTS (
        SELECT 1 FROM groups.group_activity ga
        WHERE ga.group_id = group_update_activity.group_id
          AND ga.user_id = current_user_id
          AND ga.activity = ja.activity_type
          AND ga.timestamp = ja.activity_timestamp
    );
END;$$;


ALTER FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- 1. Check if the user is in the group
    IF NOT EXISTS (
        SELECT 1 
        FROM groups.group_members
        WHERE group_members.group_id = group_update_subscriptions.group_id
        AND group_members.user_id = current_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of the group';
    END IF;

    -- 2. Remove all existing rows in group_subscriptions for the group_id and user_id
    DELETE FROM groups.group_subscriptions
    WHERE group_subscriptions.group_id = group_update_subscriptions.group_id
    AND group_subscriptions.user_id = current_user_id;

    -- 3. Insert new subscriptions into group_subscriptions
    INSERT INTO groups.group_subscriptions (group_id, user_id, subscribed_to)
    SELECT 
        group_update_subscriptions.group_id, 
        current_user_id, 
        value::TEXT
    FROM jsonb_array_elements_text(group_update_subscriptions.subscriptions) AS value;

END;$$;


ALTER FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_upsert"("group_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- If an 'id' is provided, check if the group exists and the user is part of the group
    IF group_data ? 'id' THEN
        -- Check if the group exists
        IF EXISTS (SELECT 1 FROM groups.groups WHERE id = (group_data->>'id')::uuid) THEN
            -- Check if the user is part of the group
            IF NOT EXISTS (
                SELECT 1
                FROM groups.group_members gm
                WHERE gm.group_id = (group_data->>'id')::uuid
                  AND gm.user_id = current_user_id
            ) THEN
                -- Raise an exception if the user is not part of the group
                RAISE EXCEPTION 'User % is not a member of the group %', current_user_id, group_data->>'id';
            END IF;
        END IF;

        -- Insert or update the group with the provided 'id'
        INSERT INTO groups.groups (
            id, 
            name, 
            hue
        )
        VALUES (
            (group_data->>'id')::uuid,    -- Use the provided 'id'
            group_data->>'name',          -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        )
        ON CONFLICT (id)
        DO UPDATE
        SET name = COALESCE(group_data->>'name', groups.name),
            hue = COALESCE((group_data->>'hue')::numeric, groups.hue);

    ELSE
        -- Insert without 'id', letting Supabase generate the UUID
        INSERT INTO groups.groups (
            name, 
            hue
        )
        VALUES (
            group_data->>'name',          -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_upsert"("group_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_code"("input_code" "text") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    is_valid boolean;
BEGIN
    -- Check if code exists (case insensitive) and increment uses if it does
    UPDATE payment.promo_codes
    SET uses = uses + 1
    WHERE LOWER(code) = LOWER(input_code)
    RETURNING true INTO is_valid;
    
    -- Return the result (null becomes false)
    RETURN COALESCE(is_valid, false);
END;
$$;


ALTER FUNCTION "public"."payment_check_code"("input_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_email"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    user_email text;
    email_domain text;
    org_name text;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name if it does
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org INTO org_name;
    
    -- Return the org name (will be null if domain not found)
    RETURN org_name;
END;
$$;


ALTER FUNCTION "public"."payment_check_email"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_email_json"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    user_email text;
    email_domain text;
    org_name text;
    org_school_id text;
    result jsonb;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name and school_id
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org, school_id INTO org_name, org_school_id;
    
    -- Raise exception if no matching domain was found
    IF org_name IS NULL THEN
        RAISE EXCEPTION 'Domain % not found in allowlist', email_domain;
    END IF;
    
    -- Construct the JSON return object
    result := jsonb_build_object(
        'org', org_name,
        'school_id', org_school_id
    );
    
    RETURN result;
END;$$;


ALTER FUNCTION "public"."payment_check_email_json"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_sale"() RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
  active_sale_id bigint;
  sale_exists boolean;
  user_id text;
BEGIN
  -- Get the authenticated user's ID
  user_id := auth.uid();
  
  -- Throw an error if no authenticated user
  IF user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  
  -- Check if there's an active sale (current date between starts_on and ends_on)
  SELECT id INTO active_sale_id
  FROM payment.sale
  WHERE CURRENT_DATE BETWEEN starts_on AND ends_on
  LIMIT 1;
  
  -- Determine if a sale exists
  sale_exists := active_sale_id IS NOT NULL;
  
  -- If a sale exists, increment the uses count
  IF sale_exists THEN
    UPDATE payment.sale
    SET uses = uses + 1
    WHERE id = active_sale_id;
  END IF;
  
  -- Return whether a sale exists
  RETURN sale_exists;
END;
$$;


ALTER FUNCTION "public"."payment_check_sale"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."popin_request_clear"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_user_id text;
BEGIN
  -- Get the user ID from the authenticated user
  SELECT id INTO v_user_id
  FROM public.users
  WHERE auth_id = auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Update all unprocessed pop-in notifications for this user
  UPDATE comms.silent_notifications
  SET processed = true
  WHERE 
    user_id = v_user_id
    AND processed = false
    AND metadata->>'type' = 'popInRequest';
  RETURN;
END;$$;


ALTER FUNCTION "public"."popin_request_clear"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_user_id text;
BEGIN
  -- Get the user ID from the authenticated user
  SELECT id INTO v_user_id
  FROM public.users
  WHERE auth_id = auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Insert the notification
  INSERT INTO comms.silent_notifications
    (
      user_id, 
      scheduled_for, 
      metadata, 
      processed
    )
  VALUES
    (
      v_user_id, 
      popin_request_schedule.scheduled_for, 
      '{"type": "popInRequest"}'::jsonb, 
      false
    );
    
  RETURN;
END;$$;


ALTER FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_feedback"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    recent_assessment RECORD;
    feedback JSONB := '[]';
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Step 2: Fetch feedback for the associated program, applying filters and ordering
    SELECT jsonb_agg(
        jsonb_build_object(
            'order', af.order,
            'title', af.title,
            'body', REPLACE(af.body, '_CLIENTNAME_', _user_name),
            'links', COALESCE(
                (
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'title', key,
                            'url', value
                        )
                    )
                    FROM jsonb_each_text(af.links)
                ),
                '[]'::jsonb
            )
        ) ORDER BY af.order
    )
    INTO feedback
    FROM programs.program_feedback af
    WHERE af.program = recent_assessment.program
    AND (
        af.question_id IS NULL
        OR (
            af.question_id IS NOT NULL
            AND (
                -- String response match
                jsonb_typeof(recent_assessment.responses -> af.question_id) = 'string'
                AND af.question_response = recent_assessment.responses ->> af.question_id
            )
            OR (
                -- Array response match
                jsonb_typeof(recent_assessment.responses -> af.question_id) = 'array'
                AND af.question_response = ANY (
                    SELECT jsonb_array_elements_text(recent_assessment.responses -> af.question_id)
                )
            )
        )
    );

    RETURN COALESCE(feedback, '[]'::JSONB);
END;$$;


ALTER FUNCTION "public"."program_get_feedback"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_latest_update"() RETURNS timestamp with time zone
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$begin
  -- Check if user is authenticated
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  -- Return the most recent updated_at date
  return (
    select max(updated_at)
    from programs.programs
  );
end;$$;


ALTER FUNCTION "public"."program_get_latest_update"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Get messages with assessment message limitation
    WITH base_message_data AS (
        SELECT 
            pm.id,
            pm.day,
            pm.question_id,
            pm.question_response,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_title), '\n', '', 'g') as notification_title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_body), '\n', '', 'g') as notification_body,
            CASE 
                WHEN pm.resources IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'url', value
                    )) FROM jsonb_each_text(pm.resources))
                ELSE NULL
            END as resources,
            CASE 
                WHEN pm.meditation IS NOT NULL THEN
                    jsonb_build_object(
                        'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                        'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                    )
                ELSE NULL
            END as meditation,
            CASE 
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            pm.journal_prompts,
            CASE 
                WHEN pg.id IS NOT NULL THEN
                    (
                        SELECT jsonb_agg(jsonb_build_object('title', title, 'body', content))
                        FROM (
                            SELECT section_1_title as title, section_1_content as content FROM (SELECT pg.*) s WHERE section_1_title IS NOT NULL AND section_1_content IS NOT NULL
                            UNION ALL
                            SELECT section_2_title, section_2_content FROM (SELECT pg.*) s WHERE section_2_title IS NOT NULL AND section_2_content IS NOT NULL
                            UNION ALL
                            SELECT section_3_title, section_3_content FROM (SELECT pg.*) s WHERE section_3_title IS NOT NULL AND section_3_content IS NOT NULL
                            UNION ALL
                            SELECT section_4_title, section_4_content FROM (SELECT pg.*) s WHERE section_4_title IS NOT NULL AND section_4_content IS NOT NULL
                            UNION ALL
                            SELECT section_5_title, section_5_content FROM (SELECT pg.*) s WHERE section_5_title IS NOT NULL AND section_5_content IS NOT NULL
                            UNION ALL
                            SELECT section_6_title, section_6_content FROM (SELECT pg.*) s WHERE section_6_title IS NOT NULL AND section_6_content IS NOT NULL
                            UNION ALL
                            SELECT section_7_title, section_7_content FROM (SELECT pg.*) s WHERE section_7_title IS NOT NULL AND section_7_content IS NOT NULL
                            UNION ALL
                            SELECT section_8_title, section_8_content FROM (SELECT pg.*) s WHERE section_8_title IS NOT NULL AND section_8_content IS NOT NULL
                            UNION ALL
                            SELECT section_9_title, section_9_content FROM (SELECT pg.*) s WHERE section_9_title IS NOT NULL AND section_9_content IS NOT NULL
                        ) sections
                    )
                ELSE NULL
            END as page_info
        FROM programs.program_messages pm
        LEFT JOIN programs.program_guides pg ON pm.guide_id = pg.id
        WHERE pm.program = recent_assessment.program
        AND (
            pm.question_id IS NULL
            OR (
                pm.question_id IS NOT NULL
                AND (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                    AND pm.question_response = recent_assessment.responses ->> pm.question_id
                )
                OR (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                    AND pm.question_response = ANY (
                        SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                    )
                )
            )
        )
    ),
    message_objects AS (
        -- Convert base data into JSON objects
        SELECT 
            id,
            day,
            stage,
            question_id IS NOT NULL as is_assessment,
            jsonb_build_object(
                'id', id,
                'question_id', question_id,
                'question_response', question_response,
                'day', day,
                'title', title,
                'subtitle', subtitle,
                'body', body,
                'stage', stage,
                'resources', resources,
                'meditation', meditation,
                'claire_prompts', claire_prompts,
                'journal_prompts', journal_prompts,
                'page_info', page_info,
                'notification_title', notification_title,
                'notification_body', notification_body
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
        -- Group messages by day and type, keeping track of position within assessment messages
        SELECT 
            day,
            is_assessment,
            message_object,
            CASE 
                WHEN is_assessment THEN
                    row_number() OVER (PARTITION BY day, is_assessment ORDER BY id) - 1
                ELSE 0
            END as msg_position,
            CASE 
                WHEN is_assessment THEN
                    count(*) OVER (PARTITION BY day, is_assessment)
                ELSE 1
            END as group_size,
            -- Global counter for assessment messages across all days
            CASE 
                WHEN is_assessment THEN
                    dense_rank() OVER (ORDER BY day) - 1
                ELSE 0
            END as day_counter
        FROM message_objects
    )
    SELECT 
        (
            SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
            FROM day_groups
            WHERE NOT is_assessment  -- Include all core messages
               OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM title), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM subtitle), '\n', '', 'g'),
            'body', TRIM(TRAILING FROM REPLACE(body, '_CLIENTNAME_', _user_name)),
            'color1', color1,
            'color2', color2,
            'fred_experience', fred_experience
        )
    )
    INTO stages
    FROM programs.program_stages
    WHERE stage = ANY(stage_ids);

    -- Step 4: Return final combined structure
    RETURN jsonb_build_object(
        'stages', COALESCE(stages, '[]'::jsonb),
        'messages', COALESCE(messages, '[]'::jsonb)
    );
END;$$;


ALTER FUNCTION "public"."program_get_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_messages_qa"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Get messages with assessment message limitation
    WITH base_message_data AS (
        SELECT 
            pm.id,
            pm.day,
            pm.question_id,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            CASE 
                WHEN pm.resources IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'url', value
                    )) FROM jsonb_each_text(pm.resources))
                ELSE NULL
            END as resources,
            CASE 
                WHEN pm.meditation IS NOT NULL THEN
                    jsonb_build_object(
                        'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                        'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                    )
                ELSE NULL
            END as meditation,
            CASE 
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            pm.journal_prompts,
            CASE 
                WHEN pg.id IS NOT NULL THEN
                    (
                        SELECT jsonb_agg(jsonb_build_object('title', title, 'body', content))
                        FROM (
                            SELECT section_1_title as title, section_1_content as content FROM (SELECT pg.*) s WHERE section_1_title IS NOT NULL AND section_1_content IS NOT NULL
                            UNION ALL
                            SELECT section_2_title, section_2_content FROM (SELECT pg.*) s WHERE section_2_title IS NOT NULL AND section_2_content IS NOT NULL
                            UNION ALL
                            SELECT section_3_title, section_3_content FROM (SELECT pg.*) s WHERE section_3_title IS NOT NULL AND section_3_content IS NOT NULL
                            UNION ALL
                            SELECT section_4_title, section_4_content FROM (SELECT pg.*) s WHERE section_4_title IS NOT NULL AND section_4_content IS NOT NULL
                            UNION ALL
                            SELECT section_5_title, section_5_content FROM (SELECT pg.*) s WHERE section_5_title IS NOT NULL AND section_5_content IS NOT NULL
                            UNION ALL
                            SELECT section_6_title, section_6_content FROM (SELECT pg.*) s WHERE section_6_title IS NOT NULL AND section_6_content IS NOT NULL
                            UNION ALL
                            SELECT section_7_title, section_7_content FROM (SELECT pg.*) s WHERE section_7_title IS NOT NULL AND section_7_content IS NOT NULL
                            UNION ALL
                            SELECT section_8_title, section_8_content FROM (SELECT pg.*) s WHERE section_8_title IS NOT NULL AND section_8_content IS NOT NULL
                            UNION ALL
                            SELECT section_9_title, section_9_content FROM (SELECT pg.*) s WHERE section_9_title IS NOT NULL AND section_9_content IS NOT NULL
                        ) sections
                    )
                ELSE NULL
            END as page_info
        FROM programs.program_messages_qa pm
        LEFT JOIN programs.program_guides_qa pg ON pm.guide_id = pg.id
        WHERE pm.program = recent_assessment.program
        AND (
            pm.question_id IS NULL
            OR (
                pm.question_id IS NOT NULL
                AND (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                    AND pm.question_response = recent_assessment.responses ->> pm.question_id
                )
                OR (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                    AND pm.question_response = ANY (
                        SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                    )
                )
            )
        )
    ),
    message_objects AS (
        -- Convert base data into JSON objects
        SELECT 
            id,
            day,
            stage,
            question_id IS NOT NULL as is_assessment,
            jsonb_build_object(
                'id', id,
                'day', day,
                'title', title,
                'subtitle', subtitle,
                'body', body,
                'stage', stage,
                'resources', resources,
                'meditation', meditation,
                'claire_prompts', claire_prompts,
                'journal_prompts', journal_prompts,
                'page_info', page_info
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
        -- Group messages by day and type, keeping track of position within assessment messages
        SELECT 
            day,
            is_assessment,
            message_object,
            CASE 
                WHEN is_assessment THEN
                    row_number() OVER (PARTITION BY day, is_assessment ORDER BY id) - 1
                ELSE 0
            END as msg_position,
            CASE 
                WHEN is_assessment THEN
                    count(*) OVER (PARTITION BY day, is_assessment)
                ELSE 1
            END as group_size,
            -- Global counter for assessment messages across all days
            CASE 
                WHEN is_assessment THEN
                    dense_rank() OVER (ORDER BY day) - 1
                ELSE 0
            END as day_counter
        FROM message_objects
    )
    SELECT 
        (
            SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
            FROM day_groups
            WHERE NOT is_assessment  -- Include all core messages
               OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM title), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM subtitle), '\n', '', 'g'),
            'body', TRIM(TRAILING FROM REPLACE(body, '_CLIENTNAME_', _user_name)),
            'color1', color1,
            'color2', color2,
            'fred_experience', fred_experience
        )
    )
    INTO stages
    FROM programs.program_stages
    WHERE stage = ANY(stage_ids);

    -- Step 4: Return final combined structure
    RETURN jsonb_build_object(
        'stages', COALESCE(stages, '[]'::jsonb),
        'messages', COALESCE(messages, '[]'::jsonb)
    );
END;$$;


ALTER FUNCTION "public"."program_get_messages_qa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    valid_question_ids JSONB;
    filtered_responses JSONB := '{}';
    _auth_id uuid;
    _user_id text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Ensure the auth_id exists in users table
    IF NOT EXISTS (SELECT 1 FROM public.users WHERE auth_id = _auth_id) THEN
        RAISE EXCEPTION 'User record not found for authenticated user';
    END IF;

    -- Get user id
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Ensure the assessment exists
    IF NOT EXISTS (SELECT 1 FROM programs.program_assessments WHERE id = assessment_id) THEN
        RAISE EXCEPTION 'Assessment with ID % does not exist', assessment_id;
    END IF;

    -- Get valid question IDs from the programs.program_assessments table
    SELECT question_ids
    INTO valid_question_ids
    FROM programs.program_assessments
    WHERE id = assessment_id;

    -- Filter the input JSON for valid question IDs
    SELECT jsonb_object_agg(key, value)
    INTO filtered_responses
    FROM jsonb_each(responses)
    WHERE key = ANY (SELECT jsonb_array_elements_text(valid_question_ids));

    -- Insert into the AssessmentResponses table
    INSERT INTO programs.program_assessment_responses (
        user_id, assessment, responses
    )
    VALUES (
        _user_id, assessment_id, filtered_responses
    );

END;$$;


ALTER FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Step 1: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = rem_group_mem.user_id
      AND ga.group_id = rem_group_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = rem_group_mem.user_id
      AND gm.group_id = rem_group_mem.group_id;

    -- Step 3: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = rem_group_mem.user_id
      AND gs.group_id = rem_group_mem.group_id;

    -- Step 4: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = rem_group_mem.group_id
      AND (gn.to_member_id = rem_group_mem.user_id OR gn.from_member_id = rem_group_mem.user_id);

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = rem_group_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM group_delete(rem_group_mem.group_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_clear"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_auth_id uuid;
BEGIN
    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user id from the users table
    SELECT id 
    INTO v_user_id
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Update future messages to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = v_user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false);
END;$$;


ALTER FUNCTION "public"."sms_clear"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_clear"("message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_auth_id uuid;
BEGIN
    -- Input validation
    IF message IS NULL OR trim(message) = '' THEN
        RAISE EXCEPTION 'Message pattern cannot be empty';
    END IF;

    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user id from the users table
    SELECT id 
    INTO v_user_id
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Update future messages containing the pattern to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = v_user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false)
    AND text LIKE '%' || message || '%';

END;$$;


ALTER FUNCTION "public"."sms_clear"("message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_schedule"("sms_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_phone_number text;
    v_message text;
    v_offset_minutes int;
    v_auth_id uuid;
BEGIN
    -- Get the message and offset from the JSONB input
    v_message := sms_data->>'message';
    v_offset_minutes := (sms_data->>'offset_minutes')::int;

    -- Input validation
    IF v_message IS NULL OR trim(v_message) = '' THEN
        RAISE EXCEPTION 'Message cannot be empty';
    END IF;

    IF v_offset_minutes IS NULL OR v_offset_minutes < 0 THEN
        RAISE EXCEPTION 'Offset minutes must be a positive number';
    END IF;

    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user details from the users table
    SELECT id, phone_number 
    INTO v_user_id, v_phone_number
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    IF v_phone_number IS NULL THEN
        RAISE EXCEPTION 'User has no associated phone number';
    END IF;

    -- Insert the SMS message
    INSERT INTO comms.sms_messages (
        user_id,
        phone_number,
        text,
        scheduled_for
    ) VALUES (
        v_user_id,
        v_phone_number,
        v_message,
        now() + (v_offset_minutes * interval '1 minute')
    );
END;$$;


ALTER FUNCTION "public"."sms_schedule"("sms_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") RETURNS "void"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$BEGIN
    INSERT INTO comms.feedback (user_id, feedback, timestamp)
    VALUES (user_id, feedback, CURRENT_TIMESTAMP);
END;$$;


ALTER FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
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
        DELETE FROM groups.group_activity ga
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
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    SELECT update_group_activity.group_id, ja.user_id, ja.activity_type, ja.activity_timestamp
    FROM json_activities ja
    WHERE ja.activity_type IS NOT NULL  -- Only insert valid activity types
      AND NOT EXISTS (
        SELECT 1 FROM groups.group_activity ga
        WHERE ga.group_id = update_group_activity.group_id
          AND ga.user_id = update_group_activity.user_id
          AND ga.activity = ja.activity_type
          AND ga.timestamp = ja.activity_timestamp
    );
END;$$;


ALTER FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- 1. Check if the user is in the group
    IF NOT EXISTS (
        SELECT 1 
        FROM groups.group_members
        WHERE group_members.group_id = update_group_subscriptions.group_id
        AND group_members.user_id = update_group_subscriptions.user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of the group';
    END IF;

    -- 2. Remove all existing rows in group_subscriptions for the group_id and user_id
    DELETE FROM groups.group_subscriptions
    WHERE group_subscriptions.group_id = update_group_subscriptions.group_id
    AND group_subscriptions.user_id = update_group_subscriptions.user_id;

    -- 3. Insert new subscriptions into group_subscriptions
    INSERT INTO groups.group_subscriptions (group_id, user_id, subscribed_to)
    SELECT update_group_subscriptions.group_id, update_group_subscriptions.user_id, value::TEXT
    FROM jsonb_array_elements_text(update_group_subscriptions.subscriptions) AS value;

END;$$;


ALTER FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- If an 'id' is provided, check if the group exists and the user is part of the group
    IF group_data ? 'id' THEN
        -- Check if the group exists
        IF EXISTS (SELECT 1 FROM groups.groups WHERE id = (group_data->>'id')::uuid) THEN
            -- Check if the user is part of the group
            IF NOT EXISTS (
                SELECT 1
                FROM groups.group_members gm
                WHERE gm.group_id = (group_data->>'id')::uuid
                  AND gm.user_id = upsert_group.user_id
            ) THEN
                -- Raise an exception if the user is not part of the group
                RAISE EXCEPTION 'User % is not a member of the group %', upsert_group.user_id, group_data->>'id';
            END IF;
        END IF;

        -- Insert or update the group with the provided 'id'
        INSERT INTO groups.groups (
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
        INSERT INTO groups.groups (
            name, 
            hue
        )
        VALUES (
            group_data->>'name', -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."upsert_user"("user_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
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
        show_in_group_rank,
        start_date,
        days_sober
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
        COALESCE((user_data->>'show_in_group_rank')::boolean, existing_user.show_in_group_rank),
        CASE
            WHEN user_data ? 'start_date' THEN
                (user_data->>'start_date')::timestamptz
            ELSE
                existing_user.start_date
        END,
        CASE
            WHEN user_data ? 'days_sober' THEN
                ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
            ELSE
                existing_user.days_sober
        END
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
        show_in_group_rank = COALESCE((user_data->>'show_in_group_rank')::boolean, users.show_in_group_rank),
        start_date = CASE
            WHEN user_data ? 'start_date' THEN
                (user_data->>'start_date')::timestamptz
            ELSE
                users.start_date
        END,
        days_sober = CASE
            WHEN user_data ? 'days_sober' THEN
                ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
            ELSE
                users.days_sober
        END;
END;$$;


ALTER FUNCTION "public"."upsert_user"("user_data" "jsonb") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "comms"."dr_fred" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "text" "text" NOT NULL,
    "outbound" boolean NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."dr_fred" OWNER TO "postgres";


ALTER TABLE "comms"."dr_fred" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."dr_fred_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."feedback" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "feedback" "text" NOT NULL,
    "timestamp" timestamp with time zone NOT NULL
);


ALTER TABLE "comms"."feedback" OWNER TO "postgres";


ALTER TABLE "comms"."feedback" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."feedback_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."notifications" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "metadata" "jsonb",
    "silent" boolean DEFAULT false
);


ALTER TABLE "comms"."notifications" OWNER TO "postgres";


ALTER TABLE "comms"."notifications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."notifications_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."silent_notifications" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "scheduled_for" timestamp with time zone NOT NULL,
    "metadata" "jsonb" NOT NULL,
    "processed" boolean DEFAULT false NOT NULL
);


ALTER TABLE "comms"."silent_notifications" OWNER TO "postgres";


ALTER TABLE "comms"."silent_notifications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."silent_notifications_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."sms_messages" (
    "id" bigint NOT NULL,
    "user_id" "text",
    "phone_number" "text" NOT NULL,
    "text" "text" NOT NULL,
    "outbound" boolean DEFAULT true NOT NULL,
    "scheduled_for" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "twilio_id" "text",
    "sent_at" timestamp with time zone,
    "canceled" boolean DEFAULT false NOT NULL
);


ALTER TABLE "comms"."sms_messages" OWNER TO "postgres";


ALTER TABLE "comms"."sms_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."sms_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."sms_statuses" (
    "id" bigint NOT NULL,
    "message_id" bigint NOT NULL,
    "status" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."sms_statuses" OWNER TO "postgres";


ALTER TABLE "comms"."sms_statuses" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."sms_statuses_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "groups"."group_activity" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "activity" "public"."group_activity_type" DEFAULT 'joined'::"public"."group_activity_type" NOT NULL
);


ALTER TABLE "groups"."group_activity" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_members" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL
);


ALTER TABLE "groups"."group_members" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_notes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "to_member_id" "text" NOT NULL,
    "from_member_id" "text" NOT NULL,
    "message" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_notes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_pings" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "from_user_id" "text" DEFAULT "now"() NOT NULL,
    "to_user_id" "text" NOT NULL,
    "group_id" "uuid" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_pings" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_subscriptions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "subscribed_to" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_subscriptions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."groups" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "hue" numeric NOT NULL
);


ALTER TABLE "groups"."groups" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."cache" (
    "type" "text" NOT NULL,
    "json" "json" NOT NULL,
    "last_updated" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "library"."cache" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."one_offs" (
    "key" "text" NOT NULL,
    "value" "text" NOT NULL
);


ALTER TABLE "library"."one_offs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."push_check_in" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text"
);


ALTER TABLE "library"."push_check_in" OWNER TO "postgres";


ALTER TABLE "library"."push_check_in" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."push_check_in_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_check_in" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_check_in" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."sms_day" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "hour" bigint,
    "min_offset" bigint,
    "program" "text" NOT NULL,
    "matches_program_time" boolean DEFAULT true,
    "question_id" "text",
    "question_response" "text",
    "text" "text" NOT NULL,
    "additional_id" "text",
    "paid" boolean
);


ALTER TABLE "library"."sms_day" OWNER TO "postgres";


ALTER TABLE "library"."sms_day" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_day_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_eow_summary" (
    "id" bigint NOT NULL,
    "program" "text" DEFAULT ''::"text",
    "question_id" "text",
    "question_response" "text",
    "check_in_days" bigint,
    "sober_days" bigint,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_eow_summary" OWNER TO "postgres";


ALTER TABLE "library"."sms_eow_summary" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_eow_summary_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_get_back" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_get_back" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."sms_inactive" (
    "id" bigint NOT NULL,
    "day" "date",
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_inactive" OWNER TO "postgres";


ALTER TABLE "library"."sms_inactive" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_inactive_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_slip_up" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_slip_up" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."domain_allowlist" (
    "domain" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL,
    "org" "text" DEFAULT ''::"text" NOT NULL,
    "school_id" "text"
);


ALTER TABLE "payment"."domain_allowlist" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."promo_codes" (
    "code" "text" NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL
);


ALTER TABLE "payment"."promo_codes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."sale" (
    "id" bigint NOT NULL,
    "starts_on" "date" NOT NULL,
    "ends_on" "date" NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL
);


ALTER TABLE "payment"."sale" OWNER TO "postgres";


ALTER TABLE "payment"."sale" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "payment"."sale_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_assessment_responses" (
    "id" bigint NOT NULL,
    "user_id" "text" DEFAULT ''::"text" NOT NULL,
    "assessment" "text" NOT NULL,
    "responses" "jsonb" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "programs"."program_assessment_responses" OWNER TO "postgres";


ALTER TABLE "programs"."program_assessment_responses" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_assessment_responses_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_assessments" (
    "id" "text" NOT NULL,
    "desc" "text" NOT NULL,
    "program" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "question_ids" "jsonb" NOT NULL
);


ALTER TABLE "programs"."program_assessments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_feedback" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "order" bigint,
    "links" "jsonb"
);


ALTER TABLE "programs"."program_feedback" OWNER TO "postgres";


ALTER TABLE "programs"."program_feedback" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_feedback_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_guides" (
    "id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "published_on" "date" NOT NULL,
    "thumbnail" "text" NOT NULL,
    "section_1_title" "text",
    "section_1_content" "text",
    "section_2_title" "text",
    "section_2_content" "text",
    "section_3_title" "text",
    "section_3_content" "text",
    "section_4_title" "text",
    "section_4_content" "text",
    "section_5_title" "text",
    "section_5_content" "text",
    "section_6_title" "text",
    "section_6_content" "text",
    "section_7_title" "text",
    "section_7_content" "text",
    "section_8_title" "text",
    "section_8_content" "text",
    "section_9_title" "text",
    "section_9_content" "text",
    "resources" "jsonb"
);


ALTER TABLE "programs"."program_guides" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_guides_qa" (
    "id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "published_on" "date" NOT NULL,
    "thumbnail" "text" NOT NULL,
    "section_1_title" "text",
    "section_1_content" "text",
    "section_2_title" "text",
    "section_2_content" "text",
    "section_3_title" "text",
    "section_3_content" "text",
    "section_4_title" "text",
    "section_4_content" "text",
    "section_5_title" "text",
    "section_5_content" "text",
    "section_6_title" "text",
    "section_6_content" "text",
    "section_7_title" "text",
    "section_7_content" "text",
    "section_8_title" "text",
    "section_8_content" "text",
    "section_9_title" "text",
    "section_9_content" "text",
    "resources" "jsonb"
);


ALTER TABLE "programs"."program_guides_qa" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_messages" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb",
    "page_info" "jsonb",
    "stage" "text",
    "guide_id" "text",
    "notification_body" "text",
    "notification_title" "text"
);


ALTER TABLE "programs"."program_messages" OWNER TO "postgres";


ALTER TABLE "programs"."program_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_messages_qa" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb",
    "page_info" "jsonb",
    "stage" "text",
    "guide_id" "text"
);


ALTER TABLE "programs"."program_messages_qa" OWNER TO "postgres";


ALTER TABLE "programs"."program_messages_qa" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_messages_qa_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_stages" (
    "stage" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "fred_experience" "text"
);


ALTER TABLE "programs"."program_stages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."programs" (
    "id" "text" NOT NULL,
    "descriptions" "text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "programs"."programs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."admins" (
    "id" bigint NOT NULL,
    "auth_id" "uuid" NOT NULL
);


ALTER TABLE "public"."admins" OWNER TO "postgres";


ALTER TABLE "public"."admins" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."admins_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."api_keys" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "key" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "timezone"('utc'::"text", "now"())
);


ALTER TABLE "public"."api_keys" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."events" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "event" "text" NOT NULL,
    "extra_data" "json"
);


ALTER TABLE "public"."events" OWNER TO "postgres";


ALTER TABLE "public"."events" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."events_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."forms" (
    "id" bigint NOT NULL,
    "form_name" "text" NOT NULL,
    "response" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."forms" OWNER TO "postgres";


ALTER TABLE "public"."forms" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."forms_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" "text" NOT NULL,
    "name" "text" NOT NULL,
    "emoji" "text" NOT NULL,
    "days_sober" boolean[],
    "initial_frequency" numeric,
    "show_in_group_rank" boolean,
    "fcm_token" "text",
    "start_date" timestamp with time zone,
    "day_info" "jsonb",
    "auth_id" "uuid",
    "phone_number" "text",
    "adjust_token" "text",
    "email" "text",
    "sms_settings" "jsonb",
    "notification_settings" "jsonb"
);


ALTER TABLE "public"."users" OWNER TO "postgres";


COMMENT ON TABLE "public"."users" IS 'Clear30 users';



CREATE TABLE IF NOT EXISTS "schools"."school_activities" (
    "school_id" "text" NOT NULL,
    "org_name" "text" NOT NULL,
    "title" "text" NOT NULL,
    "date_time" timestamp with time zone NOT NULL,
    "link" "text" NOT NULL,
    "subtitle" "text",
    "description" "text",
    "facilitated_by" "text",
    "location" "text",
    "phone_number" "text",
    "email" "text",
    "repeats" "public"."school_activity_repeat_rate",
    "sub_links" "json",
    "id" bigint NOT NULL
);


ALTER TABLE "schools"."school_activities" OWNER TO "postgres";


ALTER TABLE "schools"."school_activities" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_activities_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."school_messages" (
    "school_id" "text" NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "message" "text" NOT NULL,
    "id" bigint NOT NULL,
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb"
);


ALTER TABLE "schools"."school_messages" OWNER TO "postgres";


ALTER TABLE "schools"."school_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."school_resources" (
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "link" "text" NOT NULL,
    "description" "text",
    "badge" "text",
    "phone_number" "text",
    "location" "text",
    "section_title" "text" NOT NULL,
    "school_id" "text" NOT NULL,
    "id" bigint NOT NULL
);


ALTER TABLE "schools"."school_resources" OWNER TO "postgres";


ALTER TABLE "schools"."school_resources" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_resources_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."schools" (
    "school_id" "text" NOT NULL,
    "short_name" "text" NOT NULL,
    "long_name" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "reddit_flair" "text",
    "sf_symbol_icon" "text"
);


ALTER TABLE "schools"."schools" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "symptoms"."symptom_messages" (
    "symptom" "text" NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "symptoms"."symptom_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "symptoms"."symptom_tip_sections" (
    "id" bigint NOT NULL,
    "symptom_tip" bigint NOT NULL,
    "heading" "text" NOT NULL,
    "content" "text" NOT NULL,
    "example" "text"
);


ALTER TABLE "symptoms"."symptom_tip_sections" OWNER TO "postgres";


ALTER TABLE "symptoms"."symptom_tip_sections" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "symptoms"."symptom_tip_sections_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "symptoms"."symptom_tips" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "symptom" "text" NOT NULL
);


ALTER TABLE "symptoms"."symptom_tips" OWNER TO "postgres";


ALTER TABLE "symptoms"."symptom_tips" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "symptoms"."symptom_tips_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "symptoms"."symptoms" (
    "symptom" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "emoji" "text" NOT NULL,
    "resources" "jsonb",
    "claire_prompts" "jsonb"
);


ALTER TABLE "symptoms"."symptoms" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."assessment_responses_clear30" WITH ("security_invoker"='true') AS
 SELECT "par"."user_id",
    "u"."name" AS "user_name",
    "par"."id" AS "response_id",
    ("par"."responses" -> 'LO-Age'::"text") AS "Age",
    ("par"."responses" -> 'Consumption-Method'::"text") AS "Consumption Method",
    ("par"."responses" -> 'Days-Using'::"text") AS "Days Using",
    ("par"."responses" -> 'Trigger'::"text") AS "Triggers",
    ("par"."responses" -> 'Help-Harm'::"text") AS "Help vs Harm",
    ("par"."responses" -> 'Previous-Break'::"text") AS "Previous Break",
    ("par"."responses" -> 'Break-Reason'::"text") AS "Break Reason",
    ("par"."responses" -> 'Goal30'::"text") AS "30 Day Goal",
    ("par"."responses" -> 'Commitment'::"text") AS "Commitment",
    ("par"."responses" -> 'Then-What'::"text") AS "Then What",
    "par"."timestamp",
    ("par"."responses" -> 'Money-Spent'::"text") AS "Money Spent"
   FROM ("programs"."program_assessment_responses" "par"
     JOIN "public"."users" "u" ON (("par"."user_id" = "u"."id")))
  WHERE ("par"."assessment" = 'clear30'::"text")
  ORDER BY "par"."timestamp" DESC;


ALTER TABLE "views"."assessment_responses_clear30" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."assessment_responses_life" WITH ("security_invoker"='true') AS
 SELECT "par"."user_id",
    "u"."name" AS "user_name",
    "par"."id" AS "response_id",
    ("par"."responses" -> 'Days-Using'::"text") AS "Days Using",
    ("par"."responses" -> 'Participation-Reason'::"text") AS "Participation Reason",
    ("par"."responses" -> 'Helpful'::"text") AS "Helpful",
    ("par"."responses" -> 'Not-Helpful'::"text") AS "Not Helpful",
    ("par"."responses" -> 'Identity'::"text") AS "Identity",
    ("par"."responses" -> 'Self-Growth'::"text") AS "Self Growth",
    ("par"."responses" -> 'Relationship'::"text") AS "Relationship",
    ("par"."responses" -> 'Positive-Results'::"text") AS "Positive Results",
    ("par"."responses" -> 'Mental-Clarity'::"text") AS "Mental Clarity",
    ("par"."responses" -> 'Worth-It'::"text") AS "Worth It",
    ("par"."responses" -> 'Comments'::"text") AS "Comments",
    ("par"."responses" -> 'LO-Use-State'::"text") AS "LO-Use-State",
    ("par"."responses" -> 'Moderation-Tech'::"text") AS "Moderation",
    "par"."timestamp"
   FROM ("programs"."program_assessment_responses" "par"
     JOIN "public"."users" "u" ON (("par"."user_id" = "u"."id")))
  WHERE ("par"."assessment" = 'life'::"text")
  ORDER BY "par"."timestamp" DESC;


ALTER TABLE "views"."assessment_responses_life" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."claire_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."name",
    ("last_message"."extra_data" ->> 'claire_message'::"text") AS "text",
    "last_message"."timestamp"
   FROM (( SELECT DISTINCT ON ("events"."user_id") "events"."user_id",
            "events"."id",
            "events"."timestamp",
            "events"."event",
            "events"."extra_data"
           FROM "public"."events"
          WHERE (("events"."event" = 'used_claire'::"text") AND ("events"."user_id" IS NOT NULL))
          ORDER BY "events"."user_id", "events"."timestamp" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."timestamp" DESC;


ALTER TABLE "views"."claire_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."dr_fred_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."name",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM (( SELECT DISTINCT ON ("dr_fred"."user_id") "dr_fred"."user_id",
            "dr_fred"."id",
            "dr_fred"."text",
            "dr_fred"."created_at",
            "dr_fred"."outbound"
           FROM "comms"."dr_fred"
          ORDER BY "dr_fred"."user_id", "dr_fred"."created_at" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."dr_fred_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."groups_summary" AS
 SELECT "g"."id" AS "group_id",
    "g"."name" AS "group_name",
    ( SELECT "json_agg"(DISTINCT "u"."name") AS "json_agg"
           FROM ("groups"."group_members" "gm"
             LEFT JOIN "public"."users" "u" ON (("gm"."user_id" = "u"."id")))
          WHERE ("gm"."group_id" = "g"."id")) AS "members_in_group",
    "json_agg"("ga"."activity") AS "activities_in_group",
    "max"("ga"."timestamp") AS "most_recent_activity_timestamp"
   FROM ("groups"."groups" "g"
     LEFT JOIN "groups"."group_activity" "ga" ON (("g"."id" = "ga"."group_id")))
  GROUP BY "g"."id", "g"."name"
  ORDER BY "g"."id";


ALTER TABLE "views"."groups_summary" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."sms_non_user_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."phone_number",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM ( SELECT DISTINCT ON ("sms_messages"."phone_number") "sms_messages"."user_id",
            "sms_messages"."phone_number",
            "sms_messages"."id",
            "sms_messages"."text",
            "sms_messages"."created_at",
            "sms_messages"."outbound"
           FROM "comms"."sms_messages"
          WHERE (("sms_messages"."canceled" = false) AND ("sms_messages"."user_id" IS NULL) AND ("sms_messages"."scheduled_for" <= "now"()))
          ORDER BY "sms_messages"."phone_number", "sms_messages"."created_at" DESC) "last_message"
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."sms_non_user_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."sms_user_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."phone_number",
    "u"."name",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM (( SELECT DISTINCT ON ("sms_messages"."user_id") "sms_messages"."user_id",
            "sms_messages"."id",
            "sms_messages"."text",
            "sms_messages"."created_at",
            "sms_messages"."outbound"
           FROM "comms"."sms_messages"
          WHERE (("sms_messages"."canceled" = false) AND ("sms_messages"."user_id" IS NOT NULL) AND ("sms_messages"."scheduled_for" <= "now"()))
          ORDER BY "sms_messages"."user_id", "sms_messages"."created_at" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."sms_user_conversations" OWNER TO "postgres";


ALTER TABLE ONLY "comms"."dr_fred"
    ADD CONSTRAINT "dr_fred_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."feedback"
    ADD CONSTRAINT "feedback_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."silent_notifications"
    ADD CONSTRAINT "silent_notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_messages"
    ADD CONSTRAINT "sms_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_statuses"
    ADD CONSTRAINT "sms_statuses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_unique_constraint" UNIQUE ("group_id", "from_member_id", "to_member_id", "timestamp");



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."groups"
    ADD CONSTRAINT "groups_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "unique_group_activity" UNIQUE ("group_id", "user_id", "timestamp");



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "unique_group_user" UNIQUE ("group_id", "user_id");



ALTER TABLE ONLY "library"."cache"
    ADD CONSTRAINT "cache_pkey" PRIMARY KEY ("type");



ALTER TABLE ONLY "library"."sms_check_in"
    ADD CONSTRAINT "check_in_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "library"."sms_get_back"
    ADD CONSTRAINT "get_back_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "library"."one_offs"
    ADD CONSTRAINT "one_offs_pkey" PRIMARY KEY ("key");



ALTER TABLE ONLY "library"."push_check_in"
    ADD CONSTRAINT "push_check_in_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_day"
    ADD CONSTRAINT "sms_day_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_eow_summary"
    ADD CONSTRAINT "sms_eow_summary_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_inactive"
    ADD CONSTRAINT "sms_inactive_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_slip_up"
    ADD CONSTRAINT "sms_slip_up_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "payment"."domain_allowlist"
    ADD CONSTRAINT "domain_allowlist_pkey" PRIMARY KEY ("domain");



ALTER TABLE ONLY "payment"."promo_codes"
    ADD CONSTRAINT "promo_codes_pkey" PRIMARY KEY ("code");



ALTER TABLE ONLY "payment"."sale"
    ADD CONSTRAINT "sale_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_assessments"
    ADD CONSTRAINT "program_assessments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_feedback"
    ADD CONSTRAINT "program_feedback_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_guides"
    ADD CONSTRAINT "program_guides_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_guides_qa"
    ADD CONSTRAINT "program_guides_qa_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_stages"
    ADD CONSTRAINT "program_stages_pkey" PRIMARY KEY ("stage");



ALTER TABLE ONLY "programs"."programs"
    ADD CONSTRAINT "programs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."admins"
    ADD CONSTRAINT "admins_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."api_keys"
    ADD CONSTRAINT "api_keys_key_key" UNIQUE ("key");



ALTER TABLE ONLY "public"."api_keys"
    ADD CONSTRAINT "api_keys_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."events"
    ADD CONSTRAINT "events_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."forms"
    ADD CONSTRAINT "forms_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_key" UNIQUE ("auth_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_activities"
    ADD CONSTRAINT "school_activities_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_messages"
    ADD CONSTRAINT "school_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_resources"
    ADD CONSTRAINT "school_resources_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."schools"
    ADD CONSTRAINT "schools_pkey" PRIMARY KEY ("school_id");



ALTER TABLE ONLY "symptoms"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_pkey" PRIMARY KEY ("symptom", "message");



ALTER TABLE ONLY "symptoms"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "symptoms"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "symptoms"."symptoms"
    ADD CONSTRAINT "symptoms_pkey" PRIMARY KEY ("symptom");



CREATE OR REPLACE TRIGGER "dr_fred_notify_team" AFTER INSERT ON "comms"."dr_fred" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/dr_fred_notify_team', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "notification_send_trigger" AFTER INSERT ON "comms"."notifications" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/notification_send', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "schedule_dr_fred_message_notification" AFTER INSERT ON "comms"."dr_fred" FOR EACH ROW EXECUTE FUNCTION "comms"."add_dr_fred_message_notification"();



CREATE OR REPLACE TRIGGER "schedule_group_note_notification" AFTER INSERT ON "groups"."group_notes" FOR EACH ROW EXECUTE FUNCTION "comms"."add_group_note_notification"();



CREATE OR REPLACE TRIGGER "schedule_group_ping_notification" AFTER INSERT ON "groups"."group_pings" FOR EACH ROW EXECUTE FUNCTION "comms"."add_group_ping_notification"();



CREATE OR REPLACE TRIGGER "amplitude_forward_assessment_response" AFTER INSERT ON "programs"."program_assessment_responses" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_assessment_response', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "amplitude_forward_event" AFTER INSERT ON "public"."events" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_event', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



ALTER TABLE ONLY "comms"."dr_fred"
    ADD CONSTRAINT "dr_fred_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "comms"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "comms"."silent_notifications"
    ADD CONSTRAINT "silent_notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "comms"."sms_messages"
    ADD CONSTRAINT "sms_messages_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "comms"."sms_statuses"
    ADD CONSTRAINT "sms_statuses_message_id_fkey" FOREIGN KEY ("message_id") REFERENCES "comms"."sms_messages"("id");



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_from_member_id_fkey" FOREIGN KEY ("from_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_to_member_id_fkey" FOREIGN KEY ("to_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_from_user_id_fkey" FOREIGN KEY ("from_user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_to_user_id_fkey" FOREIGN KEY ("to_user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_subscribed_to_fkey" FOREIGN KEY ("subscribed_to") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "library"."sms_day"
    ADD CONSTRAINT "sms_day_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "library"."sms_eow_summary"
    ADD CONSTRAINT "sms_eow_summary_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "payment"."domain_allowlist"
    ADD CONSTRAINT "domain_allowlist_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_assessment_fkey" FOREIGN KEY ("assessment") REFERENCES "programs"."program_assessments"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_assessments"
    ADD CONSTRAINT "program_assessments_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_feedback"
    ADD CONSTRAINT "program_feedback_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_guide_id_fkey" FOREIGN KEY ("guide_id") REFERENCES "programs"."program_guides"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_guide_id_fkey" FOREIGN KEY ("guide_id") REFERENCES "programs"."program_guides_qa"("id");



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_stage_fkey" FOREIGN KEY ("stage") REFERENCES "programs"."program_stages"("stage") ON UPDATE CASCADE;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_stage_fkey" FOREIGN KEY ("stage") REFERENCES "programs"."program_stages"("stage") ON UPDATE CASCADE;



ALTER TABLE ONLY "public"."admins"
    ADD CONSTRAINT "admins_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "schools"."school_activities"
    ADD CONSTRAINT "school_activities_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "schools"."school_messages"
    ADD CONSTRAINT "school_messages_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "schools"."school_resources"
    ADD CONSTRAINT "school_resources_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_symptom_fkey1" FOREIGN KEY ("symptom") REFERENCES "symptoms"."symptoms"("symptom") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_symptom_tip_fkey1" FOREIGN KEY ("symptom_tip") REFERENCES "symptoms"."symptom_tips"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_symptom_fkey1" FOREIGN KEY ("symptom") REFERENCES "symptoms"."symptoms"("symptom") ON UPDATE CASCADE ON DELETE CASCADE;



CREATE POLICY "Admin only" ON "comms"."dr_fred" USING ("public"."admin_check"());



CREATE POLICY "Admin only" ON "comms"."feedback" FOR SELECT USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Admin only" ON "comms"."sms_messages" USING ("public"."admin_check"());



CREATE POLICY "Disable access for all" ON "comms"."notifications" USING (false);



CREATE POLICY "Disable access for all" ON "comms"."sms_statuses" USING (false);



CREATE POLICY "Disable all access" ON "comms"."silent_notifications" USING (false);



CREATE POLICY "User can get messages" ON "comms"."dr_fred" FOR SELECT USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = ( SELECT "auth"."uid"() AS "uid")))));



CREATE POLICY "Users can add feedback" ON "comms"."feedback" FOR INSERT WITH CHECK (true);



ALTER TABLE "comms"."dr_fred" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."feedback" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."silent_notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_statuses" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all users" ON "groups"."group_activity" USING (false);



CREATE POLICY "Disable access for all users" ON "groups"."group_members" USING (false);



CREATE POLICY "Disable access for all users" ON "groups"."group_notes" USING (false);



CREATE POLICY "Disable access for all users" ON "groups"."group_pings" USING (false);



CREATE POLICY "Disable access for all users" ON "groups"."group_subscriptions" USING (false);



CREATE POLICY "Disable access for all users" ON "groups"."groups" USING (false);



ALTER TABLE "groups"."group_activity" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_notes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_pings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_subscriptions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."groups" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Access to authed" ON "library"."push_check_in" FOR SELECT USING (("auth"."uid"() IS NOT NULL));



CREATE POLICY "Access to authed" ON "library"."sms_day" FOR SELECT USING (("auth"."uid"() IS NOT NULL));



CREATE POLICY "Disable access for all" ON "library"."cache" USING (false);



CREATE POLICY "Disable access for all" ON "library"."one_offs" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_check_in" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_get_back" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_slip_up" USING (false);



CREATE POLICY "Disable all access" ON "library"."sms_eow_summary" USING (false);



CREATE POLICY "Disable all access" ON "library"."sms_inactive" USING (false);



ALTER TABLE "library"."cache" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."one_offs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."push_check_in" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_check_in" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_day" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_eow_summary" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_get_back" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_inactive" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_slip_up" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable all access" ON "payment"."domain_allowlist" USING (false);



CREATE POLICY "Disable all access" ON "payment"."promo_codes" USING (false);



CREATE POLICY "Disable public access" ON "payment"."sale" USING (false);



ALTER TABLE "payment"."domain_allowlist" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."promo_codes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."sale" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Admin only" ON "programs"."program_assessment_responses" USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Disable access for all" ON "programs"."program_assessments" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_feedback" FOR SELECT USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_guides" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_messages" FOR SELECT USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_stages" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."programs" FOR SELECT USING (false);



ALTER TABLE "programs"."program_assessment_responses" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_assessments" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_feedback" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_guides" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_guides_qa" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_messages_qa" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_stages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."programs" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Admin only" ON "public"."events" FOR SELECT USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Disable access for all" ON "public"."admins" USING (false);



CREATE POLICY "Disable access for all" ON "public"."forms" USING (false);



CREATE POLICY "Disable public access for all users" ON "public"."api_keys" USING (false);



CREATE POLICY "Enable SELECT for authed users and admins" ON "public"."users" FOR SELECT USING ((("auth_id" = ( SELECT "auth"."uid"() AS "uid")) OR "public"."admin_check"()));



CREATE POLICY "Enable UPDATE for authed users" ON "public"."users" FOR UPDATE USING (("auth_id" = ( SELECT "auth"."uid"() AS "uid"))) WITH CHECK ((("auth_id" = ( SELECT "auth"."uid"() AS "uid")) AND ("id" = "id")));



CREATE POLICY "Enable insert access for all users" ON "public"."events" FOR INSERT WITH CHECK (true);



ALTER TABLE "public"."admins" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."api_keys" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."events" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."forms" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all" ON "schools"."school_activities" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."school_messages" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."school_resources" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."schools" USING (false);



ALTER TABLE "schools"."school_activities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."school_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."school_resources" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."schools" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all" ON "symptoms"."symptom_messages" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptom_tip_sections" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptom_tips" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptoms" USING (false);



ALTER TABLE "symptoms"."symptom_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptom_tip_sections" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptom_tips" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptoms" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "comms" TO "service_role";
GRANT USAGE ON SCHEMA "comms" TO "authenticated";






GRANT USAGE ON SCHEMA "library" TO "anon";
GRANT USAGE ON SCHEMA "library" TO "authenticated";
GRANT USAGE ON SCHEMA "library" TO "service_role";






GRANT USAGE ON SCHEMA "programs" TO "authenticated";
GRANT USAGE ON SCHEMA "programs" TO "service_role";



GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT USAGE ON SCHEMA "views" TO "authenticated";



























































































































































































































GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."admin_check"() TO "anon";
GRANT ALL ON FUNCTION "public"."admin_check"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."admin_check"() TO "service_role";



GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "anon";
GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "service_role";



GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_payment_settings"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_payment_settings"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_payment_settings"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_user_id"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_id"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_id"() TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "service_role";



GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "anon";
GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "service_role";



GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "anon";
GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "authenticated";
GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_clear"() TO "anon";
GRANT ALL ON FUNCTION "public"."sms_clear"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_clear"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "service_role";



GRANT ALL ON TABLE "comms"."dr_fred" TO "authenticated";
GRANT ALL ON TABLE "comms"."dr_fred" TO "service_role";



GRANT ALL ON TABLE "comms"."feedback" TO "anon";
GRANT ALL ON TABLE "comms"."feedback" TO "authenticated";
GRANT ALL ON TABLE "comms"."feedback" TO "service_role";



GRANT ALL ON TABLE "comms"."notifications" TO "anon";
GRANT ALL ON TABLE "comms"."notifications" TO "authenticated";
GRANT ALL ON TABLE "comms"."notifications" TO "service_role";



GRANT ALL ON TABLE "comms"."sms_messages" TO "service_role";
GRANT ALL ON TABLE "comms"."sms_messages" TO "authenticated";



GRANT ALL ON TABLE "comms"."sms_statuses" TO "service_role";
GRANT ALL ON TABLE "comms"."sms_statuses" TO "authenticated";


















GRANT ALL ON TABLE "groups"."group_activity" TO "anon";
GRANT ALL ON TABLE "groups"."group_activity" TO "authenticated";



GRANT ALL ON TABLE "groups"."group_members" TO "anon";
GRANT ALL ON TABLE "groups"."group_members" TO "authenticated";



GRANT ALL ON TABLE "groups"."group_notes" TO "anon";
GRANT ALL ON TABLE "groups"."group_notes" TO "authenticated";



GRANT ALL ON TABLE "groups"."group_pings" TO "anon";
GRANT ALL ON TABLE "groups"."group_pings" TO "authenticated";



GRANT ALL ON TABLE "groups"."group_subscriptions" TO "anon";
GRANT ALL ON TABLE "groups"."group_subscriptions" TO "authenticated";



GRANT ALL ON TABLE "groups"."groups" TO "anon";
GRANT ALL ON TABLE "groups"."groups" TO "authenticated";



GRANT ALL ON TABLE "library"."cache" TO "anon";
GRANT ALL ON TABLE "library"."cache" TO "authenticated";
GRANT ALL ON TABLE "library"."cache" TO "service_role";



GRANT ALL ON TABLE "library"."one_offs" TO "anon";
GRANT ALL ON TABLE "library"."one_offs" TO "authenticated";
GRANT ALL ON TABLE "library"."one_offs" TO "service_role";



GRANT ALL ON TABLE "library"."push_check_in" TO "authenticated";
GRANT ALL ON TABLE "library"."push_check_in" TO "service_role";



GRANT ALL ON TABLE "library"."sms_check_in" TO "anon";
GRANT ALL ON TABLE "library"."sms_check_in" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_check_in" TO "service_role";



GRANT ALL ON TABLE "library"."sms_day" TO "anon";
GRANT ALL ON TABLE "library"."sms_day" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_day" TO "service_role";



GRANT ALL ON TABLE "library"."sms_eow_summary" TO "service_role";



GRANT ALL ON TABLE "library"."sms_get_back" TO "anon";
GRANT ALL ON TABLE "library"."sms_get_back" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_get_back" TO "service_role";



GRANT ALL ON TABLE "library"."sms_inactive" TO "service_role";



GRANT ALL ON TABLE "library"."sms_slip_up" TO "anon";
GRANT ALL ON TABLE "library"."sms_slip_up" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_slip_up" TO "service_role";












GRANT SELECT ON TABLE "programs"."program_assessment_responses" TO "authenticated";
GRANT ALL ON TABLE "programs"."program_assessment_responses" TO "service_role";



GRANT ALL ON TABLE "programs"."program_assessments" TO "service_role";



GRANT ALL ON TABLE "programs"."program_feedback" TO "service_role";



GRANT ALL ON TABLE "programs"."program_guides" TO "service_role";



GRANT ALL ON TABLE "programs"."program_guides_qa" TO "service_role";



GRANT ALL ON TABLE "programs"."program_messages" TO "service_role";



GRANT ALL ON TABLE "programs"."program_messages_qa" TO "service_role";



GRANT ALL ON TABLE "programs"."program_stages" TO "service_role";



GRANT ALL ON TABLE "programs"."programs" TO "service_role";



GRANT ALL ON TABLE "public"."admins" TO "anon";
GRANT ALL ON TABLE "public"."admins" TO "authenticated";
GRANT ALL ON TABLE "public"."admins" TO "service_role";



GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."api_keys" TO "anon";
GRANT ALL ON TABLE "public"."api_keys" TO "authenticated";
GRANT ALL ON TABLE "public"."api_keys" TO "service_role";



GRANT ALL ON TABLE "public"."events" TO "anon";
GRANT ALL ON TABLE "public"."events" TO "authenticated";
GRANT ALL ON TABLE "public"."events" TO "service_role";



GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."forms" TO "anon";
GRANT ALL ON TABLE "public"."forms" TO "authenticated";
GRANT ALL ON TABLE "public"."forms" TO "service_role";



GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



GRANT ALL ON TABLE "schools"."school_activities" TO "anon";
GRANT ALL ON TABLE "schools"."school_activities" TO "authenticated";



GRANT ALL ON TABLE "schools"."school_messages" TO "anon";
GRANT ALL ON TABLE "schools"."school_messages" TO "authenticated";



GRANT ALL ON TABLE "schools"."school_resources" TO "anon";
GRANT ALL ON TABLE "schools"."school_resources" TO "authenticated";



GRANT ALL ON TABLE "schools"."schools" TO "anon";
GRANT ALL ON TABLE "schools"."schools" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_messages" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_messages" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_tip_sections" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_tip_sections" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_tips" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_tips" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptoms" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptoms" TO "authenticated";



GRANT SELECT ON TABLE "views"."assessment_responses_clear30" TO "authenticated";



GRANT SELECT ON TABLE "views"."assessment_responses_life" TO "authenticated";



GRANT SELECT ON TABLE "views"."claire_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."dr_fred_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."sms_non_user_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."sms_user_conversations" TO "authenticated";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "service_role";






























RESET ALL;
