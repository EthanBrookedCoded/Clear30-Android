set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.sms_clear_by_type(user_id text, message_type text)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
BEGIN
    -- Input validation
    IF sms_clear_by_type.user_id IS NULL OR trim(sms_clear_by_type.user_id) = '' THEN
        RAISE EXCEPTION 'User ID cannot be empty';
    END IF;
    
    IF sms_clear_by_type.message_type IS NULL OR trim(sms_clear_by_type.message_type) = '' THEN
        RAISE EXCEPTION 'Message type cannot be empty';
    END IF;

    -- Update future messages of the specified type to be canceled
    -- Now matches types that START WITH the provided message_type
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE comms.sms_messages.user_id = sms_clear_by_type.user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false)
    AND type LIKE sms_clear_by_type.message_type || '%';
END;
$function$
;


