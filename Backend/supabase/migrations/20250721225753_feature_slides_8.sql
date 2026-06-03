set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_feature_idea_score()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare
    fid bigint;
begin
    if (TG_OP = 'DELETE') then
        fid := old.feature_idea_id;
    else
        fid := new.feature_idea_id;
    end if;

    update comms.feature_ideas
    set score = (
        select coalesce(sum(vote), 0)
        from comms.feature_idea_votes
        where feature_idea_id = fid
    )
    where id = fid;

    return null;
end;
$function$
;

create policy "SELECT"
on "comms"."feature_idea_votes"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));



