create table "programs"."program_stages" (
    "stage" text not null,
    "title" text not null,
    "subtitle" text not null,
    "body" text not null,
    "color1" text not null,
    "color2" text not null,
    "fred_experience" text
);


alter table "programs"."program_stages" enable row level security;

alter table "programs"."program_messages" add column "stage" text;

CREATE UNIQUE INDEX program_stages_pkey ON programs.program_stages USING btree (stage);

alter table "programs"."program_stages" add constraint "program_stages_pkey" PRIMARY KEY using index "program_stages_pkey";

alter table "programs"."program_messages" add constraint "program_messages_stage_fkey" FOREIGN KEY (stage) REFERENCES programs.program_stages(stage) ON UPDATE CASCADE not valid;

alter table "programs"."program_messages" validate constraint "program_messages_stage_fkey";

create policy "Disable access for all"
on "programs"."program_stages"
as permissive
for all
to public
using (false);



set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_feedback(user_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_assessment RECORD;
    feedback JSONB := '[]';
BEGIN
    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = get_feedback.user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for user %', user_id;
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
END;$function$
;

CREATE OR REPLACE FUNCTION public.get_messages(user_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
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


