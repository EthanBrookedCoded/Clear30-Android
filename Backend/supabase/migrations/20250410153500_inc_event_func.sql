set check_function_bodies = off;

CREATE OR REPLACE FUNCTION events.increment_waiting_count(event_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    UPDATE events.events
    SET num_waiting = num_waiting + 1
    WHERE id = event_id;
END;
$function$
;


