drop policy "Users can delete own vote" on "comms"."feature_idea_votes";

drop policy "Users can update own vote" on "comms"."feature_idea_votes";

drop policy "Users can vote for self" on "comms"."feature_idea_votes";

grant delete on table "comms"."feature_idea_votes" to "anon";

grant insert on table "comms"."feature_idea_votes" to "anon";

grant references on table "comms"."feature_idea_votes" to "anon";

grant select on table "comms"."feature_idea_votes" to "anon";

grant trigger on table "comms"."feature_idea_votes" to "anon";

grant truncate on table "comms"."feature_idea_votes" to "anon";

grant update on table "comms"."feature_idea_votes" to "anon";

grant delete on table "comms"."feature_idea_votes" to "authenticated";

grant insert on table "comms"."feature_idea_votes" to "authenticated";

grant references on table "comms"."feature_idea_votes" to "authenticated";

grant select on table "comms"."feature_idea_votes" to "authenticated";

grant trigger on table "comms"."feature_idea_votes" to "authenticated";

grant truncate on table "comms"."feature_idea_votes" to "authenticated";

grant update on table "comms"."feature_idea_votes" to "authenticated";

grant delete on table "comms"."feature_idea_votes" to "service_role";

grant insert on table "comms"."feature_idea_votes" to "service_role";

grant references on table "comms"."feature_idea_votes" to "service_role";

grant select on table "comms"."feature_idea_votes" to "service_role";

grant trigger on table "comms"."feature_idea_votes" to "service_role";

grant truncate on table "comms"."feature_idea_votes" to "service_role";

grant update on table "comms"."feature_idea_votes" to "service_role";

create policy "Users can delete own vote"
on "comms"."feature_idea_votes"
as permissive
for delete
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can update own vote"
on "comms"."feature_idea_votes"
as permissive
for update
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can vote for self"
on "comms"."feature_idea_votes"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));



