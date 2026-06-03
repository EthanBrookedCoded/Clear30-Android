drop policy "Users can update for upvote downvote" on "comms"."feature_ideas";

create policy "Users can update for upvote downvote"
on "comms"."feature_ideas"
as permissive
for update
to public
using (( SELECT (get_user_id() IS NOT NULL)))
with check (true);



