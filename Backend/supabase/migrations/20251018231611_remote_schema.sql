drop policy "Users can insert their own achievements" on "achievements"."user_achievements";

drop policy "Users can update their own achievements" on "achievements"."user_achievements";

drop policy "Users can view their own achievements" on "achievements"."user_achievements";

create policy "Users can insert their own achievements"
on "achievements"."user_achievements"
as permissive
for insert
to public
with check ((( SELECT get_user_id() AS get_user_id) = user_id));


create policy "Users can update their own achievements"
on "achievements"."user_achievements"
as permissive
for update
to public
using ((( SELECT get_user_id() AS get_user_id) = user_id));


create policy "Users can view their own achievements"
on "achievements"."user_achievements"
as permissive
for select
to public
using ((( SELECT get_user_id() AS get_user_id) = user_id));



