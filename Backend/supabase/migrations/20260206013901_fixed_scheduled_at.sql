alter table "comms"."peer_messages" alter column "scheduled_for" set default 'now()'::timestamp with time zone;

alter table "comms"."peer_messages" alter column "scheduled_for" set not null;

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


