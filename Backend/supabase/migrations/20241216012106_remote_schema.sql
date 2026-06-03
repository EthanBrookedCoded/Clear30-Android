alter table "comms"."sms_messages" add column "canceled" boolean;


drop function if exists "public"."submit_assessment_response"(assessment_id uuid, responses jsonb);

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.sms_clear()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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
END;$function$
;

CREATE OR REPLACE FUNCTION public.sms_clear(message text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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

END;$function$
;

CREATE OR REPLACE FUNCTION public.sms_schedule(sms_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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
END;$function$
;

CREATE OR REPLACE FUNCTION public.submit_assessment_response(assessment_id text, responses jsonb)
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
        _auth_id::text, assessment_id, filtered_responses
    );

END;
$function$
;

CREATE OR REPLACE FUNCTION public.create_user(user_data json)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    _auth_id uuid;
    _phone_number text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Get phone number from auth.users table
    SELECT phone
    INTO _phone_number
    FROM auth.users
    WHERE id = _auth_id;

    -- Check if user exists
    IF EXISTS (SELECT 1 FROM public.users WHERE auth_id = _auth_id) THEN
        -- Update existing user with non-null values
        UPDATE public.users
        SET
            name = COALESCE(user_data->>'name', name),
            emoji = COALESCE(user_data->>'emoji', emoji),
            day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
            fcm_token = CASE 
                WHEN user_data->>'fcm_token' is not null THEN user_data->>'fcm_token'
                ELSE fcm_token
            END
        WHERE auth_id = _auth_id;
    ELSE
        -- Insert new user
        INSERT INTO public.users (
            id,
            auth_id,
            name,
            emoji,
            day_info,
            fcm_token,
            phone_number
        )
        VALUES (
            _auth_id::text,
            _auth_id,
            user_data->>'name',
            user_data->>'emoji',
            (user_data->'day_info')::jsonb,
            user_data->>'fcm_token',
            _phone_number
        );
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$function$
;

CREATE OR REPLACE FUNCTION public.get_feedback()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_assessment RECORD;
    feedback JSONB := '[]';
    _auth_id uuid;
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

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _auth_id::text
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
END;$function$
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

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _auth_id::text
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Step 2: Fetch program messages with transformed data structure
    WITH message_data AS (
        SELECT 
            pm.id,
            pm.day,
            pm.question_id,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
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
            ) ORDER BY day, (question_id IS NOT NULL), title
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM message_data;

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
END;$function$
;


