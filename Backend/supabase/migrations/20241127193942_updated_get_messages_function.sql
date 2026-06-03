set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_messages(user_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_assessment RECORD;
    messages JSONB := '[]';
BEGIN
    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = get_messages.user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for user %', user_id;
    END IF;

    -- Step 2: Fetch program messages for the associated program, applying filters and ordering
    SELECT jsonb_agg(message_data)
    INTO messages
    FROM (
        SELECT jsonb_build_object(
            'day', pm.day,
            'title', pm.title,
            'subtitle', pm.subtitle,
            'body', pm.body,
            'page_info', (
                SELECT COALESCE(
                    jsonb_agg(
                        jsonb_build_object(
                            'title', pmpi.title,
                            'body', pmpi.body
                        ) ORDER BY pmpi.order
                    ),
                    '[]'
                )
                FROM programs.program_message_page_info pmpi
                WHERE pmpi.message_id = pm.id
            ),
            'claire_prompts', (
                SELECT COALESCE(
                    jsonb_object_agg(cp.title, cp.prompt),
                    '{}'
                )
                FROM programs.program_message_claire_prompts pmcp
                JOIN library.claire_prompts cp ON pmcp.prompt_id = cp.id
                WHERE pmcp.message_id = pm.id
            ),
            'journal_prompts', (
                SELECT COALESCE(
                    jsonb_agg(jp.prompt),
                    '[]'
                )
                FROM programs.program_message_journal_prompts pmjp
                JOIN library.journal_prompts jp ON pmjp.prompt_id = jp.id
                WHERE pmjp.message_id = pm.id
            ),
            'resources', (
                SELECT COALESCE(
                    jsonb_object_agg(r.title, r.url),
                    '{}'
                )
                FROM programs.program_message_resources pmr
                JOIN library.resources r ON pmr.resource_id = r.id
                WHERE pmr.message_id = pm.id
            ),
            'meditation', (
                SELECT COALESCE(
                    jsonb_build_object(
                        'meditation_title', m.title,
                        'meditation_link', m.url
                    ),
                    '{}'
                )
                FROM programs.program_message_meditations pmm
                JOIN library.meditations m ON pmm.meditation_id = m.id
                WHERE pmm.message_id = pm.id
            )
        ) AS message_data
        FROM programs.program_messages pm
        WHERE pm.program = recent_assessment.program
        AND (
            pm.question_id IS NULL
            OR (
                pm.question_id IS NOT NULL
                AND (
                    -- Case 1: The response is a string and matches question_response
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                    AND pm.question_response = recent_assessment.responses ->> pm.question_id
                )
                OR (
                    -- Case 2: The response is an array and contains question_response
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                    AND pm.question_response = ANY (
                        SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                    )
                )
            )
        )
        ORDER BY pm.day ASC, 
                 pm.question_id IS NOT NULL
    ) subquery;

    -- Return the formatted messages
    RETURN jsonb_build_object('messages', COALESCE(messages, '[]'::JSONB));
END;$function$
;


