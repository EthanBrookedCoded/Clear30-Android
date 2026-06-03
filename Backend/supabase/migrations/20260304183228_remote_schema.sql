drop policy "Allow select for anyone" on "schools"."schools";


  create policy "Allow select for anyone"
  on "schools"."schools"
  as permissive
  for select
  to public
using (((show_in_leaderboard = true) OR ( SELECT schools.is_platform_admin() AS is_platform_admin) OR ( SELECT schools.is_portal_admin() AS is_portal_admin)));



