drop policy "Enable all access" on "groups"."group_messages";

create policy "insert_group_messages"
on "groups"."group_messages"
as permissive
for insert
to public
with check (((group_id = groups.get_user_group()) AND (user_id = get_user_id())));


create policy "select_group_messages"
on "groups"."group_messages"
as permissive
for select
to public
using ((group_id = groups.get_user_group()));


create policy "update_group_messages"
on "groups"."group_messages"
as permissive
for update
to public
using (((user_id = get_user_id()) AND (group_id = groups.get_user_group())));

grant usage on schema groups to "anon";
grant usage on schema groups to "authenticated";
grant usage on schema groups to "service_role";


