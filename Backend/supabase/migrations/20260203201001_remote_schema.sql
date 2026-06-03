drop policy "Disable access for all" on "schools"."schools";

drop policy "Users can insert own requests" on "schools"."leaderboard_requests";

alter table "schools"."schools" add column "show_in_leaderboard" boolean not null default true;

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION schools.get_leaderboard(p_limit integer DEFAULT 50)
 RETURNS TABLE(university_id bigint, name text, short_name text, logo_url text, request_count bigint, rank bigint)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        u.id AS university_id,
        u.name,
        u.short_name,
        u.logo_url,
        COUNT(r.id) AS request_count,
        RANK() OVER (ORDER BY COUNT(r.id) DESC) AS rank
    FROM schools.all_universities u
    INNER JOIN schools.leaderboard_requests r ON u.id = r.university_id
    GROUP BY u.id, u.name, u.short_name, u.logo_url
    ORDER BY request_count DESC
    LIMIT p_limit;
END;
$function$
;


  create policy "Users can delete own request"
  on "schools"."leaderboard_requests"
  as permissive
  for select
  to public
using ((( SELECT public.get_user_id() AS get_user_id) = user_id));



  create policy "Allow authed access"
  on "schools"."schools"
  as permissive
  for select
  to public
using ((show_in_leaderboard AND ( SELECT (public.get_user_id() IS NOT NULL))));



  create policy "Users can insert own requests"
  on "schools"."leaderboard_requests"
  as permissive
  for insert
  to public
with check ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



