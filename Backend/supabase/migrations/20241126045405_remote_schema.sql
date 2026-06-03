drop function if exists "library"."get_symptom_infos"();


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_symptom_infos()
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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
        SELECT s.emoji || ' ' || s.symptom AS symptom_key, s.symptom, s.color1, s.color2
        FROM symptoms.symptoms s
        LEFT JOIN symptoms.symptom_tips st ON s.symptom = st.symptom
        GROUP BY s.emoji, s.symptom, st.color1, st.color2
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

        -- Initialize the reddit threads for the current symptom
        reddits := '{';
        first_reddit := TRUE;

        -- Get all Reddit threads for the current symptom using the join table
        FOR reddit_record IN
            SELECT r.title, r.url
            FROM symptoms.symptom_reddit_threads srt
            JOIN library.resources r ON r.id = srt.resource
            WHERE srt.symptom = record.symptom
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

        -- Get all Claire prompts for the current symptom using the join table
        FOR prompt_record IN
            SELECT cp.title, cp.prompt
            FROM symptoms.symptom_claire_prompts scp
            JOIN library.claire_prompts cp ON cp.id = scp.prompt
            WHERE scp.symptom = record.symptom
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
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result::JSON, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSON
    RETURN result::JSON;
END;$function$
;

CREATE OR REPLACE FUNCTION public.get_symptom_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$DECLARE
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
END;$function$
;


alter table "schools"."school_message_journal_prompts" alter column "prompt" set not null;

alter table "schools"."school_message_resources" alter column "resource" set not null;


alter table "symptoms"."symptom_claire_prompts" drop constraint "symptom_claire_prompts_claire_prompt_fkey";

alter table "symptoms"."symptom_claire_prompts" drop column "claire_prompt";

alter table "symptoms"."symptom_claire_prompts" add column "prompt" bigint not null;

alter table "symptoms"."symptom_claire_prompts" add constraint "symptom_claire_prompts_prompt_fkey" FOREIGN KEY (prompt) REFERENCES library.claire_prompts(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "symptoms"."symptom_claire_prompts" validate constraint "symptom_claire_prompts_prompt_fkey";


