drop policy "Definitions are viewable by authenticated users" on "achievements"."definitions";

drop policy "Rarities are viewable by authenticated users" on "achievements"."rarities";

drop policy "Access to authed (& active)" on "achievements"."stats";

create policy "Definitions are viewable by authenticated users"
on "achievements"."definitions"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));


create policy "Rarities are viewable by authenticated users"
on "achievements"."rarities"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));


create policy "Access to authed (& active)"
on "achievements"."stats"
as permissive
for select
to public
using (((( SELECT auth.uid() AS uid) IS NOT NULL) AND (active IS TRUE)));



drop policy "Admin only" on "comms"."dr_fred";

drop policy "User can get messages" on "comms"."dr_fred";

create policy "dr_fred_delete"
on "comms"."dr_fred"
as permissive
for delete
to public
using (( SELECT admin_check() AS admin_check));


create policy "dr_fred_insert"
on "comms"."dr_fred"
as permissive
for insert
to public
with check (( SELECT admin_check() AS admin_check));


create policy "dr_fred_select"
on "comms"."dr_fred"
as permissive
for select
to public
using ((( SELECT admin_check() AS admin_check) OR (user_id = ( SELECT get_user_id() AS get_user_id))));


create policy "dr_fred_update"
on "comms"."dr_fred"
as permissive
for update
to public
using (( SELECT admin_check() AS admin_check));



drop policy "Admin all" on "community"."reported_posts";

drop policy "Users can insert for self" on "community"."reported_posts";

drop policy "Users can select for self" on "community"."reported_posts";

drop policy "Admin all" on "community"."tags";

drop policy "Public select" on "community"."tags";

create policy "reported_posts_delete"
on "community"."reported_posts"
as permissive
for delete
to public
using (( SELECT admin_check() AS admin_check));


create policy "reported_posts_insert"
on "community"."reported_posts"
as permissive
for insert
to public
with check ((( SELECT admin_check() AS admin_check) OR (user_id = ( SELECT get_user_id() AS get_user_id))));


create policy "reported_posts_select"
on "community"."reported_posts"
as permissive
for select
to public
using ((( SELECT admin_check() AS admin_check) OR (user_id = ( SELECT get_user_id() AS get_user_id))));


create policy "reported_posts_update"
on "community"."reported_posts"
as permissive
for update
to public
using (( SELECT admin_check() AS admin_check));


create policy "tags_delete"
on "community"."tags"
as permissive
for delete
to public
using (( SELECT admin_check() AS admin_check));


create policy "tags_insert"
on "community"."tags"
as permissive
for insert
to public
with check (( SELECT admin_check() AS admin_check));


create policy "tags_select"
on "community"."tags"
as permissive
for select
to public
using (true);


create policy "tags_update"
on "community"."tags"
as permissive
for update
to public
using (( SELECT admin_check() AS admin_check));



drop policy "Access to authed" on "library"."push_check_in";

drop policy "Access to authed" on "library"."sms_day";

create policy "Access to authed"
on "library"."push_check_in"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));


create policy "Access to authed"
on "library"."sms_day"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));



drop policy "Admin only" on "payment"."domain_allowlist";

drop policy "Disable all access" on "payment"."domain_allowlist";

create policy "domain_allowlist_admin"
on "payment"."domain_allowlist"
as permissive
for all
to public
using (( SELECT admin_check() AS admin_check))
with check (( SELECT admin_check() AS admin_check));



drop policy "Admin only" on "programs"."program_assessment_responses";

drop policy "Authed users" on "programs"."program_assessment_responses";

create policy "assessment_responses_delete"
on "programs"."program_assessment_responses"
as permissive
for delete
to public
using (( SELECT admin_check() AS admin_check));


create policy "assessment_responses_insert"
on "programs"."program_assessment_responses"
as permissive
for insert
to public
with check ((( SELECT admin_check() AS admin_check) OR (user_id = ( SELECT get_user_id() AS get_user_id))));


create policy "assessment_responses_select"
on "programs"."program_assessment_responses"
as permissive
for select
to public
using ((( SELECT admin_check() AS admin_check) OR (user_id = ( SELECT get_user_id() AS get_user_id))));


create policy "assessment_responses_update"
on "programs"."program_assessment_responses"
as permissive
for update
to public
using (( SELECT admin_check() AS admin_check));



