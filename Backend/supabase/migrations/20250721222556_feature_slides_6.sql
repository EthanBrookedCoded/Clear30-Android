create sequence "comms"."feature_idea_votes_id_seq";

create table "comms"."feature_idea_votes" (
    "id" bigint not null default nextval('comms.feature_idea_votes_id_seq'::regclass),
    "feature_idea_id" bigint not null,
    "user_id" text not null,
    "vote" integer not null,
    "created_at" timestamp with time zone not null default now()
);


alter table "comms"."feature_idea_votes" enable row level security;

alter sequence "comms"."feature_idea_votes_id_seq" owned by "comms"."feature_idea_votes"."id";

CREATE UNIQUE INDEX feature_idea_votes_feature_idea_id_user_id_key ON comms.feature_idea_votes USING btree (feature_idea_id, user_id);

CREATE UNIQUE INDEX feature_idea_votes_pkey ON comms.feature_idea_votes USING btree (id);

alter table "comms"."feature_idea_votes" add constraint "feature_idea_votes_pkey" PRIMARY KEY using index "feature_idea_votes_pkey";

alter table "comms"."feature_idea_votes" add constraint "feature_idea_votes_feature_idea_id_fkey" FOREIGN KEY (feature_idea_id) REFERENCES comms.feature_ideas(id) ON DELETE CASCADE not valid;

alter table "comms"."feature_idea_votes" validate constraint "feature_idea_votes_feature_idea_id_fkey";

alter table "comms"."feature_idea_votes" add constraint "feature_idea_votes_feature_idea_id_user_id_key" UNIQUE using index "feature_idea_votes_feature_idea_id_user_id_key";

alter table "comms"."feature_idea_votes" add constraint "feature_idea_votes_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "comms"."feature_idea_votes" validate constraint "feature_idea_votes_user_id_fkey";

alter table "comms"."feature_idea_votes" add constraint "feature_idea_votes_vote_check" CHECK ((vote = ANY (ARRAY[1, '-1'::integer]))) not valid;

alter table "comms"."feature_idea_votes" validate constraint "feature_idea_votes_vote_check";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_feature_idea_score()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    update comms.feature_ideas
    set score = (
        select coalesce(sum(vote), 0)
        from comms.feature_idea_votes
        where feature_idea_id = new.feature_idea_id
    )
    where id = new.feature_idea_id;
    return null;
end;
$function$
;

create policy "Users can delete own vote"
on "comms"."feature_idea_votes"
as permissive
for delete
to public
using ((user_id = get_user_id()));


create policy "Users can update own vote"
on "comms"."feature_idea_votes"
as permissive
for update
to public
using ((user_id = get_user_id()));


create policy "Users can vote for self"
on "comms"."feature_idea_votes"
as permissive
for insert
to public
with check ((user_id = get_user_id()));


CREATE TRIGGER trg_update_feature_idea_score_delete AFTER DELETE ON comms.feature_idea_votes FOR EACH ROW EXECUTE FUNCTION comms.update_feature_idea_score();

CREATE TRIGGER trg_update_feature_idea_score_insert AFTER INSERT ON comms.feature_idea_votes FOR EACH ROW EXECUTE FUNCTION comms.update_feature_idea_score();

CREATE TRIGGER trg_update_feature_idea_score_update AFTER UPDATE ON comms.feature_idea_votes FOR EACH ROW EXECUTE FUNCTION comms.update_feature_idea_score();


