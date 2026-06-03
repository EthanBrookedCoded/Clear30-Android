set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.get_last_check_in_date(p_user_id text)
 RETURNS timestamp with time zone
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_day_info JSONB;
    v_last_date TEXT;
BEGIN
    -- Get user day_info
    SELECT day_info INTO v_day_info
    FROM public.users WHERE id = p_user_id;

    -- day_info is array of pairs: [date, {sober: bool}, date, {sober: bool}, ...]
    -- Last date is at index (length - 2), second to last element
    IF v_day_info IS NOT NULL AND jsonb_array_length(v_day_info) >= 2 THEN
        v_last_date := v_day_info->>(jsonb_array_length(v_day_info) - 2);
        RETURN v_last_date::TIMESTAMPTZ;
    END IF;

    RETURN NULL;
END;
$function$
;


