
  create policy "School can read"
  on "schools"."mid_pilot_assessments"
  as permissive
  for select
  to public
using (((school_id = schools.get_portal_school_id()) OR schools.is_platform_admin()));



