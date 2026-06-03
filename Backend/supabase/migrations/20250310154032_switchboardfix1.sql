CREATE OR REPLACE FUNCTION auth.set_switchboard_number()
RETURNS TRIGGER AS $$
DECLARE
    v_success BOOLEAN;
    v_response JSON;
    v_error_message TEXT;
    v_retry_count INTEGER := 0;
    v_max_retries INTEGER := 1; -- One retry attempt
    v_switchboard_url TEXT := 'https://aqhzgkdkjebmdqgbzkbu.supabase.co/functions/v1/sms_handle_service_clear30';
    v_jwt TEXT := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFxaHpna2RramVibWRxZ2J6a2J1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1NTkwMDMsImV4cCI6MjA1NzEzNTAwM30.ei7F4MlGxbUpPHmzxa4CWy6YZ-1JjF2hmPelBbDeaSk';
BEGIN
    -- Only proceed if the user has a phone number
    IF NEW.phone IS NULL OR NEW.phone = '' THEN
        RETURN NEW;
    END IF;

    -- Try to call the switchboard function
    <<retry_loop>>
    WHILE v_retry_count <= v_max_retries LOOP
        BEGIN
            SELECT 
                status >= 200 AND status < 300,
                content::json
            INTO v_success, v_response
            FROM http((
                'POST',
                v_switchboard_url,
                ARRAY[
                    http_header('Content-Type', 'application/json'),
                    http_header('Authorization', 'Bearer ' || v_jwt)
                ],
                'application/json',
                json_build_object('phone_number', NEW.phone)::text
            ));

            -- If successful, exit the loop
            IF v_success THEN
                EXIT retry_loop;
            END IF;

            -- If not successful, prepare for retry
            v_error_message := v_response->>'error';
            v_retry_count := v_retry_count + 1;
            
            -- Wait a moment before retrying (500ms)
            PERFORM pg_sleep(0.5);
            
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
        NEW.id,
        NEW.phone,
        v_success,
        v_error_message,
        v_retry_count
    );

    -- Return the NEW record to allow the insert to proceed
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;