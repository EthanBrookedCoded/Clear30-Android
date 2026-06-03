set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.update_feature_idea_score()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
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


