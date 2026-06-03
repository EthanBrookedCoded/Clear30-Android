drop policy "Users can delete own request" on "schools"."leaderboard_requests";

drop policy "Users can insert own requests" on "schools"."leaderboard_requests";


  create policy "Users can do anything for own requests"
  on "schools"."leaderboard_requests"
  as permissive
  for all
  to public
using ((user_id = ( SELECT public.get_user_id() AS get_user_id)))
with check ((user_id = ( SELECT public.get_user_id() AS get_user_id)));



