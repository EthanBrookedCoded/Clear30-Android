
  create policy "Disable all access"
  on "public"."user_sessions"
  as permissive
  for all
  to public
using (false);



