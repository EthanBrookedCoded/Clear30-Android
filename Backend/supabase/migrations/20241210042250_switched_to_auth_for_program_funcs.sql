drop function if exists "public"."get_feedback"(user_id text);

drop function if exists "public"."get_messages"(user_id text);

drop function if exists "public"."submit_assessment_response"(user_id text, assessment_id text, responses jsonb);

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_feedback()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    recent_assessment RECORD;
    feedback JSONB := '[]';
    _auth_id uuid;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _auth_id
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
            'body', af.body,
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
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _auth_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Step 2: Fetch program messages with transformed data structure
    WITH message_data AS (
        SELECT 
            pm.day,
            TRIM(TRAILING FROM pm.title) as title,
            TRIM(TRAILING FROM pm.subtitle) as subtitle,
            TRIM(TRAILING FROM pm.body) as body,
            pm.stage,
            -- Transform resources from object to array of objects
            CASE 
                WHEN pm.resources IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'url', value
                    )) FROM jsonb_each_text(pm.resources))
                ELSE NULL
            END as resources,
            -- Transform meditation to name/url format
            CASE 
                WHEN pm.meditation IS NOT NULL THEN
                    jsonb_build_object(
                        'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                        'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                    )
                ELSE NULL
            END as meditation,
            -- Transform claire_prompts to array of title/prompt objects
            CASE 
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            pm.journal_prompts,
            pm.page_info
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
    )
    -- Aggregate messages and collect unique stages
    SELECT 
        jsonb_agg(
            jsonb_build_object(
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
            ) ORDER BY day, title
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', TRIM(TRAILING FROM title),
            'subtitle', TRIM(TRAILING FROM subtitle),
            'body', TRIM(TRAILING FROM body),
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
END;$function$
;

CREATE OR REPLACE FUNCTION public.submit_assessment_response(assessment_id uuid, responses jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    valid_question_ids JSONB;
    filtered_responses JSONB := '{}';
    _auth_id uuid;
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
        _auth_id, assessment_id, filtered_responses
    );

END;
$function$
;


