-- Create new version of assessment submission function that returns the ID
CREATE OR REPLACE FUNCTION public.program_submit_assessment_response_v2(assessment_id text, responses jsonb)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
    valid_question_ids JSONB;
    filtered_responses JSONB := '{}'::jsonb;
    _auth_id uuid;
    _user_id text;
    _response_id bigint;
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

    -- Insert into the AssessmentResponses table and capture the ID
    INSERT INTO programs.program_assessment_responses (
        user_id, assessment, responses
    )
    VALUES (
        _user_id, assessment_id, filtered_responses
    )
    RETURNING id INTO _response_id;

    -- Return the ID
    RETURN _response_id;

END;
$function$;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION public.program_submit_assessment_response_v2(text, jsonb) TO authenticated;

