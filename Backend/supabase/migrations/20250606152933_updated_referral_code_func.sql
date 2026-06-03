alter table "payment"."referral_codes" alter column "created_at" set default now();

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION payment.check_referral_code(input_code text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    is_valid boolean;
    rows_affected integer;
BEGIN
    -- Check if code exists (case insensitive) and increment uses if it does
    UPDATE payment.referral_codes
    SET uses = uses + 1
    WHERE LOWER(code) = LOWER(input_code)
    RETURNING free INTO is_valid;
    
    -- Check how many rows were affected by the update
    GET DIAGNOSTICS rows_affected = ROW_COUNT;
    
    -- If no rows were updated, create a new row
    IF rows_affected = 0 THEN
        INSERT INTO payment.referral_codes (code, uses, free)
        VALUES (input_code, 1, false)
        RETURNING free INTO is_valid;
    END IF;
    
    -- Return the result (null becomes false)
    RETURN COALESCE(is_valid, false);
END;$function$
;


