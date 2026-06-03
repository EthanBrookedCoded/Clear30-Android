create policy "Users can update for upvote downvote"
on "comms"."feature_ideas"
as permissive
for update
to public
using (( SELECT (get_user_id() IS NOT NULL)))
with check ((user_id = ( SELECT feature_ideas_1.user_id
   FROM comms.feature_ideas feature_ideas_1
  WHERE (feature_ideas_1.id = feature_ideas_1.id))));



