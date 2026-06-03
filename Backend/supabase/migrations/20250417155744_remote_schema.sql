alter table "comms"."sms_blocked" add column "blocked_on" timestamp with time zone not null default now();


