alter table "public"."notifications" drop constraint "notifications_user_id_fkey";

alter table "public"."notifications" add constraint "notifications_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "public"."notifications" validate constraint "notifications_user_id_fkey";


