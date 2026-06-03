create policy "Authed users"
on "programs"."program_assessment_responses"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));



