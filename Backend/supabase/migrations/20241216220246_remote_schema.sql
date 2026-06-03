drop view if exists "views"."messages_life_program";

drop policy "Disable access for all" on "programs"."program_message_claire_prompts";

drop policy "Disable access for all" on "programs"."program_message_journal_prompts";

drop policy "Disable access for all" on "programs"."program_message_meditations";

drop policy "Disable access for all" on "programs"."program_message_page_info";

drop policy "Disable access for all" on "programs"."program_message_resources";

alter table "programs"."program_feedback_resources" drop constraint "program_feedback_resources_feedback_id_fkey";

alter table "programs"."program_feedback_resources" drop constraint "program_feedback_resources_resource_id_fkey";

alter table "programs"."program_message_claire_prompts" drop constraint "program_message_claire_prompts_message_id_fkey";

alter table "programs"."program_message_claire_prompts" drop constraint "program_message_claire_prompts_prompt_id_fkey";

alter table "programs"."program_message_journal_prompts" drop constraint "program_message_journal_prompts_message_id_fkey";

alter table "programs"."program_message_journal_prompts" drop constraint "program_message_journal_prompts_prompt_id_fkey";

alter table "programs"."program_message_meditations" drop constraint "program_message_meditations_meditation_id_fkey";

alter table "programs"."program_message_meditations" drop constraint "program_message_meditations_message_id_fkey";

alter table "programs"."program_message_page_info" drop constraint "program_message_page_info_message_id_fkey";

alter table "programs"."program_message_resources" drop constraint "program_message_resources_message_id_fkey";

alter table "programs"."program_message_resources" drop constraint "program_message_resources_resource_id_fkey";

alter table "programs"."program_feedback_resources" drop constraint "program_feedback_resources_pkey";

alter table "programs"."program_message_claire_prompts" drop constraint "program_message_claire_prompts_pkey";

alter table "programs"."program_message_journal_prompts" drop constraint "program_message_journal_prompts_pkey";

alter table "programs"."program_message_meditations" drop constraint "program_message_meditations_pkey";

alter table "programs"."program_message_page_info" drop constraint "program_message_page_info_pkey";

alter table "programs"."program_message_resources" drop constraint "program_message_resources_pkey";

drop index if exists "programs"."program_feedback_resources_pkey";

drop index if exists "programs"."program_message_claire_prompts_pkey";

drop index if exists "programs"."program_message_journal_prompts_pkey";

drop index if exists "programs"."program_message_meditations_pkey";

drop index if exists "programs"."program_message_page_info_pkey";

drop index if exists "programs"."program_message_resources_pkey";

drop table "programs"."program_feedback_resources";

drop table "programs"."program_message_claire_prompts";

drop table "programs"."program_message_journal_prompts";

drop table "programs"."program_message_meditations";

drop table "programs"."program_message_page_info";

drop table "programs"."program_message_resources";


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
END;$function$
;


drop policy "Disable access for all" on "schools"."school_message_claire_prompts";

drop policy "Disable access for all" on "schools"."school_message_journal_prompts";

drop policy "Disable access for all" on "schools"."school_message_meditations";

drop policy "Disable access for all" on "schools"."school_message_resources";

revoke delete on table "schools"."school_message_claire_prompts" from "anon";

revoke insert on table "schools"."school_message_claire_prompts" from "anon";

revoke references on table "schools"."school_message_claire_prompts" from "anon";

revoke select on table "schools"."school_message_claire_prompts" from "anon";

revoke trigger on table "schools"."school_message_claire_prompts" from "anon";

revoke truncate on table "schools"."school_message_claire_prompts" from "anon";

revoke update on table "schools"."school_message_claire_prompts" from "anon";

revoke delete on table "schools"."school_message_claire_prompts" from "authenticated";

revoke insert on table "schools"."school_message_claire_prompts" from "authenticated";

revoke references on table "schools"."school_message_claire_prompts" from "authenticated";

revoke select on table "schools"."school_message_claire_prompts" from "authenticated";

revoke trigger on table "schools"."school_message_claire_prompts" from "authenticated";

revoke truncate on table "schools"."school_message_claire_prompts" from "authenticated";

revoke update on table "schools"."school_message_claire_prompts" from "authenticated";

revoke delete on table "schools"."school_message_journal_prompts" from "anon";

revoke insert on table "schools"."school_message_journal_prompts" from "anon";

revoke references on table "schools"."school_message_journal_prompts" from "anon";

revoke select on table "schools"."school_message_journal_prompts" from "anon";

revoke trigger on table "schools"."school_message_journal_prompts" from "anon";

revoke truncate on table "schools"."school_message_journal_prompts" from "anon";

revoke update on table "schools"."school_message_journal_prompts" from "anon";

revoke delete on table "schools"."school_message_journal_prompts" from "authenticated";

revoke insert on table "schools"."school_message_journal_prompts" from "authenticated";

revoke references on table "schools"."school_message_journal_prompts" from "authenticated";

revoke select on table "schools"."school_message_journal_prompts" from "authenticated";

revoke trigger on table "schools"."school_message_journal_prompts" from "authenticated";

revoke truncate on table "schools"."school_message_journal_prompts" from "authenticated";

revoke update on table "schools"."school_message_journal_prompts" from "authenticated";

revoke delete on table "schools"."school_message_meditations" from "anon";

revoke insert on table "schools"."school_message_meditations" from "anon";

revoke references on table "schools"."school_message_meditations" from "anon";

revoke select on table "schools"."school_message_meditations" from "anon";

revoke trigger on table "schools"."school_message_meditations" from "anon";

revoke truncate on table "schools"."school_message_meditations" from "anon";

revoke update on table "schools"."school_message_meditations" from "anon";

revoke delete on table "schools"."school_message_meditations" from "authenticated";

revoke insert on table "schools"."school_message_meditations" from "authenticated";

revoke references on table "schools"."school_message_meditations" from "authenticated";

revoke select on table "schools"."school_message_meditations" from "authenticated";

revoke trigger on table "schools"."school_message_meditations" from "authenticated";

revoke truncate on table "schools"."school_message_meditations" from "authenticated";

revoke update on table "schools"."school_message_meditations" from "authenticated";

revoke delete on table "schools"."school_message_resources" from "anon";

revoke insert on table "schools"."school_message_resources" from "anon";

revoke references on table "schools"."school_message_resources" from "anon";

revoke select on table "schools"."school_message_resources" from "anon";

revoke trigger on table "schools"."school_message_resources" from "anon";

revoke truncate on table "schools"."school_message_resources" from "anon";

revoke update on table "schools"."school_message_resources" from "anon";

revoke delete on table "schools"."school_message_resources" from "authenticated";

revoke insert on table "schools"."school_message_resources" from "authenticated";

revoke references on table "schools"."school_message_resources" from "authenticated";

revoke select on table "schools"."school_message_resources" from "authenticated";

revoke trigger on table "schools"."school_message_resources" from "authenticated";

revoke truncate on table "schools"."school_message_resources" from "authenticated";

revoke update on table "schools"."school_message_resources" from "authenticated";

alter table "schools"."school_message_claire_prompts" drop constraint "school_message_claire_prompts_prompt_fkey";

alter table "schools"."school_message_claire_prompts" drop constraint "school_message_claire_prompts_school_message_fkey";

alter table "schools"."school_message_journal_prompts" drop constraint "school_message_journal_prompts_prompt_fkey";

alter table "schools"."school_message_journal_prompts" drop constraint "school_message_journal_prompts_school_message_fkey";

alter table "schools"."school_message_meditations" drop constraint "school_message_meditations_meditation_fkey";

alter table "schools"."school_message_meditations" drop constraint "school_message_meditations_school_message_fkey";

alter table "schools"."school_message_resources" drop constraint "school_message_resources_resource_fkey";

alter table "schools"."school_message_resources" drop constraint "school_message_resources_school_message_fkey";

alter table "schools"."school_message_claire_prompts" drop constraint "school_message_claire_prompts_pkey";

alter table "schools"."school_message_journal_prompts" drop constraint "school_message_journal_prompts_pkey";

alter table "schools"."school_message_meditations" drop constraint "school_message_meditations_pkey";

alter table "schools"."school_message_resources" drop constraint "school_message_resources_pkey";

drop index if exists "schools"."school_message_claire_prompts_pkey";

drop index if exists "schools"."school_message_journal_prompts_pkey";

drop index if exists "schools"."school_message_meditations_pkey";

drop index if exists "schools"."school_message_resources_pkey";

drop table "schools"."school_message_claire_prompts";

drop table "schools"."school_message_journal_prompts";

drop table "schools"."school_message_meditations";

drop table "schools"."school_message_resources";


drop policy "Disable access for all" on "symptoms"."symptom_claire_prompts";

drop policy "Disable access for all" on "symptoms"."symptom_reddit_threads";

revoke delete on table "symptoms"."symptom_claire_prompts" from "anon";

revoke insert on table "symptoms"."symptom_claire_prompts" from "anon";

revoke references on table "symptoms"."symptom_claire_prompts" from "anon";

revoke select on table "symptoms"."symptom_claire_prompts" from "anon";

revoke trigger on table "symptoms"."symptom_claire_prompts" from "anon";

revoke truncate on table "symptoms"."symptom_claire_prompts" from "anon";

revoke update on table "symptoms"."symptom_claire_prompts" from "anon";

revoke delete on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke insert on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke references on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke select on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke trigger on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke truncate on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke update on table "symptoms"."symptom_claire_prompts" from "authenticated";

revoke delete on table "symptoms"."symptom_reddit_threads" from "anon";

revoke insert on table "symptoms"."symptom_reddit_threads" from "anon";

revoke references on table "symptoms"."symptom_reddit_threads" from "anon";

revoke select on table "symptoms"."symptom_reddit_threads" from "anon";

revoke trigger on table "symptoms"."symptom_reddit_threads" from "anon";

revoke truncate on table "symptoms"."symptom_reddit_threads" from "anon";

revoke update on table "symptoms"."symptom_reddit_threads" from "anon";

revoke delete on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke insert on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke references on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke select on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke trigger on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke truncate on table "symptoms"."symptom_reddit_threads" from "authenticated";

revoke update on table "symptoms"."symptom_reddit_threads" from "authenticated";

alter table "symptoms"."symptom_claire_prompts" drop constraint "symptom_claire_prompts_prompt_fkey";

alter table "symptoms"."symptom_claire_prompts" drop constraint "symptom_claire_prompts_symptom_fkey";

alter table "symptoms"."symptom_reddit_threads" drop constraint "symptom_reddit_threads_resource_fkey";

alter table "symptoms"."symptom_reddit_threads" drop constraint "symptom_reddit_threads_symptom_fkey";

alter table "symptoms"."symptom_claire_prompts" drop constraint "symptom_claire_prompts_pkey";

alter table "symptoms"."symptom_reddit_threads" drop constraint "symptom_reddit_threads_pkey";

drop index if exists "symptoms"."symptom_claire_prompts_pkey";

drop index if exists "symptoms"."symptom_reddit_threads_pkey";

drop table "symptoms"."symptom_claire_prompts";

drop table "symptoms"."symptom_reddit_threads";

alter table "symptoms"."symptoms" add column "claire_prompts" jsonb;

alter table "symptoms"."symptoms" add column "resources" jsonb;


