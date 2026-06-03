create sequence "comms"."sms_verify_logs_id_seq";

create table "comms"."sms_verify_logs" (
    "id" integer not null default nextval('comms.sms_verify_logs_id_seq'::regclass),
    "auth_id" uuid not null default gen_random_uuid(),
    "phone_number" text not null,
    "success" boolean not null,
    "error_message" text,
    "retry_count" integer not null default 0,
    "created_at" timestamp with time zone default now()
);


alter table "comms"."sms_verify_logs" enable row level security;

alter sequence "comms"."sms_verify_logs_id_seq" owned by "comms"."sms_verify_logs"."id";

CREATE UNIQUE INDEX sms_verify_logs_pkey ON comms.sms_verify_logs USING btree (id);

alter table "comms"."sms_verify_logs" add constraint "sms_verify_logs_pkey" PRIMARY KEY using index "sms_verify_logs_pkey";

alter table "comms"."sms_verify_logs" add constraint "sms_verify_logs_auth_id_fkey" FOREIGN KEY (auth_id) REFERENCES auth.users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "comms"."sms_verify_logs" validate constraint "sms_verify_logs_auth_id_fkey";

create policy "Disable access for all"
on "comms"."sms_verify_logs"
as permissive
for select
to public
using (false);



drop view if exists "platform"."assessment_responses_clear30";

drop view if exists "platform"."assessment_responses_life";

drop view if exists "platform"."sms_non_user_conversations";

drop view if exists "platform"."sms_non_user_conversations_v2";

drop view if exists "platform"."sms_user_conversations";

drop view if exists "platform"."sms_user_conversations_v2";


drop index if exists "programs"."program_assessment_responses_original_user_id_idx";


drop policy "Disable access for all" on "public"."admins";

drop policy "Disable all access" on "public"."amplitude_test";

drop policy "Disable public access for all users" on "public"."api_keys";

drop policy "Disable all acess" on "public"."sms_verify_logs";

revoke delete on table "public"."admins" from "anon";

revoke insert on table "public"."admins" from "anon";

revoke references on table "public"."admins" from "anon";

revoke select on table "public"."admins" from "anon";

revoke trigger on table "public"."admins" from "anon";

revoke truncate on table "public"."admins" from "anon";

revoke update on table "public"."admins" from "anon";

revoke delete on table "public"."admins" from "authenticated";

revoke insert on table "public"."admins" from "authenticated";

revoke references on table "public"."admins" from "authenticated";

revoke select on table "public"."admins" from "authenticated";

revoke trigger on table "public"."admins" from "authenticated";

revoke truncate on table "public"."admins" from "authenticated";

revoke update on table "public"."admins" from "authenticated";

revoke delete on table "public"."admins" from "service_role";

revoke insert on table "public"."admins" from "service_role";

revoke references on table "public"."admins" from "service_role";

revoke select on table "public"."admins" from "service_role";

revoke trigger on table "public"."admins" from "service_role";

revoke truncate on table "public"."admins" from "service_role";

revoke update on table "public"."admins" from "service_role";

revoke delete on table "public"."amplitude_test" from "anon";

revoke insert on table "public"."amplitude_test" from "anon";

revoke references on table "public"."amplitude_test" from "anon";

revoke select on table "public"."amplitude_test" from "anon";

revoke trigger on table "public"."amplitude_test" from "anon";

revoke truncate on table "public"."amplitude_test" from "anon";

revoke update on table "public"."amplitude_test" from "anon";

revoke delete on table "public"."amplitude_test" from "authenticated";

revoke insert on table "public"."amplitude_test" from "authenticated";

revoke references on table "public"."amplitude_test" from "authenticated";

revoke select on table "public"."amplitude_test" from "authenticated";

revoke trigger on table "public"."amplitude_test" from "authenticated";

revoke truncate on table "public"."amplitude_test" from "authenticated";

revoke update on table "public"."amplitude_test" from "authenticated";

revoke delete on table "public"."amplitude_test" from "service_role";

revoke insert on table "public"."amplitude_test" from "service_role";

revoke references on table "public"."amplitude_test" from "service_role";

revoke select on table "public"."amplitude_test" from "service_role";

revoke trigger on table "public"."amplitude_test" from "service_role";

revoke truncate on table "public"."amplitude_test" from "service_role";

revoke update on table "public"."amplitude_test" from "service_role";

revoke delete on table "public"."api_keys" from "anon";

revoke insert on table "public"."api_keys" from "anon";

revoke references on table "public"."api_keys" from "anon";

revoke select on table "public"."api_keys" from "anon";

revoke trigger on table "public"."api_keys" from "anon";

revoke truncate on table "public"."api_keys" from "anon";

revoke update on table "public"."api_keys" from "anon";

revoke delete on table "public"."api_keys" from "authenticated";

revoke insert on table "public"."api_keys" from "authenticated";

revoke references on table "public"."api_keys" from "authenticated";

revoke select on table "public"."api_keys" from "authenticated";

revoke trigger on table "public"."api_keys" from "authenticated";

revoke truncate on table "public"."api_keys" from "authenticated";

revoke update on table "public"."api_keys" from "authenticated";

revoke delete on table "public"."api_keys" from "service_role";

revoke insert on table "public"."api_keys" from "service_role";

revoke references on table "public"."api_keys" from "service_role";

revoke select on table "public"."api_keys" from "service_role";

revoke trigger on table "public"."api_keys" from "service_role";

revoke truncate on table "public"."api_keys" from "service_role";

revoke update on table "public"."api_keys" from "service_role";

revoke delete on table "public"."sms_verify_logs" from "anon";

revoke insert on table "public"."sms_verify_logs" from "anon";

revoke references on table "public"."sms_verify_logs" from "anon";

revoke select on table "public"."sms_verify_logs" from "anon";

revoke trigger on table "public"."sms_verify_logs" from "anon";

revoke truncate on table "public"."sms_verify_logs" from "anon";

revoke update on table "public"."sms_verify_logs" from "anon";

revoke delete on table "public"."sms_verify_logs" from "authenticated";

revoke insert on table "public"."sms_verify_logs" from "authenticated";

revoke references on table "public"."sms_verify_logs" from "authenticated";

revoke select on table "public"."sms_verify_logs" from "authenticated";

revoke trigger on table "public"."sms_verify_logs" from "authenticated";

revoke truncate on table "public"."sms_verify_logs" from "authenticated";

revoke update on table "public"."sms_verify_logs" from "authenticated";

revoke delete on table "public"."sms_verify_logs" from "service_role";

revoke insert on table "public"."sms_verify_logs" from "service_role";

revoke references on table "public"."sms_verify_logs" from "service_role";

revoke select on table "public"."sms_verify_logs" from "service_role";

revoke trigger on table "public"."sms_verify_logs" from "service_role";

revoke truncate on table "public"."sms_verify_logs" from "service_role";

revoke update on table "public"."sms_verify_logs" from "service_role";

alter table "public"."admins" drop constraint "admins_auth_id_fkey";

alter table "public"."api_keys" drop constraint "api_keys_key_key";

alter table "public"."sms_verify_logs" drop constraint "sms_verify_logs_auth_id_fkey";

alter table "public"."admins" drop constraint "admins_pkey";

alter table "public"."amplitude_test" drop constraint "amplitude_test_pkey";

alter table "public"."api_keys" drop constraint "api_keys_pkey";

alter table "public"."sms_verify_logs" drop constraint "sms_verify_logs_pkey";

drop index if exists "public"."admins_pkey";

drop index if exists "public"."amplitude_test_pkey";

drop index if exists "public"."api_keys_key_key";

drop index if exists "public"."api_keys_pkey";

drop index if exists "public"."sms_verify_logs_pkey";

drop table "public"."admins";

drop table "public"."amplitude_test";

drop table "public"."api_keys";

drop table "public"."sms_verify_logs";

drop sequence if exists "public"."sms_verify_logs_id_seq";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.set_switchboard_number()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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

    -- Only proceed if the user has an auth id
    IF NEW.auth_id IS NULL THEN
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
    INSERT INTO comms.sms_verify_logs (
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
END;$function$
;


