drop policy "Users can delete their own journal entries" on "public"."journal_entries";

drop policy "Users can insert their own journal entries" on "public"."journal_entries";

drop policy "Users can update their own journal entries" on "public"."journal_entries";

drop policy "Users can view their own journal entries" on "public"."journal_entries";

create policy "Users can delete their own journal entries"
on "public"."journal_entries"
as permissive
for delete
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can insert their own journal entries"
on "public"."journal_entries"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can update their own journal entries"
on "public"."journal_entries"
as permissive
for update
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can view their own journal entries"
on "public"."journal_entries"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));



