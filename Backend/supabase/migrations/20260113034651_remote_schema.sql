set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_dr_fred_conversation()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user_name TEXT;
    v_is_disclaimer BOOLEAN;
BEGIN
    -- Get user name
    SELECT name INTO v_user_name FROM public.users WHERE id = NEW.user_id;
    
    -- Check if this is the disclaimer message
    v_is_disclaimer := NEW.text ILIKE '%Dr. Fred holds a Ph.D.%';
    
    -- Insert or update the conversation summary
    INSERT INTO comms.dr_fred_conversations (
        user_id, name, last_message_id, last_message_text, 
        last_message_timestamp, last_message_outbound, 
        last_real_message_outbound, message_count
    )
    VALUES (
        NEW.user_id,
        COALESCE(v_user_name, 'Unknown User'),
        NEW.id,
        NEW.text,
        NEW.created_at,
        NEW.outbound,
        CASE WHEN v_is_disclaimer THEN NULL ELSE NEW.outbound END,
        1
    )
    ON CONFLICT (user_id) DO UPDATE SET
        name = COALESCE(EXCLUDED.name, comms.dr_fred_conversations.name),
        last_message_id = EXCLUDED.last_message_id,
        last_message_text = EXCLUDED.last_message_text,
        last_message_timestamp = EXCLUDED.last_message_timestamp,
        last_message_outbound = EXCLUDED.last_message_outbound,
        -- Only update last_real_message_outbound if this isn't the disclaimer
        last_real_message_outbound = CASE 
            WHEN v_is_disclaimer THEN comms.dr_fred_conversations.last_real_message_outbound
            ELSE NEW.outbound
        END,
        message_count = comms.dr_fred_conversations.message_count + 1,
        updated_at = NOW()
    WHERE EXCLUDED.last_message_timestamp >= comms.dr_fred_conversations.last_message_timestamp;
    
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.update_sms_conversation()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user_name TEXT;
    v_is_user BOOLEAN;
BEGIN
    -- Skip if message is canceled or scheduled for the future
    IF NEW.canceled = true OR NEW.scheduled_for > NOW() THEN
        RETURN NEW;
    END IF;

    -- Determine if this is a user conversation
    v_is_user := NEW.user_id IS NOT NULL;

    -- Get user name if user_id exists
    IF v_is_user THEN
        SELECT name INTO v_user_name 
        FROM public.users 
        WHERE id = NEW.user_id;
    END IF;

    -- Upsert the conversation record
    INSERT INTO platform.sms_conversations (
        phone_number,
        user_id,
        name,
        last_message_id,
        last_message_text,
        last_message_timestamp,
        last_message_outbound,
        is_user,
        updated_at
    ) VALUES (
        NEW.phone_number,
        NEW.user_id,
        COALESCE(v_user_name, 'Unknown (' || NEW.phone_number || ')'),
        NEW.id,
        NEW.text,
        COALESCE(NEW.sent_at, NEW.created_at),
        NEW.outbound,
        v_is_user,
        NOW()
    )
    ON CONFLICT (phone_number) DO UPDATE SET
        user_id = COALESCE(EXCLUDED.user_id, platform.sms_conversations.user_id),
        name = CASE 
            WHEN EXCLUDED.user_id IS NOT NULL THEN EXCLUDED.name 
            ELSE platform.sms_conversations.name 
        END,
        last_message_id = EXCLUDED.last_message_id,
        last_message_text = EXCLUDED.last_message_text,
        last_message_timestamp = EXCLUDED.last_message_timestamp,
        last_message_outbound = EXCLUDED.last_message_outbound,
        is_user = COALESCE(EXCLUDED.is_user, platform.sms_conversations.is_user),
        updated_at = NOW()
    WHERE EXCLUDED.last_message_timestamp >= platform.sms_conversations.last_message_timestamp;

    RETURN NEW;
END;
$function$
;


