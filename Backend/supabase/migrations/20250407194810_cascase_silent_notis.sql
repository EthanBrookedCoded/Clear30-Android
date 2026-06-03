alter table "comms"."silent_notifications" drop constraint "silent_notifications_user_id_fkey";

alter table "comms"."silent_notifications" add constraint "silent_notifications_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "comms"."silent_notifications" validate constraint "silent_notifications_user_id_fkey";


