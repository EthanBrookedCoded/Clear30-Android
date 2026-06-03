drop policy "User can get messages" on "comms"."dr_fred";

create policy "User can get messages"
on "comms"."dr_fred"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));



alter table "community"."deleted_posts_log" drop constraint "deleted_posts_log_deleted_by_user_id_fkey";

alter table "community"."deleted_posts_log" add constraint "deleted_posts_log_deleted_by_user_id_fkey" FOREIGN KEY (deleted_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."deleted_posts_log" validate constraint "deleted_posts_log_deleted_by_user_id_fkey";


