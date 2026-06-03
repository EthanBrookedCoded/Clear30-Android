alter table "programs"."program_messages" add column "notification_body" text;

alter table "programs"."program_messages" add column "notification_title" text;


alter table "public"."users" add column "notification_settings" jsonb;


