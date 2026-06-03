drop trigger if exists "notification_send_trigger" on "comms"."notifications";

alter table "comms"."notifications" add column "error_message" text;

alter table "comms"."notifications" add column "sent_at" timestamp with time zone;

alter table "comms"."notifications" add column "status" text not null default 'pending'::text;

CREATE INDEX idx_notifications_status_timestamp ON comms.notifications USING btree (status, "timestamp") WHERE (status = 'pending'::text);

alter table "comms"."notifications" add constraint "notifications_status_check" CHECK ((status = ANY (ARRAY['pending'::text, 'sent'::text, 'failed'::text, 'cancelled'::text]))) not valid;

alter table "comms"."notifications" validate constraint "notifications_status_check";


