create extension if not exists "http" with schema "public" version '1.5';

create sequence "public"."sms_verify_logs_id_seq";

create table "public"."sms_verify_logs" (
    "id" integer not null default nextval('sms_verify_logs_id_seq'::regclass),
    "auth_id" uuid not null default gen_random_uuid(),
    "phone_number" text not null,
    "success" boolean not null,
    "error_message" text,
    "retry_count" integer not null default 0,
    "created_at" timestamp with time zone default now()
);


alter table "public"."sms_verify_logs" enable row level security;

alter sequence "public"."sms_verify_logs_id_seq" owned by "public"."sms_verify_logs"."id";

CREATE UNIQUE INDEX sms_verify_logs_pkey ON public.sms_verify_logs USING btree (id);

alter table "public"."sms_verify_logs" add constraint "sms_verify_logs_pkey" PRIMARY KEY using index "sms_verify_logs_pkey";

alter table "public"."sms_verify_logs" add constraint "sms_verify_logs_auth_id_fkey" FOREIGN KEY (auth_id) REFERENCES auth.users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."sms_verify_logs" validate constraint "sms_verify_logs_auth_id_fkey";

DO $$ BEGIN
    IF to_regtype('public.http_header') IS NULL THEN
        create type "public"."http_header" as ("field" character varying, "value" character varying);
    END IF;
END $$;


DO $$ BEGIN
    IF to_regtype('public.http_request') IS NULL THEN
        create type "public"."http_request" as ("method" http_method, "uri" character varying, "headers" http_header[], "content_type" character varying, "content" character varying);
    END IF;
END $$;


DO $$ BEGIN
    IF to_regtype('public.http_response') IS NULL THEN
        create type "public"."http_response" as ("status" integer, "content_type" character varying, "headers" http_header[], "content" character varying);
    END IF;
END $$;

grant delete on table "public"."sms_verify_logs" to "anon";

grant insert on table "public"."sms_verify_logs" to "anon";

grant references on table "public"."sms_verify_logs" to "anon";

grant select on table "public"."sms_verify_logs" to "anon";

grant trigger on table "public"."sms_verify_logs" to "anon";

grant truncate on table "public"."sms_verify_logs" to "anon";

grant update on table "public"."sms_verify_logs" to "anon";

grant delete on table "public"."sms_verify_logs" to "authenticated";

grant insert on table "public"."sms_verify_logs" to "authenticated";

grant references on table "public"."sms_verify_logs" to "authenticated";

grant select on table "public"."sms_verify_logs" to "authenticated";

grant trigger on table "public"."sms_verify_logs" to "authenticated";

grant truncate on table "public"."sms_verify_logs" to "authenticated";

grant update on table "public"."sms_verify_logs" to "authenticated";

grant delete on table "public"."sms_verify_logs" to "service_role";

grant insert on table "public"."sms_verify_logs" to "service_role";

grant references on table "public"."sms_verify_logs" to "service_role";

grant select on table "public"."sms_verify_logs" to "service_role";

grant trigger on table "public"."sms_verify_logs" to "service_role";

grant truncate on table "public"."sms_verify_logs" to "service_role";

grant update on table "public"."sms_verify_logs" to "service_role";

create policy "Disable all acess"
on "public"."sms_verify_logs"
as permissive
for all
to public
using (false);



-- AUTH shit

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
        user_id,
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

-- Create the trigger on the auth.users table
DROP TRIGGER IF EXISTS after_sign_up ON auth.users;
CREATE TRIGGER after_sign_up
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.set_switchboard_number();