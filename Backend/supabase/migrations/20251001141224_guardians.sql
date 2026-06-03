alter table "guardian"."codes" add column "allow_guardian_reports" boolean not null default true;

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
        'allow_guardian_reports', c.allow_guardian_reports,
        'assessment_id', c.assessment_id
    )
    INTO result_json
    FROM guardian.codes c
    WHERE c.code = input_code;
    
    RETURN result_json;
END;$function$
;


