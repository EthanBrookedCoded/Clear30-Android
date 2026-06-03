alter table "guardian"."codes" drop constraint "codes_program_fkey";

alter table "guardian"."code_users" add column "timestamp" timestamp with time zone not null default now();

alter table "guardian"."codes" drop column "program";

alter table "guardian"."codes" add column "assessment_id" text not null default 'adolescent'::text;

alter table "guardian"."codes" add constraint "codes_assessment_id_fkey" FOREIGN KEY (assessment_id) REFERENCES programs.program_assessments(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "guardian"."codes" validate constraint "codes_assessment_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION guardian.check_code(input_code text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    result_json json;
BEGIN
    SELECT json_build_object(
        'code', c.code,
        'free', c.free,
        'assessment_id', c.assessment_id
    )
    INTO result_json
    FROM guardian.codes c
    WHERE c.code = input_code;
    
    RETURN result_json;
END;$function$
;


