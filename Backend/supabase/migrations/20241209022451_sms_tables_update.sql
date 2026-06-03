alter table "comms"."sms_messages" alter column "twilio_id" drop not null;

alter table "comms"."sms_statuses" add column "timestamp" timestamp with time zone not null default now();


