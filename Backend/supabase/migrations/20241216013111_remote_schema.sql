alter table "comms"."sms_messages" alter column "canceled" set default false;

alter table "comms"."sms_messages" alter column "canceled" set not null;


