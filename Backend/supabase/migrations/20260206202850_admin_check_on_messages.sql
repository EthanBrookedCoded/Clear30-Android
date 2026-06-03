drop policy "Users can insert own peer messages" on "comms"."peer_messages";

drop policy "Users can read own peer messages" on "comms"."peer_messages";

drop policy "users can update own messages" on "comms"."peer_messages";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.update_user_timezone(p_timezone text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  UPDATE public.users
  SET timezone = p_timezone
  WHERE auth_id = auth.uid();
END;
$function$
;


  create policy "Admin or Users can insert own peer messages"
  on "comms"."peer_messages"
  as permissive
  for insert
  to public
with check ((( SELECT public.admin_check() AS admin_check) OR (user_id = ( SELECT public.get_user_id() AS get_user_id))));



  create policy "Admin or Users can read own peer messages"
  on "comms"."peer_messages"
  as permissive
  for select
  to public
using ((( SELECT public.admin_check() AS admin_check) OR (user_id = ( SELECT public.get_user_id() AS get_user_id))));



  create policy "Admin or users can update own messages"
  on "comms"."peer_messages"
  as permissive
  for update
  to public
using ((( SELECT public.admin_check() AS admin_check) OR (user_id = ( SELECT public.get_user_id() AS get_user_id))))
with check ((( SELECT public.admin_check() AS admin_check) OR (user_id = ( SELECT public.get_user_id() AS get_user_id))));



