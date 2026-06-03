drop policy "Authenticated users can insert creators" on "marketing"."creators";

drop policy "Users can update creators" on "marketing"."creators";

drop policy "Users can view creators" on "marketing"."creators";


  create policy "Authenticated users can insert creators"
  on "marketing"."creators"
  as permissive
  for insert
  to authenticated
with check ((added_by_profile_id = ( SELECT marketing.get_profile_id() AS get_profile_id)));



  create policy "Users can update creators"
  on "marketing"."creators"
  as permissive
  for update
  to authenticated
using (( SELECT (marketing.get_user_role() IS NOT NULL)))
with check (( SELECT (marketing.get_user_role() IS NOT NULL)));



  create policy "Users can view creators"
  on "marketing"."creators"
  as permissive
  for select
  to authenticated
using (((deleted_at IS NULL) AND (( SELECT marketing.get_user_role() AS get_user_role) IS NOT NULL)));



