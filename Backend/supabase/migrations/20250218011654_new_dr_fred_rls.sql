create policy "User can get messages"
on "comms"."dr_fred"
as permissive
for select
to public
using ((user_id = ( SELECT users.id
   FROM users
  WHERE (users.auth_id = ( SELECT auth.uid() AS uid)))));



