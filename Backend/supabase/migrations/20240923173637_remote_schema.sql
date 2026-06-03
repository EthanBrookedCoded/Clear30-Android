

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


CREATE SCHEMA IF NOT EXISTS "content";


ALTER SCHEMA "content" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgsodium" WITH SCHEMA "pgsodium";






COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgjwt" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "vector" WITH SCHEMA "public";






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


CREATE OR REPLACE FUNCTION "public"."delete_group"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM group_notes
    WHERE group_notes.group_id = delete_group.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM group_activity
    WHERE group_activity.group_id = delete_group.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM group_members
    WHERE group_members.group_id = delete_group.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups
    WHERE groups.id = delete_group.group_id;

END;$$;


ALTER FUNCTION "public"."delete_group"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_check_in_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'check_in_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if the cache already has a value for "check_in_messages"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSONB result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM content.check_in_messages  -- Ensure the correct schema is used
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_check_in_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_claire_prompt"() RETURNS "text"
    LANGUAGE "plpgsql"
    SET "search_path" TO ''
    AS $$
DECLARE
    claire_prompt_value TEXT;
BEGIN
    -- Query to get the value of the row where the key is 'claire_prompt'
    SELECT value INTO claire_prompt_value
    FROM content.one_offs
    WHERE key = 'claire_prompt';

    -- Return the retrieved value
    RETURN claire_prompt_value;
END;
$$;


ALTER FUNCTION "public"."get_claire_prompt"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_generic_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    school_demo CONSTANT TEXT := 'generic_demo';
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    -- Step 1: Check if there's a cached result for "school_demo"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = school_demo;

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
        FROM content.school_resources r
        WHERE r.school_id = umich_school_id
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
                       'meditation_name', COALESCE(m.meditation_name, NULL),
                       'meditation_link', COALESCE(m.meditation_link, NULL),
                       'prompts', (
                           SELECT jsonb_object_agg(c.title, c.prompt)
                           FROM content.claire_prompts c
                           WHERE c.school_message_id = m.id
                       ),
                       'journal_prompts', (
                           SELECT jsonb_agg(j.prompt)
                           FROM content.journal_prompts j
                           WHERE j.school_message_id = m.id
                       ),
                       'resources', (
                           SELECT jsonb_object_agg(r.title, r.url)
                           FROM (
                               SELECT rt.title, rt.url
                               FROM content.reddit_threads rt
                               WHERE rt.school_message_id = m.id
                               UNION ALL
                               SELECT yt.title, yt.url
                               FROM content.youtube_videos yt
                               WHERE yt.school_message_id = m.id
                           ) r
                       )
                   )
               ) AS messages_array
        FROM content.school_messages m
        WHERE m.school_id = umich_school_id
    )
    
    -- Step 4: Build the JSON result using "school_demo" for school data but "umich" for resources, activities, and messages
    SELECT jsonb_build_object(
        'school_id', s.school_id,
        'short_name', s.short_name,
        'long_name', s.long_name,
        'reddit_flair', COALESCE(s.reddit_flair, NULL),
        'color1', s.color1,
        'color2', s.color2,
        'sf_symbol_icon', COALESCE(s.sf_symbol_icon, NULL),
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
    FROM content.schools s
    LEFT JOIN content.school_activities a ON a.school_id = umich_school_id
    LEFT JOIN final_resources fr ON fr.school_id = umich_school_id
    LEFT JOIN aggregated_messages am ON TRUE
    WHERE s.school_id = school_demo
    GROUP BY s.school_id, fr.resources, am.messages_array;

    -- Step 5: Insert the computed result into the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (school_demo, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 6: Return the computed result
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_generic_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_get_back_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'get_back_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "get_back_messages"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM content.get_back_messages  -- Ensure the correct schema is used
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_get_back_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_group"("group_id" "uuid") RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
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
                        'daysSober', u.days_sober,
                        'initialFrequency', COALESCE(NULLIF(u.initial_frequency, 0), 0),
                        'showInRank', u.show_in_group_rank,
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', gn.timestamp
                                )
                            )
                            FROM group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', ga.timestamp
                    )
                )
                FROM group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json)
        )
        FROM groups g
        WHERE g.id = group_id
    );
END;$$;


ALTER FUNCTION "public"."get_group"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_payment_settings"() RETURNS "jsonb"
    LANGUAGE "plpgsql"
    SET "search_path" TO ''
    AS $$
DECLARE
    payment_settings TEXT;
BEGIN
    -- Retrieve the string value for the key "payment_settings" from the content.one_offs table
    SELECT value
    INTO payment_settings
    FROM content.one_offs
    WHERE key = 'payment_settings';

    -- Return the value as JSONB
    RETURN payment_settings::JSONB;
END;
$$;


ALTER FUNCTION "public"."get_payment_settings"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_data"("school_id" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $_$DECLARE
    result JSONB;
    cached_result JSONB;
BEGIN
    -- Step 1: Check if there's a cached result for this school_id
    SELECT json INTO cached_result
    FROM content.cache
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
        FROM content.school_resources r
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
                       'meditation_name', COALESCE(m.meditation_name, NULL),
                       'meditation_link', COALESCE(m.meditation_link, NULL),
                       'prompts', (
                           SELECT jsonb_object_agg(c.title, c.prompt)
                           FROM content.claire_prompts c
                           WHERE c.school_message_id = m.id
                       ),
                       'journal_prompts', (
                           SELECT jsonb_agg(j.prompt)
                           FROM content.journal_prompts j
                           WHERE j.school_message_id = m.id
                       ),
                       'resources', (
                           SELECT jsonb_object_agg(r.title, r.url)
                           FROM (
                               SELECT rt.title, rt.url
                               FROM content.reddit_threads rt
                               WHERE rt.school_message_id = m.id
                               UNION ALL
                               SELECT yt.title, yt.url
                               FROM content.youtube_videos yt
                               WHERE yt.school_message_id = m.id
                           ) r
                       )
                   )
               ) AS messages_array
        FROM content.school_messages m
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
    FROM content.schools s
    LEFT JOIN content.school_activities a ON s.school_id = a.school_id
    LEFT JOIN final_resources fr ON s.school_id = fr.school_id
    LEFT JOIN aggregated_messages am ON TRUE  -- Ensure that all messages for this school are selected
    WHERE s.school_id = $1
    GROUP BY s.school_id, fr.resources, am.messages_array;

    -- Step 5: Insert the computed result into the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES ($1, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 6: Return the computed result
    RETURN result;
END;$_$;


ALTER FUNCTION "public"."get_school_data"("school_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    school_demo CONSTANT TEXT := 'school_demo';
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    -- Step 1: Check if there's a cached result for "school_demo"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = school_demo;

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
        FROM content.school_resources r
        WHERE r.school_id = umich_school_id
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
                       'meditation_name', COALESCE(m.meditation_name, NULL),
                       'meditation_link', COALESCE(m.meditation_link, NULL),
                       'prompts', (
                           SELECT jsonb_object_agg(c.title, c.prompt)
                           FROM content.claire_prompts c
                           WHERE c.school_message_id = m.id
                       ),
                       'journal_prompts', (
                           SELECT jsonb_agg(j.prompt)
                           FROM content.journal_prompts j
                           WHERE j.school_message_id = m.id
                       ),
                       'resources', (
                           SELECT jsonb_object_agg(r.title, r.url)
                           FROM (
                               SELECT rt.title, rt.url
                               FROM content.reddit_threads rt
                               WHERE rt.school_message_id = m.id
                               UNION ALL
                               SELECT yt.title, yt.url
                               FROM content.youtube_videos yt
                               WHERE yt.school_message_id = m.id
                           ) r
                       )
                   )
               ) AS messages_array
        FROM content.school_messages m
        WHERE m.school_id = umich_school_id
    )
    
    -- Step 4: Build the JSON result using "school_demo" for school data but "umich" for resources, activities, and messages
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
    FROM content.schools s
    LEFT JOIN content.school_activities a ON a.school_id = umich_school_id
    LEFT JOIN final_resources fr ON fr.school_id = umich_school_id
    LEFT JOIN aggregated_messages am ON TRUE
    WHERE s.school_id = school_demo
    GROUP BY s.school_id, fr.resources, am.messages_array;

    -- Step 5: Insert the computed result into the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (school_demo, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 6: Return the computed result
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_school_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_slip_up_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'slip_up_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "slip_up_messages"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM content.slip_up_messages  -- Ensure the correct schema is used
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_slip_up_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_symptom_infos"() RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
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
    FROM content.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSON result
    -- Loop through each symptom and gather the colors, tips, reddit threads, and prompts for that symptom
    FOR record IN 
        SELECT s.emoji || ' ' || s.symptom AS symptom_key, s.symptom, s.color1, s.color2
        FROM content.symptoms s
        LEFT JOIN content.symptom_tips st ON s.symptom = st.symptom
        GROUP BY s.emoji, s.symptom, st.color1, st.color2
        ORDER BY s.symptom
    LOOP
        -- Initialize tips array for the current symptom
        tips_array := '[';
        first_tip := TRUE;

        -- Get all tips for the current symptom by matching the symptom name
        FOR tip_record IN
            SELECT st.id, st.title, st.color1, st.color2
            FROM content.symptom_tips st
            WHERE st.symptom = record.symptom
        LOOP
            -- Initialize sections array for the current tip
            sections_array := '[';
            first_section := TRUE;

            -- Get all sections for the current tip
            FOR section_record IN
                SELECT sts.heading, sts.content, sts.example
                FROM content.symptom_tip_sections sts
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

        -- Initialize the reddit threads for the current symptom
        reddits := '{';
        first_reddit := TRUE;

        -- Get all Reddit threads for the current symptom
        FOR reddit_record IN
            SELECT rt.title, rt.url
            FROM content.reddit_threads rt
            WHERE rt.symptom = record.symptom
        LOOP
            -- Handle commas between reddit entries
            IF NOT first_reddit THEN
                reddits := reddits || ', ';
            END IF;

            -- Add the reddit thread with title as key and url as value
            reddits := reddits || to_json(reddit_record.title) || ': ' || to_json(reddit_record.url);

            -- Mark that this is no longer the first reddit entry
            first_reddit := FALSE;
        END LOOP;

        -- Close the reddit threads object
        reddits := reddits || '}';

        -- Initialize the prompts for the current symptom
        prompts := '{';
        first_prompt := TRUE;

        -- Get all Claire prompts for the current symptom
        FOR prompt_record IN
            SELECT cp.title, cp.prompt
            FROM content.claire_prompts cp
            WHERE cp.symptom = record.symptom
        LOOP
            -- Handle commas between prompt entries
            IF NOT first_prompt THEN
                prompts := prompts || ', ';
            END IF;

            -- Add the prompt with title as key and prompt as value
            prompts := prompts || to_json(prompt_record.title) || ': ' || to_json(prompt_record.prompt);

            -- Mark that this is no longer the first prompt entry
            first_prompt := FALSE;
        END LOOP;

        -- Close the prompts object
        prompts := prompts || '}';

        -- Handle commas between symptom entries
        IF NOT first_entry THEN
            result := result || ', ';
        END IF;

        -- Add the symptom's colors, tips, reddits, and prompts to the result
        result := result || '"' || record.symptom_key || '": {"color1": ' || to_json(record.color1) || ', "color2": ' || to_json(record.color2) || ', "tips": ' || tips_array || ', "reddits": ' || reddits || ', "prompts": ' || prompts || '}';

        -- Mark that this is no longer the first entry
        first_entry := FALSE;
    END LOOP;

    -- Close the JSON object with a closing brace
    result := result || '}';

    -- Step 4: Store the computed result in the cache table
    INSERT INTO content.cache (type, json, last_updated)
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
    AS $$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'symptom_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "symptom_messages"
    SELECT json INTO cached_result
    FROM content.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    WITH aggregated_symptoms AS (
        SELECT s.emoji || ' ' || s.symptom AS symptom_key,
               jsonb_agg(sm.message) AS messages_array
        FROM content.symptom_messages sm
        JOIN content.symptoms s ON sm.symptom = s.symptom
        GROUP BY s.emoji, s.symptom
    )
    SELECT jsonb_object_agg(symptom_key, jsonb_build_object('messages', messages_array))
    INTO result
    FROM aggregated_symptoms;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO content.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_symptom_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."match_documents"("query_embedding" "public"."vector", "match_count" integer DEFAULT 5, "filter" "jsonb" DEFAULT '{}'::"jsonb") RETURNS TABLE("id" "uuid", "content" "text", "metadata" "jsonb", "similarity" double precision)
    LANGUAGE "plpgsql"
    AS $$
#variable_conflict use_variable
BEGIN
  RETURN QUERY
  SELECT
    documents.id,
    documents.content,
    documents.metadata,
    1 - (documents.embedding <=> query_embedding) AS similarity
  FROM documents
  WHERE documents.embedding IS NOT NULL
    AND (filter = '{}'::jsonb OR documents.metadata @> filter)
  ORDER BY documents.embedding <=> query_embedding
  LIMIT match_count;
END;
$$;


ALTER FUNCTION "public"."match_documents"("query_embedding" "public"."vector", "match_count" integer, "filter" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN
    INSERT INTO feedback (user_id, feedback, timestamp)
    VALUES (user_id, feedback, CURRENT_TIMESTAMP);
END;$$;


ALTER FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_fcm_token"("user_id" "text", "fcm_token" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
    -- Update the fcm token for the user with the provided user_id
    UPDATE public.users
    SET fcm_token = update_fcm_token.fcm_token -- Use the function parameter explicitly
    WHERE public.users.id = update_fcm_token.user_id;

    -- Raise a notice for confirmation (optional)
    RAISE NOTICE 'FCM token updated for user_id: %', user_id;
END;$$;


ALTER FUNCTION "public"."update_fcm_token"("user_id" "text", "fcm_token" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_group"("group_data" "json") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
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

END;$$;


ALTER FUNCTION "public"."update_group"("group_data" "json") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "content"."cache" (
    "type" "text" NOT NULL,
    "json" "json" NOT NULL,
    "last_updated" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "content"."cache" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."check_in_messages" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "content"."check_in_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."claire_prompts" (
    "symptom" "text" DEFAULT ''::"text",
    "title" "text" NOT NULL,
    "prompt" "text" NOT NULL,
    "school_message_id" bigint
);


ALTER TABLE "content"."claire_prompts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."get_back_messages" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "content"."get_back_messages" OWNER TO "postgres";


COMMENT ON TABLE "content"."get_back_messages" IS 'This is a duplicate of check_in_messages';



CREATE TABLE IF NOT EXISTS "content"."journal_prompts" (
    "prompt" "text" NOT NULL,
    "school_message_id" bigint
);


ALTER TABLE "content"."journal_prompts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."one_offs" (
    "key" "text" NOT NULL,
    "value" "text" NOT NULL
);


ALTER TABLE "content"."one_offs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."reddit_threads" (
    "symptom" "text",
    "title" "text" NOT NULL,
    "url" "text" NOT NULL,
    "school_message_id" bigint
);


ALTER TABLE "content"."reddit_threads" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."school_activities" (
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
    "sub_links" "json"
);


ALTER TABLE "content"."school_activities" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."school_messages" (
    "school_id" "text" NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "message" "text" NOT NULL,
    "meditation_name" "text",
    "meditation_link" "text",
    "id" bigint NOT NULL
);


ALTER TABLE "content"."school_messages" OWNER TO "postgres";


ALTER TABLE "content"."school_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "content"."school_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "content"."school_resources" (
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "link" "text" NOT NULL,
    "description" "text",
    "badge" "text",
    "phone_number" "text",
    "location" "text",
    "section_title" "text" NOT NULL,
    "school_id" "text" NOT NULL
);


ALTER TABLE "content"."school_resources" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."schools" (
    "school_id" "text" NOT NULL,
    "short_name" "text" NOT NULL,
    "long_name" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "reddit_flair" "text",
    "sf_symbol_icon" "text"
);


ALTER TABLE "content"."schools" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."slip_up_messages" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "content"."slip_up_messages" OWNER TO "postgres";


COMMENT ON TABLE "content"."slip_up_messages" IS 'This is a duplicate of check_in_messages';



CREATE TABLE IF NOT EXISTS "content"."symptom_messages" (
    "symptom" "text" NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "content"."symptom_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."symptom_tip_sections" (
    "id" bigint NOT NULL,
    "symptom_tip" bigint NOT NULL,
    "heading" "text" NOT NULL,
    "content" "text" NOT NULL,
    "example" "text"
);


ALTER TABLE "content"."symptom_tip_sections" OWNER TO "postgres";


ALTER TABLE "content"."symptom_tip_sections" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "content"."symptom_tip_sections_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "content"."symptom_tips" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "symptom" "text" NOT NULL
);


ALTER TABLE "content"."symptom_tips" OWNER TO "postgres";


ALTER TABLE "content"."symptom_tips" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "content"."symptom_tips_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "content"."symptoms" (
    "symptom" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "emoji" "text" NOT NULL
);


ALTER TABLE "content"."symptoms" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "content"."youtube_videos" (
    "symptom" "text" DEFAULT ''::"text",
    "title" "text" NOT NULL,
    "url" "text" NOT NULL,
    "school_message_id" bigint
);


ALTER TABLE "content"."youtube_videos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."documents" (
    "content" "text" NOT NULL,
    "embedding" "public"."vector" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "metadata" "jsonb" NOT NULL,
    "id" "uuid" NOT NULL
);


ALTER TABLE "public"."documents" OWNER TO "postgres";


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



CREATE TABLE IF NOT EXISTS "public"."feedback" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "feedback" "text" NOT NULL,
    "timestamp" timestamp with time zone NOT NULL
);


ALTER TABLE "public"."feedback" OWNER TO "postgres";


ALTER TABLE "public"."feedback" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."feedback_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."group_activity" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "activity" "public"."group_activity_type" DEFAULT 'joined'::"public"."group_activity_type" NOT NULL
);


ALTER TABLE "public"."group_activity" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."group_members" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL
);


ALTER TABLE "public"."group_members" OWNER TO "postgres";


COMMENT ON TABLE "public"."group_members" IS 'Group - user pairs';



CREATE TABLE IF NOT EXISTS "public"."group_notes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "to_member_id" "text" NOT NULL,
    "from_member_id" "text" NOT NULL,
    "message" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."group_notes" OWNER TO "postgres";


COMMENT ON TABLE "public"."group_notes" IS 'Clear30 group messages';



CREATE TABLE IF NOT EXISTS "public"."groups" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "hue" numeric NOT NULL
);


ALTER TABLE "public"."groups" OWNER TO "postgres";


COMMENT ON TABLE "public"."groups" IS 'Clear30 groups';



CREATE TABLE IF NOT EXISTS "public"."notifications" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."notifications" OWNER TO "postgres";


ALTER TABLE "public"."notifications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."notifications_id_seq"
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
    "days_sober" boolean[] NOT NULL,
    "initial_frequency" numeric NOT NULL,
    "show_in_group_rank" boolean NOT NULL,
    "fcm_token" "text"
);


ALTER TABLE "public"."users" OWNER TO "postgres";


COMMENT ON TABLE "public"."users" IS 'Clear30 users';



ALTER TABLE ONLY "content"."cache"
    ADD CONSTRAINT "cache_pkey" PRIMARY KEY ("type");



ALTER TABLE ONLY "content"."check_in_messages"
    ADD CONSTRAINT "check_in_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "content"."claire_prompts"
    ADD CONSTRAINT "claire_prompts_pkey" PRIMARY KEY ("prompt");



ALTER TABLE ONLY "content"."claire_prompts"
    ADD CONSTRAINT "claire_prompts_prompt_key" UNIQUE ("prompt");



ALTER TABLE ONLY "content"."get_back_messages"
    ADD CONSTRAINT "get_back_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "content"."journal_prompts"
    ADD CONSTRAINT "journal_prompts_pkey" PRIMARY KEY ("prompt");



ALTER TABLE ONLY "content"."one_offs"
    ADD CONSTRAINT "one_offs_pkey" PRIMARY KEY ("key");



ALTER TABLE ONLY "content"."reddit_threads"
    ADD CONSTRAINT "reddit_threads_pkey" PRIMARY KEY ("url");



ALTER TABLE ONLY "content"."reddit_threads"
    ADD CONSTRAINT "reddit_threads_url_key" UNIQUE ("url");



ALTER TABLE ONLY "content"."school_activities"
    ADD CONSTRAINT "school_activities_pkey" PRIMARY KEY ("school_id", "org_name", "title", "date_time");



ALTER TABLE ONLY "content"."school_messages"
    ADD CONSTRAINT "school_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "content"."school_resources"
    ADD CONSTRAINT "school_resources_pkey" PRIMARY KEY ("title", "section_title", "school_id");



ALTER TABLE ONLY "content"."schools"
    ADD CONSTRAINT "schools_pkey" PRIMARY KEY ("school_id");



ALTER TABLE ONLY "content"."slip_up_messages"
    ADD CONSTRAINT "slip_up_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "content"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_pkey" PRIMARY KEY ("symptom", "message");



ALTER TABLE ONLY "content"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "content"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "content"."symptoms"
    ADD CONSTRAINT "symptoms_pkey" PRIMARY KEY ("symptom");



ALTER TABLE ONLY "content"."youtube_videos"
    ADD CONSTRAINT "youtube_videos_pkey" PRIMARY KEY ("url");



ALTER TABLE ONLY "content"."youtube_videos"
    ADD CONSTRAINT "youtube_videos_url_key" UNIQUE ("url");



ALTER TABLE ONLY "public"."documents"
    ADD CONSTRAINT "documents_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."events"
    ADD CONSTRAINT "events_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."feedback"
    ADD CONSTRAINT "feedback_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."group_activity"
    ADD CONSTRAINT "group_activity_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."group_members"
    ADD CONSTRAINT "group_members_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."group_notes"
    ADD CONSTRAINT "group_notes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."groups"
    ADD CONSTRAINT "groups_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."group_activity"
    ADD CONSTRAINT "unique_group_activity" UNIQUE ("group_id", "user_id", "timestamp");



ALTER TABLE ONLY "public"."group_notes"
    ADD CONSTRAINT "unique_group_note" UNIQUE ("group_id", "to_member_id", "from_member_id");



ALTER TABLE ONLY "public"."group_members"
    ADD CONSTRAINT "unique_group_user" UNIQUE ("group_id", "user_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



CREATE OR REPLACE TRIGGER "send_notification" AFTER INSERT ON "public"."notifications" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/sendNotification', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '1000');



ALTER TABLE ONLY "content"."claire_prompts"
    ADD CONSTRAINT "claire_prompts_school_message_id_fkey" FOREIGN KEY ("school_message_id") REFERENCES "content"."school_messages"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."claire_prompts"
    ADD CONSTRAINT "claire_prompts_symptom_fkey" FOREIGN KEY ("symptom") REFERENCES "content"."symptoms"("symptom") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."journal_prompts"
    ADD CONSTRAINT "journal_prompts_school_message_id_fkey" FOREIGN KEY ("school_message_id") REFERENCES "content"."school_messages"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."reddit_threads"
    ADD CONSTRAINT "reddit_threads_school_message_id_fkey" FOREIGN KEY ("school_message_id") REFERENCES "content"."school_messages"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."reddit_threads"
    ADD CONSTRAINT "reddit_threads_symptom_fkey" FOREIGN KEY ("symptom") REFERENCES "content"."symptoms"("symptom") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."school_activities"
    ADD CONSTRAINT "school_activities_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "content"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."school_messages"
    ADD CONSTRAINT "school_messages_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "content"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."school_resources"
    ADD CONSTRAINT "school_resources_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "content"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_symptom_fkey" FOREIGN KEY ("symptom") REFERENCES "content"."symptoms"("symptom") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_symptom_tip_fkey" FOREIGN KEY ("symptom_tip") REFERENCES "content"."symptom_tips"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "content"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_symptom_fkey" FOREIGN KEY ("symptom") REFERENCES "content"."symptoms"("symptom") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."youtube_videos"
    ADD CONSTRAINT "youtube_videos_school_message_id_fkey" FOREIGN KEY ("school_message_id") REFERENCES "content"."school_messages"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "content"."youtube_videos"
    ADD CONSTRAINT "youtube_videos_symptom_fkey" FOREIGN KEY ("symptom") REFERENCES "content"."symptoms"("symptom") ON UPDATE CASCADE;



ALTER TABLE ONLY "public"."group_activity"
    ADD CONSTRAINT "group_activity_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "public"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_activity"
    ADD CONSTRAINT "group_activity_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_members"
    ADD CONSTRAINT "group_members_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "public"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_members"
    ADD CONSTRAINT "group_members_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_notes"
    ADD CONSTRAINT "group_notes_from_member_id_fkey" FOREIGN KEY ("from_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_notes"
    ADD CONSTRAINT "group_notes_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "public"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."group_notes"
    ADD CONSTRAINT "group_notes_to_member_id_fkey" FOREIGN KEY ("to_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



CREATE POLICY "Enable read access for all users" ON "content"."cache" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."check_in_messages" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."claire_prompts" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."get_back_messages" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."journal_prompts" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."one_offs" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."reddit_threads" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."school_activities" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."school_messages" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."school_resources" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."schools" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."slip_up_messages" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."symptom_messages" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."symptom_tip_sections" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."symptom_tips" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "content"."symptoms" FOR SELECT USING (true);



ALTER TABLE "content"."cache" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."check_in_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."claire_prompts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."get_back_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."journal_prompts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."one_offs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."reddit_threads" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."school_activities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."school_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."school_resources" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."schools" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."slip_up_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."symptom_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."symptom_tip_sections" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."symptom_tips" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "content"."symptoms" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable public access" ON "public"."group_activity" USING (false);



CREATE POLICY "Disable public access" ON "public"."group_members" USING (false);



CREATE POLICY "Disable public access" ON "public"."group_notes" USING (false);



CREATE POLICY "Disable public access" ON "public"."groups" USING (false);



CREATE POLICY "Disable public access" ON "public"."notifications" USING (false);



CREATE POLICY "Disable public access" ON "public"."users" USING (false);



CREATE POLICY "Disable read access for all users" ON "public"."events" FOR SELECT USING (false);



CREATE POLICY "Disable read access for all users" ON "public"."feedback" FOR SELECT USING (false);



CREATE POLICY "Enable insert access for all users" ON "public"."events" FOR INSERT WITH CHECK (true);



CREATE POLICY "Enable write access for all users" ON "public"."feedback" FOR INSERT WITH CHECK (true);



ALTER TABLE "public"."events" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."feedback" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."group_activity" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."group_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."group_notes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."groups" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "content" TO "anon";
GRANT USAGE ON SCHEMA "content" TO "authenticated";






GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_in"("cstring", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_in"("cstring", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_in"("cstring", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_in"("cstring", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_out"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_out"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_out"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_out"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_recv"("internal", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_recv"("internal", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_recv"("internal", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_recv"("internal", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_send"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_send"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_send"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_send"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_typmod_in"("cstring"[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_typmod_in"("cstring"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_typmod_in"("cstring"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_typmod_in"("cstring"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_in"("cstring", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_in"("cstring", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_in"("cstring", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_in"("cstring", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_out"("public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_out"("public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_out"("public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_out"("public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_recv"("internal", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_recv"("internal", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_recv"("internal", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_recv"("internal", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_send"("public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_send"("public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_send"("public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_send"("public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_typmod_in"("cstring"[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_typmod_in"("cstring"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_typmod_in"("cstring"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_typmod_in"("cstring"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_in"("cstring", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_in"("cstring", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_in"("cstring", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_in"("cstring", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_out"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_out"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_out"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_out"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_recv"("internal", "oid", integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_recv"("internal", "oid", integer) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_recv"("internal", "oid", integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_recv"("internal", "oid", integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_send"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_send"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_send"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_send"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_typmod_in"("cstring"[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_typmod_in"("cstring"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_typmod_in"("cstring"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_typmod_in"("cstring"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_halfvec"(real[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(real[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(real[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(real[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_vector"(real[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_vector"(real[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_vector"(real[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_vector"(real[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_halfvec"(double precision[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(double precision[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(double precision[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(double precision[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_vector"(double precision[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_vector"(double precision[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_vector"(double precision[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_vector"(double precision[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_halfvec"(integer[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(integer[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(integer[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(integer[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_vector"(integer[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_vector"(integer[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_vector"(integer[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_vector"(integer[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_halfvec"(numeric[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(numeric[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(numeric[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_halfvec"(numeric[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."array_to_vector"(numeric[], integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."array_to_vector"(numeric[], integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."array_to_vector"(numeric[], integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."array_to_vector"(numeric[], integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_to_float4"("public"."halfvec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_to_float4"("public"."halfvec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_to_float4"("public"."halfvec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_to_float4"("public"."halfvec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec"("public"."halfvec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec"("public"."halfvec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec"("public"."halfvec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec"("public"."halfvec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_to_sparsevec"("public"."halfvec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_to_sparsevec"("public"."halfvec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_to_sparsevec"("public"."halfvec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_to_sparsevec"("public"."halfvec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_to_vector"("public"."halfvec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_to_vector"("public"."halfvec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_to_vector"("public"."halfvec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_to_vector"("public"."halfvec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_to_halfvec"("public"."sparsevec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_to_halfvec"("public"."sparsevec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_to_halfvec"("public"."sparsevec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_to_halfvec"("public"."sparsevec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec"("public"."sparsevec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec"("public"."sparsevec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec"("public"."sparsevec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec"("public"."sparsevec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_to_vector"("public"."sparsevec", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_to_vector"("public"."sparsevec", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_to_vector"("public"."sparsevec", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_to_vector"("public"."sparsevec", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_to_float4"("public"."vector", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_to_float4"("public"."vector", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_to_float4"("public"."vector", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_to_float4"("public"."vector", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_to_halfvec"("public"."vector", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_to_halfvec"("public"."vector", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_to_halfvec"("public"."vector", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_to_halfvec"("public"."vector", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_to_sparsevec"("public"."vector", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_to_sparsevec"("public"."vector", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_to_sparsevec"("public"."vector", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_to_sparsevec"("public"."vector", integer, boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector"("public"."vector", integer, boolean) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector"("public"."vector", integer, boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."vector"("public"."vector", integer, boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector"("public"."vector", integer, boolean) TO "service_role";






































































































































































































GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."binary_quantize"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cosine_distance"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "service_role";



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



GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid") TO "service_role";



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



GRANT ALL ON FUNCTION "public"."halfvec_accum"(double precision[], "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_accum"(double precision[], "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_accum"(double precision[], "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_accum"(double precision[], "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_add"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_add"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_add"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_add"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_avg"(double precision[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_avg"(double precision[]) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_avg"(double precision[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_avg"(double precision[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_cmp"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_cmp"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_cmp"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_cmp"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_combine"(double precision[], double precision[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_combine"(double precision[], double precision[]) TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_combine"(double precision[], double precision[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_combine"(double precision[], double precision[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_concat"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_concat"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_concat"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_concat"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_eq"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_eq"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_eq"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_eq"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_ge"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_ge"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_ge"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_ge"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_gt"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_gt"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_gt"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_gt"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_l2_squared_distance"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_l2_squared_distance"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_l2_squared_distance"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_l2_squared_distance"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_le"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_le"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_le"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_le"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_lt"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_lt"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_lt"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_lt"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_mul"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_mul"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_mul"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_mul"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_ne"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_ne"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_ne"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_ne"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_negative_inner_product"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_negative_inner_product"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_negative_inner_product"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_negative_inner_product"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_spherical_distance"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_spherical_distance"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_spherical_distance"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_spherical_distance"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."halfvec_sub"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."halfvec_sub"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."halfvec_sub"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."halfvec_sub"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."hamming_distance"(bit, bit) TO "postgres";
GRANT ALL ON FUNCTION "public"."hamming_distance"(bit, bit) TO "anon";
GRANT ALL ON FUNCTION "public"."hamming_distance"(bit, bit) TO "authenticated";
GRANT ALL ON FUNCTION "public"."hamming_distance"(bit, bit) TO "service_role";



GRANT ALL ON FUNCTION "public"."hnsw_bit_support"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."hnsw_bit_support"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."hnsw_bit_support"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."hnsw_bit_support"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."hnsw_halfvec_support"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."hnsw_halfvec_support"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."hnsw_halfvec_support"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."hnsw_halfvec_support"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."hnsw_sparsevec_support"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."hnsw_sparsevec_support"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."hnsw_sparsevec_support"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."hnsw_sparsevec_support"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."hnswhandler"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."hnswhandler"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."hnswhandler"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."hnswhandler"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."inner_product"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."inner_product"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."inner_product"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."inner_product"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."ivfflat_bit_support"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."ivfflat_bit_support"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."ivfflat_bit_support"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."ivfflat_bit_support"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."ivfflat_halfvec_support"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."ivfflat_halfvec_support"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."ivfflat_halfvec_support"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."ivfflat_halfvec_support"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."ivfflathandler"("internal") TO "postgres";
GRANT ALL ON FUNCTION "public"."ivfflathandler"("internal") TO "anon";
GRANT ALL ON FUNCTION "public"."ivfflathandler"("internal") TO "authenticated";
GRANT ALL ON FUNCTION "public"."ivfflathandler"("internal") TO "service_role";



GRANT ALL ON FUNCTION "public"."jaccard_distance"(bit, bit) TO "postgres";
GRANT ALL ON FUNCTION "public"."jaccard_distance"(bit, bit) TO "anon";
GRANT ALL ON FUNCTION "public"."jaccard_distance"(bit, bit) TO "authenticated";
GRANT ALL ON FUNCTION "public"."jaccard_distance"(bit, bit) TO "service_role";



GRANT ALL ON FUNCTION "public"."l1_distance"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l1_distance"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l1_distance"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l1_distance"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_distance"("public"."halfvec", "public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."halfvec", "public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."halfvec", "public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."halfvec", "public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_distance"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_distance"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_distance"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_norm"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_norm"("public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_norm"("public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."l2_normalize"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."match_documents"("query_embedding" "public"."vector", "match_count" integer, "filter" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."match_documents"("query_embedding" "public"."vector", "match_count" integer, "filter" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."match_documents"("query_embedding" "public"."vector", "match_count" integer, "filter" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_cmp"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_cmp"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_cmp"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_cmp"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_eq"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_eq"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_eq"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_eq"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_ge"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_ge"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_ge"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_ge"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_gt"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_gt"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_gt"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_gt"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_l2_squared_distance"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_l2_squared_distance"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_l2_squared_distance"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_l2_squared_distance"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_le"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_le"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_le"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_le"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_lt"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_lt"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_lt"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_lt"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_ne"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_ne"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_ne"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_ne"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sparsevec_negative_inner_product"("public"."sparsevec", "public"."sparsevec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sparsevec_negative_inner_product"("public"."sparsevec", "public"."sparsevec") TO "anon";
GRANT ALL ON FUNCTION "public"."sparsevec_negative_inner_product"("public"."sparsevec", "public"."sparsevec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sparsevec_negative_inner_product"("public"."sparsevec", "public"."sparsevec") TO "service_role";



GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."subvector"("public"."halfvec", integer, integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."subvector"("public"."halfvec", integer, integer) TO "anon";
GRANT ALL ON FUNCTION "public"."subvector"("public"."halfvec", integer, integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."subvector"("public"."halfvec", integer, integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."subvector"("public"."vector", integer, integer) TO "postgres";
GRANT ALL ON FUNCTION "public"."subvector"("public"."vector", integer, integer) TO "anon";
GRANT ALL ON FUNCTION "public"."subvector"("public"."vector", integer, integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."subvector"("public"."vector", integer, integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."update_fcm_token"("user_id" "text", "fcm_token" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."update_fcm_token"("user_id" "text", "fcm_token" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_fcm_token"("user_id" "text", "fcm_token" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_group"("group_data" "json") TO "anon";
GRANT ALL ON FUNCTION "public"."update_group"("group_data" "json") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_group"("group_data" "json") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_accum"(double precision[], "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_accum"(double precision[], "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_accum"(double precision[], "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_accum"(double precision[], "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_add"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_add"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_add"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_add"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_avg"(double precision[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_avg"(double precision[]) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_avg"(double precision[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_avg"(double precision[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_cmp"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_cmp"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_cmp"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_cmp"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_combine"(double precision[], double precision[]) TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_combine"(double precision[], double precision[]) TO "anon";
GRANT ALL ON FUNCTION "public"."vector_combine"(double precision[], double precision[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_combine"(double precision[], double precision[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_concat"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_concat"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_concat"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_concat"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_dims"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_dims"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_dims"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_eq"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_eq"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_eq"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_eq"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_ge"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_ge"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_ge"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_ge"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_gt"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_gt"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_gt"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_gt"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_l2_squared_distance"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_l2_squared_distance"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_l2_squared_distance"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_l2_squared_distance"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_le"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_le"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_le"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_le"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_lt"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_lt"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_lt"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_lt"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_mul"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_mul"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_mul"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_mul"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_ne"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_ne"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_ne"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_ne"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_negative_inner_product"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_negative_inner_product"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_negative_inner_product"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_negative_inner_product"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_norm"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_norm"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_norm"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_norm"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_spherical_distance"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_spherical_distance"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_spherical_distance"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_spherical_distance"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."vector_sub"("public"."vector", "public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."vector_sub"("public"."vector", "public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."vector_sub"("public"."vector", "public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vector_sub"("public"."vector", "public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."avg"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."avg"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."avg"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."avg"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."avg"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."avg"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."avg"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."avg"("public"."vector") TO "service_role";



GRANT ALL ON FUNCTION "public"."sum"("public"."halfvec") TO "postgres";
GRANT ALL ON FUNCTION "public"."sum"("public"."halfvec") TO "anon";
GRANT ALL ON FUNCTION "public"."sum"("public"."halfvec") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sum"("public"."halfvec") TO "service_role";



GRANT ALL ON FUNCTION "public"."sum"("public"."vector") TO "postgres";
GRANT ALL ON FUNCTION "public"."sum"("public"."vector") TO "anon";
GRANT ALL ON FUNCTION "public"."sum"("public"."vector") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sum"("public"."vector") TO "service_role";



GRANT SELECT ON TABLE "content"."cache" TO "anon";
GRANT SELECT ON TABLE "content"."cache" TO PUBLIC;



GRANT SELECT ON TABLE "content"."check_in_messages" TO "anon";
GRANT SELECT ON TABLE "content"."check_in_messages" TO "authenticated";
GRANT SELECT ON TABLE "content"."check_in_messages" TO PUBLIC;



GRANT SELECT ON TABLE "content"."claire_prompts" TO "anon";
GRANT SELECT ON TABLE "content"."claire_prompts" TO "authenticated";
GRANT SELECT ON TABLE "content"."claire_prompts" TO PUBLIC;



GRANT SELECT ON TABLE "content"."get_back_messages" TO "anon";
GRANT SELECT ON TABLE "content"."get_back_messages" TO "authenticated";
GRANT SELECT ON TABLE "content"."get_back_messages" TO PUBLIC;



GRANT SELECT ON TABLE "content"."journal_prompts" TO PUBLIC;



GRANT SELECT ON TABLE "content"."one_offs" TO PUBLIC;



GRANT SELECT ON TABLE "content"."reddit_threads" TO "anon";
GRANT SELECT ON TABLE "content"."reddit_threads" TO "authenticated";
GRANT SELECT ON TABLE "content"."reddit_threads" TO PUBLIC;



GRANT SELECT ON TABLE "content"."school_activities" TO PUBLIC;



GRANT SELECT ON TABLE "content"."school_messages" TO PUBLIC;



GRANT SELECT ON TABLE "content"."school_resources" TO PUBLIC;



GRANT SELECT ON TABLE "content"."schools" TO PUBLIC;



GRANT SELECT ON TABLE "content"."slip_up_messages" TO "anon";
GRANT SELECT ON TABLE "content"."slip_up_messages" TO "authenticated";
GRANT SELECT ON TABLE "content"."slip_up_messages" TO PUBLIC;



GRANT SELECT ON TABLE "content"."symptom_messages" TO "anon";
GRANT SELECT ON TABLE "content"."symptom_messages" TO "authenticated";
GRANT SELECT ON TABLE "content"."symptom_messages" TO PUBLIC;



GRANT SELECT ON TABLE "content"."symptom_tip_sections" TO "anon";
GRANT SELECT ON TABLE "content"."symptom_tip_sections" TO "authenticated";
GRANT SELECT ON TABLE "content"."symptom_tip_sections" TO PUBLIC;



GRANT SELECT ON TABLE "content"."symptom_tips" TO "anon";
GRANT SELECT ON TABLE "content"."symptom_tips" TO "authenticated";
GRANT SELECT ON TABLE "content"."symptom_tips" TO PUBLIC;



GRANT SELECT ON TABLE "content"."symptoms" TO "anon";
GRANT SELECT ON TABLE "content"."symptoms" TO "authenticated";
GRANT SELECT ON TABLE "content"."symptoms" TO PUBLIC;



GRANT SELECT ON TABLE "content"."youtube_videos" TO PUBLIC;





















GRANT ALL ON TABLE "public"."documents" TO "anon";
GRANT ALL ON TABLE "public"."documents" TO "authenticated";
GRANT ALL ON TABLE "public"."documents" TO "service_role";



GRANT ALL ON TABLE "public"."events" TO "anon";
GRANT ALL ON TABLE "public"."events" TO "authenticated";
GRANT ALL ON TABLE "public"."events" TO "service_role";



GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."feedback" TO "anon";
GRANT ALL ON TABLE "public"."feedback" TO "authenticated";
GRANT ALL ON TABLE "public"."feedback" TO "service_role";



GRANT ALL ON SEQUENCE "public"."feedback_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."feedback_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."feedback_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."group_activity" TO "anon";
GRANT ALL ON TABLE "public"."group_activity" TO "authenticated";
GRANT ALL ON TABLE "public"."group_activity" TO "service_role";



GRANT ALL ON TABLE "public"."group_members" TO "anon";
GRANT ALL ON TABLE "public"."group_members" TO "authenticated";
GRANT ALL ON TABLE "public"."group_members" TO "service_role";



GRANT ALL ON TABLE "public"."group_notes" TO "anon";
GRANT ALL ON TABLE "public"."group_notes" TO "authenticated";
GRANT ALL ON TABLE "public"."group_notes" TO "service_role";



GRANT ALL ON TABLE "public"."groups" TO "anon";
GRANT ALL ON TABLE "public"."groups" TO "authenticated";
GRANT ALL ON TABLE "public"."groups" TO "service_role";



GRANT ALL ON TABLE "public"."notifications" TO "anon";
GRANT ALL ON TABLE "public"."notifications" TO "authenticated";
GRANT ALL ON TABLE "public"."notifications" TO "service_role";



GRANT ALL ON SEQUENCE "public"."notifications_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."notifications_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."notifications_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



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
