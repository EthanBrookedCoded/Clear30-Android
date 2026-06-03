drop policy "Disable public access" on "claire"."messages";

create policy "User can access own"
on "claire"."messages"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));



