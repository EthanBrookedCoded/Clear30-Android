alter table "community"."tags" add column "hidden" boolean not null default false;


alter table "guardian"."codes" drop constraint "codes_program_fkey";

drop function if exists "guardian"."check_code"(input_code text);

alter table "guardian"."codes" drop column "uses";

alter table "guardian"."codes" add constraint "codes_program_fkey" FOREIGN KEY (program) REFERENCES programs.program_assessments(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "guardian"."codes" validate constraint "codes_program_fkey";

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
        'program', c.program
    )
    INTO result_json
    FROM guardian.codes c
    WHERE c.code = input_code;
    
    RETURN result_json;
END;$function$
;


