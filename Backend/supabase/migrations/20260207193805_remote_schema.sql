drop policy "Admin or Users can read own peer messages" on "comms"."peer_messages";


  create policy "Admin or Users can read own peer messages"
  on "comms"."peer_messages"
  as permissive
  for select
  to public
using (((user_id = ( SELECT public.get_user_id() AS get_user_id)) OR ( SELECT public.admin_check() AS admin_check)));



