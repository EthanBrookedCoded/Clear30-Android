drop policy "Enable SELECT for authed users and admins" on "public"."users";

drop policy "Enable UPDATE for authed users" on "public"."users";

create policy "Enable SELECT for authed users and admins"
on "public"."users"
as permissive
for select
to public
using (((auth_id = ( SELECT auth.uid() AS uid)) OR admin_check()));


create policy "Enable UPDATE for authed users"
on "public"."users"
as permissive
for update
to public
using ((auth_id = ( SELECT auth.uid() AS uid)))
with check (((auth_id = ( SELECT auth.uid() AS uid)) AND (id = id)));



