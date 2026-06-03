drop policy "Allow select for authed" on "schools"."schools";


  create policy "Allow select for authed"
  on "schools"."schools"
  as permissive
  for select
  to public
using (((( SELECT auth.uid() AS uid) IS NOT NULL) AND (show_in_leaderboard = true)));



