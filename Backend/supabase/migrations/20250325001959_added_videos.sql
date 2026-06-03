alter table "programs"."program_messages" add column "thumbnail_url" text;

alter table "programs"."program_messages" add column "video_url" text;


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.program_get_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
    _start_soon_program_id text;
    start_soon_messages JSONB;
    start_soon_stage_ids TEXT[];
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

    -- Check if there's a start_soon program associated with this program
    SELECT start_soon INTO _start_soon_program_id
    FROM programs.programs
    WHERE id = recent_assessment.program;

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
            pm.thumbnail_url,
            pm.video_url,
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
                'notification_body', notification_body,
                'thumbnail_url', thumbnail_url,
                'video_url', video_url
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

    -- If there's a start_soon program, get its non-assessment messages
    IF _start_soon_program_id IS NOT NULL THEN
        WITH start_soon_base_data AS (
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
                pm.thumbnail_url,
                pm.video_url,
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
            WHERE pm.program = _start_soon_program_id
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
        start_soon_message_objects AS (
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
                    'notification_body', notification_body,
                    'thumbnail_url', thumbnail_url,
                    'video_url', video_url
                ) as message_object
            FROM start_soon_base_data
        ),
        start_soon_day_groups AS (
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
            FROM start_soon_message_objects
        )
        SELECT 
            (
                SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
                FROM start_soon_day_groups
                WHERE NOT is_assessment  -- Include all core messages
                   OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
            ),
            array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
        INTO start_soon_messages, start_soon_stage_ids
        FROM start_soon_base_data;
        
        -- Combine stage IDs from both programs
        IF start_soon_stage_ids IS NOT NULL THEN
            stage_ids := array_cat(stage_ids, start_soon_stage_ids);
        END IF;
    END IF;

    -- Step 3: Fetch stage information for all referenced stages (from both programs)
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

    -- Step 4: Return final combined structure with start_soon_messages if available
    IF _start_soon_program_id IS NOT NULL AND start_soon_messages IS NOT NULL THEN
        RETURN jsonb_build_object(
            'stages', COALESCE(stages, '[]'::jsonb),
            'messages', COALESCE(messages, '[]'::jsonb),
            'start_soon_messages', COALESCE(start_soon_messages, '[]'::jsonb)
        );
    ELSE
        RETURN jsonb_build_object(
            'stages', COALESCE(stages, '[]'::jsonb),
            'messages', COALESCE(messages, '[]'::jsonb)
        );
    END IF;
END;$function$
;


