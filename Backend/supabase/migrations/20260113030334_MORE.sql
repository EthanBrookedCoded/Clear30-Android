create sequence "comms"."dr_fred_conversations_id_seq";

create table "comms"."dr_fred_conversations" (
    "id" bigint not null default nextval('comms.dr_fred_conversations_id_seq'::regclass),
    "user_id" text not null,
    "name" text,
    "last_message_id" bigint not null,
    "last_message_text" text,
    "last_message_timestamp" timestamp with time zone not null,
    "last_message_outbound" boolean not null,
    "last_real_message_outbound" boolean,
    "message_count" integer default 1,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "comms"."dr_fred_conversations" enable row level security;

alter sequence "comms"."dr_fred_conversations_id_seq" owned by "comms"."dr_fred_conversations"."id";

CREATE UNIQUE INDEX dr_fred_conversations_pkey ON comms.dr_fred_conversations USING btree (id);

CREATE UNIQUE INDEX dr_fred_conversations_user_id_key ON comms.dr_fred_conversations USING btree (user_id);

CREATE INDEX idx_dr_fred_conv_name ON comms.dr_fred_conversations USING btree (name);

CREATE INDEX idx_dr_fred_conv_timestamp ON comms.dr_fred_conversations USING btree (last_message_timestamp DESC);

CREATE INDEX idx_dr_fred_conv_unanswered ON comms.dr_fred_conversations USING btree (last_real_message_outbound, last_message_timestamp DESC);

alter table "comms"."dr_fred_conversations" add constraint "dr_fred_conversations_pkey" PRIMARY KEY using index "dr_fred_conversations_pkey";

alter table "comms"."dr_fred_conversations" add constraint "dr_fred_conversations_user_id_key" UNIQUE using index "dr_fred_conversations_user_id_key";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_dr_fred_conversation()
 RETURNS trigger
 LANGUAGE plpgsql
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

grant delete on table "comms"."dr_fred_conversations" to "anon";

grant insert on table "comms"."dr_fred_conversations" to "anon";

grant references on table "comms"."dr_fred_conversations" to "anon";

grant select on table "comms"."dr_fred_conversations" to "anon";

grant trigger on table "comms"."dr_fred_conversations" to "anon";

grant truncate on table "comms"."dr_fred_conversations" to "anon";

grant update on table "comms"."dr_fred_conversations" to "anon";

grant delete on table "comms"."dr_fred_conversations" to "authenticated";

grant insert on table "comms"."dr_fred_conversations" to "authenticated";

grant references on table "comms"."dr_fred_conversations" to "authenticated";

grant select on table "comms"."dr_fred_conversations" to "authenticated";

grant trigger on table "comms"."dr_fred_conversations" to "authenticated";

grant truncate on table "comms"."dr_fred_conversations" to "authenticated";

grant update on table "comms"."dr_fred_conversations" to "authenticated";

grant delete on table "comms"."dr_fred_conversations" to "service_role";

grant insert on table "comms"."dr_fred_conversations" to "service_role";

grant references on table "comms"."dr_fred_conversations" to "service_role";

grant select on table "comms"."dr_fred_conversations" to "service_role";

grant trigger on table "comms"."dr_fred_conversations" to "service_role";

grant truncate on table "comms"."dr_fred_conversations" to "service_role";

grant update on table "comms"."dr_fred_conversations" to "service_role";

create policy "Admin only select"
on "comms"."dr_fred_conversations"
as permissive
for select
to public
using (( SELECT admin_check() AS admin_check));


CREATE TRIGGER trg_update_dr_fred_conversation AFTER INSERT OR UPDATE ON comms.dr_fred FOR EACH ROW EXECUTE FUNCTION comms.update_dr_fred_conversation();


alter table "platform"."sms_conversations" enable row level security;

create policy "Admin only select"
on "platform"."sms_conversations"
as permissive
for select
to public
using (( SELECT admin_check() AS admin_check));



