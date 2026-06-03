drop policy "Use is authed" on "events"."event_pop_ups";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION events.get_active_event_pop_ups()
 RETURNS TABLE(id bigint, event text, start_date date, end_date date, text text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'events', 'public'
AS $function$
BEGIN
  RETURN QUERY
    SELECT 
      ep.id,
      ep.event,
      ep.start_date,
      ep.end_date,
      ep.text
    FROM 
      events.event_pop_ups ep
    WHERE 
      CURRENT_DATE BETWEEN ep.start_date AND ep.end_date
      AND get_user_id()::text = ANY(ep.user_ids);
END;
$function$
;

create policy "Disable all access"
on "events"."event_pop_ups"
as permissive
for all
to public
using (false);



