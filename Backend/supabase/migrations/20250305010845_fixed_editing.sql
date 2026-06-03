create policy "Users can edit own posts"
on "community"."posts"
as permissive
for update
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)))
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));



