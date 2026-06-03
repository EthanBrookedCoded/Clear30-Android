drop policy "Allow select for authed" on "schools"."schools";

drop function if exists "schools"."get_tracking_link_stats"(p_school_id text);

drop function if exists "schools"."get_tracking_link_stats_trend"(p_school_id text, p_interval text);


  create policy "Allow select for anyone"
  on "schools"."schools"
  as permissive
  for select
  to public
using ((show_in_leaderboard = true));



