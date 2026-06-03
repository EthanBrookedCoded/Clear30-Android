-- Fix get_tutorial_messages_v2: when _break_reason is NULL, only return generic messages
-- (no assessment-based filtering). Previously it fell through to the old logic which
-- could match random break reasons from the user's assessment responses.
CREATE OR REPLACE FUNCTION programs.get_tutorial_messages_v2(_break_reason TEXT DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    _auth_id := auth.uid();

    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

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
        WHERE pm.program = 'tutorial'
        AND (
            -- Always include generic messages (no question_id)
            pm.question_id IS NULL
            OR (
                -- When break_reason is provided, match that specific reason
                _break_reason IS NOT NULL
                AND pm.question_id = 'Break-Reason'
                AND pm.question_response = _break_reason
            )
            -- When _break_reason IS NULL, only generic messages are returned (no OR clause)
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

    RETURN jsonb_build_object(
        'stages', COALESCE(stages, '[]'::jsonb),
        'messages', COALESCE(messages, '[]'::jsonb)
    );
END;
$$;
