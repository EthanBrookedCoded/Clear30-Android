set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_sms_conversation()
 RETURNS trigger
 LANGUAGE plpgsql
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

CREATE TRIGGER trg_update_sms_conversation AFTER INSERT OR UPDATE ON comms.sms_messages FOR EACH ROW EXECUTE FUNCTION comms.update_sms_conversation();


create sequence "platform"."sms_conversations_id_seq";

create table "platform"."sms_conversations" (
    "id" bigint not null default nextval('platform.sms_conversations_id_seq'::regclass),
    "phone_number" text not null,
    "user_id" text,
    "name" text,
    "last_message_id" bigint not null,
    "last_message_text" text,
    "last_message_timestamp" timestamp with time zone not null,
    "last_message_outbound" boolean not null,
    "is_user" boolean not null default false,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter sequence "platform"."sms_conversations_id_seq" owned by "platform"."sms_conversations"."id";

CREATE INDEX idx_sms_conversations_is_user ON platform.sms_conversations USING btree (is_user);

CREATE INDEX idx_sms_conversations_name ON platform.sms_conversations USING btree (name);

CREATE INDEX idx_sms_conversations_phone ON platform.sms_conversations USING btree (phone_number);

CREATE INDEX idx_sms_conversations_timestamp ON platform.sms_conversations USING btree (last_message_timestamp DESC);

CREATE INDEX idx_sms_conversations_unanswered ON platform.sms_conversations USING btree (last_message_outbound, last_message_timestamp DESC);

CREATE INDEX idx_sms_conversations_user_id ON platform.sms_conversations USING btree (user_id) WHERE (user_id IS NOT NULL);

CREATE UNIQUE INDEX sms_conversations_phone_number_key ON platform.sms_conversations USING btree (phone_number);

CREATE UNIQUE INDEX sms_conversations_pkey ON platform.sms_conversations USING btree (id);

alter table "platform"."sms_conversations" add constraint "sms_conversations_pkey" PRIMARY KEY using index "sms_conversations_pkey";

alter table "platform"."sms_conversations" add constraint "sms_conversations_phone_number_key" UNIQUE using index "sms_conversations_phone_number_key";


