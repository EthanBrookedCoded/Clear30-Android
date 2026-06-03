alter table "comms"."sms_messages" add column "type" text;

CREATE INDEX idx_sms_messages_type ON comms.sms_messages USING btree (type);


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.sms_clear_by_type(user_id text, message_type text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Input validation
    IF user_id IS NULL OR trim(user_id) = '' THEN
        RAISE EXCEPTION 'User ID cannot be empty';
    END IF;
    
    IF message_type IS NULL OR trim(message_type) = '' THEN
        RAISE EXCEPTION 'Message type cannot be empty';
    END IF;

    -- Update future messages of the specified type to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = sms_clear_by_type.user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false)
    AND type = message_type;
END;$function$
;

CREATE OR REPLACE FUNCTION public.sms_clear_user(user_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Input validation
    IF user_id IS NULL OR trim(user_id) = '' THEN
        RAISE EXCEPTION 'User ID cannot be empty';
    END IF;

    -- Update future messages to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = sms_clear_user.user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false);
END;$function$
;

CREATE OR REPLACE FUNCTION public.sms_clear_user(user_id text, message text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Input validation
    IF user_id IS NULL OR trim(user_id) = '' THEN
        RAISE EXCEPTION 'User ID cannot be empty';
    END IF;
    
    IF message IS NULL OR trim(message) = '' THEN
        RAISE EXCEPTION 'Message pattern cannot be empty';
    END IF;

    -- Update future messages containing the pattern to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = sms_clear_user.user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false)
    AND text LIKE '%' || message || '%';
END;$function$
;

CREATE OR REPLACE FUNCTION public.sms_schedule_user(sms_data jsonb, user_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_phone_number text;
    v_message text;
    v_offset_minutes int;
    v_type text;
BEGIN
    -- Input validation for user_id
    IF user_id IS NULL OR trim(user_id) = '' THEN
        RAISE EXCEPTION 'User ID cannot be empty';
    END IF;

    -- Get the message, offset, and optional type from the JSONB input
    v_message := sms_data->>'message';
    v_offset_minutes := (sms_data->>'offset_minutes')::int;
    v_type := sms_data->>'type'; -- Can be null

    -- Input validation
    IF v_message IS NULL OR trim(v_message) = '' THEN
        RAISE EXCEPTION 'Message cannot be empty';
    END IF;

    IF v_offset_minutes IS NULL OR v_offset_minutes < 0 THEN
        RAISE EXCEPTION 'Offset minutes must be a positive number';
    END IF;

    -- Look up the user's phone number
    SELECT phone_number 
    INTO v_phone_number
    FROM public.users 
    WHERE id = user_id;

    IF v_phone_number IS NULL THEN
        RAISE EXCEPTION 'User not found or has no phone number';
    END IF;

    -- Insert the SMS message with optional type
    INSERT INTO comms.sms_messages (
        user_id,
        phone_number,
        text,
        scheduled_for,
        type
    ) VALUES (
        user_id,
        v_phone_number,
        v_message,
        now() + (v_offset_minutes * interval '1 minute'),
        v_type
    );
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
    v_type text;
    v_auth_id uuid;
BEGIN
    -- Get the message, offset, and optional type from the JSONB input
    v_message := sms_data->>'message';
    v_offset_minutes := (sms_data->>'offset_minutes')::int;
    v_type := sms_data->>'type'; -- Can be null

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

    -- Insert the SMS message with optional type
    INSERT INTO comms.sms_messages (
        user_id,
        phone_number,
        text,
        scheduled_for,
        type
    ) VALUES (
        v_user_id,
        v_phone_number,
        v_message,
        now() + (v_offset_minutes * interval '1 minute'),
        v_type
    );
END;$function$
;


