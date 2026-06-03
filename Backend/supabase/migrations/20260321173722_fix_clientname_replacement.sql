-- Fix _CLIENTNAME_ replacement in all message-fetching functions
-- Previously only body was replaced; title, subtitle, notification_title, notification_body were missed

-- 1. Fix fetch_messages_by_ids (had NO replacement at all)
CREATE OR REPLACE FUNCTION programs.fetch_messages_by_ids(message_ids bigint[])
 RETURNS TABLE(id bigint, day bigint, stage text, title text, subtitle text, body text, page_info jsonb, meditation jsonb, resources jsonb, claire_prompts jsonb, claire_prompts_json jsonb, journal_prompts jsonb, notification_title text, notification_body text, thumbnail_url text, video_url text, instagram_videos jsonb, carousel_images jsonb, question_id text, question_response text, stage_title text, stage_subtitle text, stage_body text, stage_color1 text, stage_color2 text, stage_fred_experience text)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    _auth_id uuid;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    RETURN QUERY
    SELECT
        m.id,
        m.day,
        m.stage,
        REPLACE(m.title, '_CLIENTNAME_', COALESCE(_user_name, '')) as title,
        REPLACE(m.subtitle, '_CLIENTNAME_', COALESCE(_user_name, '')) as subtitle,
        REPLACE(m.body, '_CLIENTNAME_', COALESCE(_user_name, '')) as body,
        m.page_info,
        m.meditation,
        m.resources,
        m.claire_prompts,
        m.claire_prompts_json,
        m.journal_prompts,
        REPLACE(m.notification_title, '_CLIENTNAME_', COALESCE(_user_name, '')) as notification_title,
        REPLACE(m.notification_body, '_CLIENTNAME_', COALESCE(_user_name, '')) as notification_body,
        m.thumbnail_url,
        m.video_url,
        m.instagram_videos,
        m.carousel_images,
        m.question_id,
        m.question_response,
        -- Stage fields
        REPLACE(s.title, '_CLIENTNAME_', COALESCE(_user_name, '')) as stage_title,
        REPLACE(s.subtitle, '_CLIENTNAME_', COALESCE(_user_name, '')) as stage_subtitle,
        REPLACE(s.body, '_CLIENTNAME_', COALESCE(_user_name, '')) as stage_body,
        s.color1 as stage_color1,
        s.color2 as stage_color2,
        s.fred_experience as stage_fred_experience
    FROM programs.program_messages m
    LEFT JOIN programs.program_stages s ON m.stage = s.stage
    WHERE m.id = ANY(message_ids);
END;
$function$;

-- 2. Fix program_get_messages (body was replaced, but not title/subtitle/notification fields)
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
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_title,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_body, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_body,
            pm.thumbnail_url,
            pm.video_url,
            pm.instagram_videos,
            pm.carousel_images,
            pm.member_perks,
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
            -- Legacy claire_prompts: transform dict to array for old apps
            CASE
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            -- New claire_prompts_json: return directly from column (null until populated)
            pm.claire_prompts_json,
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
                'claire_prompts_json', claire_prompts_json,
                'journal_prompts', journal_prompts,
                'page_info', page_info,
                'notification_title', notification_title,
                'notification_body', notification_body,
                'thumbnail_url', thumbnail_url,
                'video_url', video_url,
                'instagram_videos', instagram_videos,
                'carousel_images', carousel_images,
                'member_perks', member_perks
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
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
            WHERE NOT is_assessment
               OR (is_assessment AND msg_position = (day_counter % group_size))
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
                REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as title,
                REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as subtitle,
                TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
                pm.stage,
                REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_title,
                REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_body, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_body,
                pm.thumbnail_url,
                pm.video_url,
                pm.instagram_videos,
                pm.carousel_images,
                pm.member_perks,
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
                pm.claire_prompts_json,
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
                    'claire_prompts_json', claire_prompts_json,
                    'journal_prompts', journal_prompts,
                    'page_info', page_info,
                    'notification_title', notification_title,
                    'notification_body', notification_body,
                    'thumbnail_url', thumbnail_url,
                    'video_url', video_url,
                    'instagram_videos', instagram_videos,
                    'carousel_images', carousel_images,
                    'member_perks', member_perks
                ) as message_object
            FROM start_soon_base_data
        ),
        start_soon_day_groups AS (
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
                WHERE NOT is_assessment
                   OR (is_assessment AND msg_position = (day_counter % group_size))
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
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(title, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
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
END;$function$;

-- 3. Fix program_get_messages_qa
CREATE OR REPLACE FUNCTION public.program_get_messages_qa()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as subtitle,
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
            WHERE NOT is_assessment
               OR (is_assessment AND msg_position = (day_counter % group_size))
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(title, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
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
END;$function$;

-- 4. Fix get_tutorial_messages
CREATE OR REPLACE FUNCTION programs.get_tutorial_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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

    -- Step 1: Find the most recent assessment response for the user (for filtering logic)
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

    -- Get messages from Tutorial program using the same selection algorithm
    WITH base_message_data AS (
        SELECT
            pm.id,
            pm.day,
            pm.question_id,
            pm.question_response,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_title, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_title,
            REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(pm.notification_body, '_CLIENTNAME_', _user_name)), '\n', '', 'g') as notification_body,
            pm.thumbnail_url,
            pm.video_url,
            pm.instagram_videos,
            pm.carousel_images,
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
            -- Legacy claire_prompts: transform dict to array for old apps
            CASE
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            -- New claire_prompts_json: return directly from column (null until populated)
            pm.claire_prompts_json,
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
        WHERE pm.program = 'tutorial'  -- Override to use Tutorial program
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
                'claire_prompts_json', claire_prompts_json,
                'journal_prompts', journal_prompts,
                'page_info', page_info,
                'notification_title', notification_title,
                'notification_body', notification_body,
                'thumbnail_url', thumbnail_url,
                'video_url', video_url,
                'instagram_videos', instagram_videos,
                'carousel_images', carousel_images
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
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
            WHERE NOT is_assessment
               OR (is_assessment AND msg_position = (day_counter % group_size))
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(title, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM REPLACE(subtitle, '_CLIENTNAME_', _user_name)), '\n', '', 'g'),
            'body', TRIM(TRAILING FROM REPLACE(body, '_CLIENTNAME_', _user_name)),
            'color1', color1,
            'color2', color2,
            'fred_experience', fred_experience
        )
    )
    INTO stages
    FROM programs.program_stages
    WHERE stage = ANY(stage_ids);

    -- Step 4: Return final structure
    RETURN jsonb_build_object(
        'stages', COALESCE(stages, '[]'::jsonb),
        'messages', COALESCE(messages, '[]'::jsonb)
    );
END;
$function$;
