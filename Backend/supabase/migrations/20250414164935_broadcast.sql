create sequence "comms"."sms_broadcasts_id_seq";

create table "comms"."sms_broadcasts" (
    "id" bigint not null default nextval('comms.sms_broadcasts_id_seq'::regclass),
    "message" text not null,
    "is_canceled" boolean default false,
    "created_at" timestamp with time zone not null default now(),
    "batch_size" integer default 100,
    "batch_interval" integer default 2,
    "rest_period" integer default 300
);


alter table "comms"."sms_broadcasts" enable row level security;

alter table "comms"."sms_messages" add column "broadcast_id" bigint;

alter sequence "comms"."sms_broadcasts_id_seq" owned by "comms"."sms_broadcasts"."id";

CREATE INDEX idx_sms_messages_broadcast_id ON comms.sms_messages USING btree (broadcast_id);

CREATE UNIQUE INDEX sms_broadcasts_pkey ON comms.sms_broadcasts USING btree (id);

alter table "comms"."sms_broadcasts" add constraint "sms_broadcasts_pkey" PRIMARY KEY using index "sms_broadcasts_pkey";

alter table "comms"."sms_messages" add constraint "sms_messages_broadcast_id_fkey" FOREIGN KEY (broadcast_id) REFERENCES comms.sms_broadcasts(id) not valid;

alter table "comms"."sms_messages" validate constraint "sms_messages_broadcast_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.cancel_sms_broadcast(p_broadcast_id bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_success BOOLEAN := FALSE;
BEGIN
    -- Update the broadcast status
    UPDATE comms.sms_broadcasts
    SET is_canceled = TRUE
    WHERE id = p_broadcast_id;
    
    IF FOUND THEN
        -- Cancel all unsent SMS messages related to this broadcast
        UPDATE comms.sms_messages
        SET canceled = TRUE
        WHERE broadcast_id = p_broadcast_id
        AND sent_at IS NULL;
        
        v_success := TRUE;
    END IF;
    
    RETURN v_success;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.create_sms_broadcast(p_message text, p_user_ids text[], p_batch_size integer DEFAULT 100, p_batch_interval integer DEFAULT 2, p_rest_period integer DEFAULT 300)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_broadcast_id BIGINT;
    v_user_id TEXT;
    v_user_phone TEXT;
    v_next_schedule_time TIMESTAMPTZ;
    v_now TIMESTAMPTZ := NOW();
    v_message_count INTEGER := 0;
BEGIN
    -- Insert the broadcast
    INSERT INTO comms.sms_broadcasts (
        message,
        batch_size,
        batch_interval,
        rest_period
    ) VALUES (
        p_message,
        p_batch_size,
        p_batch_interval,
        p_rest_period
    ) RETURNING id INTO v_broadcast_id;
    
    -- Initialize scheduling time
    v_next_schedule_time := v_now;
    
    -- Process all recipients and create SMS messages immediately
    FOREACH v_user_id IN ARRAY p_user_ids
    LOOP
        -- Get user's phone number
        SELECT phone_number INTO v_user_phone
        FROM public.users
        WHERE id = v_user_id;
        
        -- Only process if user has a valid phone number
        IF v_user_phone IS NOT NULL AND v_user_phone != '' THEN
            -- Calculate the next scheduled time based on batch interval and rest periods
            -- Every batch_size messages, add a rest_period to the schedule time
            IF (v_message_count > 0) AND (v_message_count % p_batch_size) = 0 THEN
                v_next_schedule_time := v_next_schedule_time + 
                                       (p_rest_period * INTERVAL '1 second');
            ELSE
                v_next_schedule_time := v_next_schedule_time + 
                                       (p_batch_interval * INTERVAL '1 second');
            END IF;
            
            -- Insert the SMS message directly into the sms_messages table
            INSERT INTO comms.sms_messages (
                user_id,
                phone_number,
                text,
                outbound,
                scheduled_for,
                created_at,
                canceled,
                broadcast_id
            ) VALUES (
                v_user_id,
                v_user_phone,
                p_message,
                TRUE,
                v_next_schedule_time,
                v_now,
                FALSE,
                v_broadcast_id
            );
            
            v_message_count := v_message_count + 1;
        END IF;
    END LOOP;
    
    RETURN v_broadcast_id;
END;
$function$
;

create or replace view "comms"."sms_broadcast_summary" as  WITH message_stats AS (
         SELECT sms_messages.broadcast_id,
            count(*) AS total_messages,
            sum(
                CASE
                    WHEN (sms_messages.canceled = true) THEN 1
                    ELSE 0
                END) AS canceled_count,
            sum(
                CASE
                    WHEN ((sms_messages.sent_at IS NOT NULL) AND (sms_messages.canceled = false)) THEN 1
                    ELSE 0
                END) AS sent_count,
            sum(
                CASE
                    WHEN ((sms_messages.sent_at IS NULL) AND (sms_messages.canceled = false)) THEN 1
                    ELSE 0
                END) AS pending_count
           FROM comms.sms_messages
          WHERE (sms_messages.broadcast_id IS NOT NULL)
          GROUP BY sms_messages.broadcast_id
        ), status_stats AS (
         SELECT m.broadcast_id,
            s.status,
            count(*) AS status_count
           FROM (comms.sms_statuses s
             JOIN comms.sms_messages m ON ((s.message_id = m.id)))
          WHERE (m.broadcast_id IS NOT NULL)
          GROUP BY m.broadcast_id, s.status
        ), status_pivot AS (
         SELECT status_stats.broadcast_id,
            sum(
                CASE
                    WHEN (status_stats.status = 'delivered'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS delivered_count,
            sum(
                CASE
                    WHEN (status_stats.status = 'undelivered'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS undelivered_count,
            sum(
                CASE
                    WHEN (status_stats.status = 'failed'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS failed_count,
            sum(
                CASE
                    WHEN (status_stats.status = 'queued'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS queued_count,
            sum(
                CASE
                    WHEN (status_stats.status = 'sent'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS sent_status_count,
            sum(
                CASE
                    WHEN (status_stats.status = 'accepted'::text) THEN status_stats.status_count
                    ELSE (0)::bigint
                END) AS accepted_count
           FROM status_stats
          GROUP BY status_stats.broadcast_id
        )
 SELECT b.id AS broadcast_id,
    b.message,
    b.created_at,
    b.is_canceled,
    COALESCE(ms.total_messages, (0)::bigint) AS total_messages,
    COALESCE(ms.canceled_count, (0)::bigint) AS canceled_count,
    COALESCE(ms.sent_count, (0)::bigint) AS sent_count,
    COALESCE(ms.pending_count, (0)::bigint) AS pending_count,
    COALESCE(sp.delivered_count, (0)::numeric) AS delivered_count,
    COALESCE(sp.undelivered_count, (0)::numeric) AS undelivered_count,
    COALESCE(sp.failed_count, (0)::numeric) AS failed_count,
    COALESCE(sp.queued_count, (0)::numeric) AS queued_count,
    COALESCE(sp.sent_status_count, (0)::numeric) AS sent_status_count,
    COALESCE(sp.accepted_count, (0)::numeric) AS accepted_count,
        CASE
            WHEN b.is_canceled THEN 'Canceled'::text
            WHEN ((ms.pending_count = 0) AND (ms.total_messages > 0)) THEN 'Completed'::text
            WHEN (ms.sent_count > 0) THEN 'In Progress'::text
            ELSE 'Pending'::text
        END AS status
   FROM ((comms.sms_broadcasts b
     LEFT JOIN message_stats ms ON ((b.id = ms.broadcast_id)))
     LEFT JOIN status_pivot sp ON ((b.id = sp.broadcast_id)))
  ORDER BY b.created_at DESC;


create policy "Disable all access"
on "comms"."sms_broadcasts"
as permissive
for all
to public
using (false);



