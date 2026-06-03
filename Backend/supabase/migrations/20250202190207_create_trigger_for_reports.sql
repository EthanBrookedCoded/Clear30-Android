alter table "community"."posts" add column "is_flagged_by_llm" boolean default false;

alter table "community"."reported_posts" drop column "is_flagged_by_llm";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.set_post_visibility()
 RETURNS trigger
 SECURITY DEFINER
 LANGUAGE plpgsql
AS $function$BEGIN
    -- Count reported entries for the same post_id
    IF (SELECT COUNT(*) FROM reported_posts WHERE post_id = NEW.post_id) > 3 THEN
        -- Update the post to set is_hidden to TRUE
        UPDATE posts SET is_hidden = TRUE WHERE post_id = NEW.post_id;
    END IF;
    RETURN NEW;
END;$function$
;

CREATE TRIGGER reported_posts_trigger AFTER INSERT ON community.reported_posts FOR EACH ROW EXECUTE FUNCTION community.set_post_visibility();


