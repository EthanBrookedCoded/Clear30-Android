drop policy "Service role can manage peer conversations" on "comms"."peer_conversations";

drop policy "Users can read own peer conversation" on "comms"."peer_conversations";

drop policy "Service role can update peer messages" on "comms"."peer_messages";

drop policy "Users can insert own peer messages" on "comms"."peer_messages";

drop policy "Users can read own peer messages" on "comms"."peer_messages";


  create policy "Admin check"
  on "comms"."peer_conversations"
  as permissive
  for select
  to public
using (( SELECT public.admin_check() AS admin_check));



  create policy "users can update own messages"
  on "comms"."peer_messages"
  as permissive
  for update
  to public
using ((user_id = ( SELECT public.get_user_id() AS get_user_id)))
with check ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



  create policy "Users can insert own peer messages"
  on "comms"."peer_messages"
  as permissive
  for insert
  to public
with check ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



  create policy "Users can read own peer messages"
  on "comms"."peer_messages"
  as permissive
  for select
  to public
using ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



