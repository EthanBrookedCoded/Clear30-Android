drop policy "Disable all access" on "public"."deleted_users";


  create policy "Admin only access"
  on "public"."deleted_users"
  as permissive
  for select
  to public
using (( SELECT public.admin_check() AS admin_check));



