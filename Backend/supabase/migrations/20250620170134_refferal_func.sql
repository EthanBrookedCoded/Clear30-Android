alter table "payment"."referral_codes" add column "group_id" uuid;

alter table "payment"."referral_codes" add constraint "referral_codes_group_id_fkey" FOREIGN KEY (group_id) REFERENCES groups.groups(id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "payment"."referral_codes" validate constraint "referral_codes_group_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION payment.check_referral_code_json(input_code text)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    result_record RECORD;
    rows_affected INTEGER;
    lowercase_code TEXT;
BEGIN
    -- Convert input code to lowercase for logging
    lowercase_code := LOWER(input_code);
    
    -- Log the lowercase version of the input code
    RAISE LOG 'Processing referral code: %', lowercase_code;
    
    -- Check if code exists (case insensitive) and increment uses if it does
    UPDATE payment.referral_codes
    SET uses = uses + 1
    WHERE LOWER(code) = lowercase_code
    RETURNING free, group_id INTO result_record;
    
    -- Check how many rows were affected by the update
    GET DIAGNOSTICS rows_affected = ROW_COUNT;
    
    -- If no rows were updated, create a new row
    IF rows_affected = 0 THEN
        INSERT INTO payment.referral_codes (code, uses, free, group_id)
        VALUES (input_code, 1, false, NULL)
        RETURNING free, group_id INTO result_record;
    END IF;
    
    -- Return JSON with is_free and group_id
    RETURN json_build_object(
        'is_free', COALESCE(result_record.free, false),
        'group_id', result_record.group_id
    );
END;
$function$
;


