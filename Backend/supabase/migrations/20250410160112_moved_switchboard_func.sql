set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.set_switchboard_number()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_request_id BIGINT;
    v_success BOOLEAN := FALSE;
    v_error_message TEXT;
    v_retry_count INTEGER := 0;
    v_max_retries INTEGER := 1;
    v_switchboard_url TEXT := 'https://aqhzgkdkjebmdqgbzkbu.supabase.co/functions/v1/sms_handle_service_clear30';
    v_jwt TEXT := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFxaHpna2RramVibWRxZ2J6a2J1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1NTkwMDMsImV4cCI6MjA1NzEzNTAwM30.ei7F4MlGxbUpPHmzxa4CWy6YZ-1JjF2hmPelBbDeaSk';
BEGIN
    -- Only proceed if the user has a phone number
    IF NEW.phone_number IS NULL OR NEW.phone_number = '' THEN
        RETURN NEW;
    END IF;

    -- For updates, only proceed if phone_number has changed
    IF TG_OP = 'UPDATE' THEN
        IF NEW.phone_number = OLD.phone_number THEN
            RETURN NEW;
        END IF;
    END IF;

    -- Try to call the switchboard function
    <<retry_loop>>
    WHILE v_retry_count <= v_max_retries LOOP
        BEGIN
            -- Make HTTP request using pg_net
            SELECT net.http_post(
                -- URL for the request
                url := v_switchboard_url,
                
                -- Body of the POST request (as jsonb)
                body := jsonb_build_object('phone_number', NEW.phone_number),
                
                -- No URL parameters
                params := '{}'::jsonb,
                
                -- Headers including Content-Type and Authorization
                headers := jsonb_build_object(
                    'Content-Type', 'application/json',
                    'Authorization', 'Bearer ' || v_jwt
                ),
                
                -- Timeout in milliseconds
                timeout_milliseconds := 5000
            )
            INTO v_request_id;

            -- If we got a request ID, consider it a success
            v_success := v_request_id IS NOT NULL;
            
            -- Exit the retry loop
            EXIT retry_loop;
            
        EXCEPTION WHEN OTHERS THEN
            -- Catch any other errors
            v_success := FALSE;
            v_error_message := SQLERRM;
            v_retry_count := v_retry_count + 1;
            
            -- Wait a moment before retrying (500ms)
            PERFORM pg_sleep(0.5);
        END;
    END LOOP retry_loop;

    -- Log the result
    INSERT INTO public.sms_verify_logs (
        auth_id,
        phone_number,
        success,
        error_message,
        retry_count
    ) VALUES (
        NEW.auth_id,  -- Using auth_id field from public.users
        NEW.phone_number,
        v_success,
        CASE 
            WHEN v_success THEN 'HTTP request initiated with request ID: ' || v_request_id
            ELSE v_error_message
        END,
        v_retry_count
    );

    -- Return the NEW record to allow the operation to proceed
    RETURN NEW;
END;
$function$
;

CREATE TRIGGER after_user_insert AFTER INSERT ON public.users FOR EACH ROW EXECUTE FUNCTION set_switchboard_number();

CREATE TRIGGER after_user_phone_update AFTER UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION set_switchboard_number();


