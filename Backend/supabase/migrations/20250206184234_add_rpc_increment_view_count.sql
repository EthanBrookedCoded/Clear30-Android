set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.increment_view_count(post_id uuid)
 RETURNS void
 SECURITY DEFINER
 LANGUAGE plpgsql
AS $function$
BEGIN
    UPDATE community.posts 
    SET view_count = view_count + 1 
    WHERE id = post_id;
END;
$function$
;


