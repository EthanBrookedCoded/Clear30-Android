
  create policy "Users can select for self"
  on "guardian"."code_users"
  as permissive
  for select
  to public
using ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



