drop function if exists "public"."send_notification_public"(user_id text, title text, body text);

alter table "public"."group_pings" alter column "to_user_id" set not null;


