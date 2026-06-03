
set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.admin_check()
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
declare
  uid uuid;
  is_admin boolean;
begin
  -- Get the current user ID from the JWT using auth.uid()
  select auth.uid() into uid;
  
  if uid is null then
    return false;
  end if;

  select exists(select 1 from public.admins where auth_id = uid) into is_admin;
  return is_admin;
end;
$function$
;

drop trigger if exists "send_notification" on "comms"."notifications";

drop policy "Disable access for all" on "comms"."dr_fred";

drop policy "Disable access for all" on "comms"."sms_messages";

alter table "comms"."dr_fred" drop column "message";

alter table "comms"."dr_fred" drop column "timestamp";

alter table "comms"."dr_fred" add column "created_at" timestamp with time zone not null default now();

alter table "comms"."dr_fred" add column "text" text not null;

grant insert on table "comms"."dr_fred" to "authenticated";

grant select on table "comms"."dr_fred" to "authenticated";

grant update on table "comms"."dr_fred" to "authenticated";

grant insert on table "comms"."sms_messages" to "authenticated";

grant select on table "comms"."sms_messages" to "authenticated";

grant update on table "comms"."sms_messages" to "authenticated";

grant insert on table "comms"."sms_statuses" to "authenticated";

grant select on table "comms"."sms_statuses" to "authenticated";

grant update on table "comms"."sms_statuses" to "authenticated";

create policy "Admin only"
on "comms"."dr_fred"
as permissive
for all
to public
using (admin_check());


create policy "Admin only"
on "comms"."sms_messages"
as permissive
for all
to public
using (admin_check());


CREATE TRIGGER notification_send_trigger AFTER INSERT ON comms.notifications FOR EACH ROW EXECUTE FUNCTION supabase_functions.http_request('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/notification_send', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');


drop policy "Disable access for all" on "library"."claire_prompts";

drop policy "Disable access for all" on "library"."journal_prompts";

drop policy "Disable access for all" on "library"."meditations";

drop policy "Disable access for all" on "library"."resources";

revoke delete on table "library"."claire_prompts" from "anon";

revoke insert on table "library"."claire_prompts" from "anon";

revoke references on table "library"."claire_prompts" from "anon";

revoke select on table "library"."claire_prompts" from "anon";

revoke trigger on table "library"."claire_prompts" from "anon";

revoke truncate on table "library"."claire_prompts" from "anon";

revoke update on table "library"."claire_prompts" from "anon";

revoke delete on table "library"."claire_prompts" from "authenticated";

revoke insert on table "library"."claire_prompts" from "authenticated";

revoke references on table "library"."claire_prompts" from "authenticated";

revoke select on table "library"."claire_prompts" from "authenticated";

revoke trigger on table "library"."claire_prompts" from "authenticated";

revoke truncate on table "library"."claire_prompts" from "authenticated";

revoke update on table "library"."claire_prompts" from "authenticated";

revoke delete on table "library"."journal_prompts" from "anon";

revoke insert on table "library"."journal_prompts" from "anon";

revoke references on table "library"."journal_prompts" from "anon";

revoke select on table "library"."journal_prompts" from "anon";

revoke trigger on table "library"."journal_prompts" from "anon";

revoke truncate on table "library"."journal_prompts" from "anon";

revoke update on table "library"."journal_prompts" from "anon";

revoke delete on table "library"."journal_prompts" from "authenticated";

revoke insert on table "library"."journal_prompts" from "authenticated";

revoke references on table "library"."journal_prompts" from "authenticated";

revoke select on table "library"."journal_prompts" from "authenticated";

revoke trigger on table "library"."journal_prompts" from "authenticated";

revoke truncate on table "library"."journal_prompts" from "authenticated";

revoke update on table "library"."journal_prompts" from "authenticated";

revoke delete on table "library"."meditations" from "anon";

revoke insert on table "library"."meditations" from "anon";

revoke references on table "library"."meditations" from "anon";

revoke select on table "library"."meditations" from "anon";

revoke trigger on table "library"."meditations" from "anon";

revoke truncate on table "library"."meditations" from "anon";

revoke update on table "library"."meditations" from "anon";

revoke delete on table "library"."meditations" from "authenticated";

revoke insert on table "library"."meditations" from "authenticated";

revoke references on table "library"."meditations" from "authenticated";

revoke select on table "library"."meditations" from "authenticated";

revoke trigger on table "library"."meditations" from "authenticated";

revoke truncate on table "library"."meditations" from "authenticated";

revoke update on table "library"."meditations" from "authenticated";

revoke delete on table "library"."resources" from "anon";

revoke insert on table "library"."resources" from "anon";

revoke references on table "library"."resources" from "anon";

revoke select on table "library"."resources" from "anon";

revoke trigger on table "library"."resources" from "anon";

revoke truncate on table "library"."resources" from "anon";

revoke update on table "library"."resources" from "anon";

revoke delete on table "library"."resources" from "authenticated";

revoke insert on table "library"."resources" from "authenticated";

revoke references on table "library"."resources" from "authenticated";

revoke select on table "library"."resources" from "authenticated";

revoke trigger on table "library"."resources" from "authenticated";

revoke truncate on table "library"."resources" from "authenticated";

revoke update on table "library"."resources" from "authenticated";

alter table "library"."claire_prompts" drop constraint "claire_prompts_pkey";

alter table "library"."journal_prompts" drop constraint "journal_prompts_pkey";

alter table "library"."meditations" drop constraint "meditations_pkey";

alter table "library"."resources" drop constraint "resources_pkey";

drop index if exists "library"."claire_prompts_pkey";

drop index if exists "library"."journal_prompts_pkey";

drop index if exists "library"."meditations_pkey";

drop index if exists "library"."resources_pkey";

drop table "library"."claire_prompts";

drop table "library"."journal_prompts";

drop table "library"."meditations";

drop table "library"."resources";


drop policy "Enable SELECT for authed users" on "public"."users";

create table "public"."admins" (
    "id" bigint generated by default as identity not null,
    "auth_id" uuid not null
);


alter table "public"."admins" enable row level security;

CREATE UNIQUE INDEX admins_pkey ON public.admins USING btree (id);

alter table "public"."admins" add constraint "admins_pkey" PRIMARY KEY using index "admins_pkey";

alter table "public"."admins" add constraint "admins_auth_id_fkey" FOREIGN KEY (auth_id) REFERENCES auth.users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."admins" validate constraint "admins_auth_id_fkey";

CREATE OR REPLACE FUNCTION public.dr_fred_get_messages()
 RETURNS text[]
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$DECLARE
    v_user_id text;
    v_messages text[];
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Get messages and store them in array
    SELECT array_agg(text ORDER BY created_at)
    INTO v_messages
    FROM comms.dr_fred
    WHERE user_id = v_user_id
    AND outbound = true;  -- Only get messages from Dr. Fred (outbound = true)

    -- Return empty array if no messages found
    RETURN COALESCE(v_messages, ARRAY[]::text[]);
END;$function$
;

CREATE OR REPLACE FUNCTION public.dr_fred_send_message(message text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_user_id text;
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Insert the message
    INSERT INTO comms.dr_fred (
        user_id,
        text,
        outbound
    ) VALUES (
        v_user_id,
        message,
        false
    );
END;$function$
;

grant delete on table "public"."admins" to "anon";

grant insert on table "public"."admins" to "anon";

grant references on table "public"."admins" to "anon";

grant select on table "public"."admins" to "anon";

grant trigger on table "public"."admins" to "anon";

grant truncate on table "public"."admins" to "anon";

grant update on table "public"."admins" to "anon";

grant delete on table "public"."admins" to "authenticated";

grant insert on table "public"."admins" to "authenticated";

grant references on table "public"."admins" to "authenticated";

grant select on table "public"."admins" to "authenticated";

grant trigger on table "public"."admins" to "authenticated";

grant truncate on table "public"."admins" to "authenticated";

grant update on table "public"."admins" to "authenticated";

grant delete on table "public"."admins" to "service_role";

grant insert on table "public"."admins" to "service_role";

grant references on table "public"."admins" to "service_role";

grant select on table "public"."admins" to "service_role";

grant trigger on table "public"."admins" to "service_role";

grant truncate on table "public"."admins" to "service_role";

grant update on table "public"."admins" to "service_role";

create policy "Disable access for all"
on "public"."admins"
as permissive
for all
to public
using (false);


create policy "Enable SELECT for authed users and admins"
on "public"."users"
as permissive
for select
to public
using (((auth_id = auth.uid()) OR admin_check()));


CREATE TRIGGER amplitude_forward_event AFTER INSERT ON public.events FOR EACH ROW EXECUTE FUNCTION supabase_functions.http_request('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_event', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');


create or replace view "views"."sms_unanswered" as  WITH latest_messages AS (
         SELECT DISTINCT ON (sms_messages.phone_number) sms_messages.id,
            sms_messages.user_id,
            sms_messages.phone_number,
            sms_messages.text,
            sms_messages.outbound,
            sms_messages.created_at,
            sms_messages.sent_at
           FROM comms.sms_messages
          WHERE ((sms_messages.sent_at IS NOT NULL) AND (sms_messages.canceled = false))
          ORDER BY sms_messages.phone_number, sms_messages.sent_at DESC
        ), message_history AS (
         SELECT sms_messages.phone_number,
            jsonb_agg(
                CASE
                    WHEN sms_messages.outbound THEN concat('clear30: ', sms_messages.text)
                    ELSE concat('user: ', sms_messages.text)
                END ORDER BY sms_messages.sent_at) AS message_history
           FROM comms.sms_messages
          WHERE ((sms_messages.sent_at IS NOT NULL) AND (sms_messages.canceled = false))
          GROUP BY sms_messages.phone_number
        )
 SELECT u.name AS user_name,
    lm.user_id,
    lm.phone_number,
    lm.text AS latest_message,
    lm.sent_at,
    mh.message_history
   FROM ((latest_messages lm
     LEFT JOIN users u ON ((lm.user_id = u.id)))
     LEFT JOIN message_history mh ON ((lm.phone_number = mh.phone_number)))
  WHERE (NOT lm.outbound);



