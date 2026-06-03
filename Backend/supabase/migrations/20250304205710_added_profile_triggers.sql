drop trigger if exists "after_comment_created" on "community"."comments";

drop trigger if exists "after_post_created" on "community"."posts";

drop function if exists "community"."get_filtered_posts"(start_range integer, end_range integer, tag_ids uuid[], only_my_posts boolean);

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.create_profile()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
  -- Check if a profile with the user_id already exists in community.profiles
  IF NOT EXISTS (SELECT 1 FROM community.profiles WHERE id = NEW.user_id) THEN
    -- If no profile exists, insert a new one using data from public.users
    INSERT INTO community.profiles (id, name, emoji)
    SELECT id, name, emoji
    FROM public.users
    WHERE id = NEW.user_id;
  END IF;
  
  RETURN NEW;
END;$function$
;

grant delete on table "community"."activities" to "anon";

grant insert on table "community"."activities" to "anon";

grant references on table "community"."activities" to "anon";

grant trigger on table "community"."activities" to "anon";

grant truncate on table "community"."activities" to "anon";

grant update on table "community"."activities" to "anon";

grant delete on table "community"."activities" to "authenticated";

grant insert on table "community"."activities" to "authenticated";

grant references on table "community"."activities" to "authenticated";

grant trigger on table "community"."activities" to "authenticated";

grant truncate on table "community"."activities" to "authenticated";

grant delete on table "community"."activities" to "service_role";

grant insert on table "community"."activities" to "service_role";

grant references on table "community"."activities" to "service_role";

grant select on table "community"."activities" to "service_role";

grant trigger on table "community"."activities" to "service_role";

grant truncate on table "community"."activities" to "service_role";

grant update on table "community"."activities" to "service_role";

grant delete on table "community"."comments" to "service_role";

grant insert on table "community"."comments" to "service_role";

grant references on table "community"."comments" to "service_role";

grant select on table "community"."comments" to "service_role";

grant trigger on table "community"."comments" to "service_role";

grant truncate on table "community"."comments" to "service_role";

grant update on table "community"."comments" to "service_role";

grant delete on table "community"."deleted_posts_log" to "anon";

grant insert on table "community"."deleted_posts_log" to "anon";

grant references on table "community"."deleted_posts_log" to "anon";

grant select on table "community"."deleted_posts_log" to "anon";

grant trigger on table "community"."deleted_posts_log" to "anon";

grant truncate on table "community"."deleted_posts_log" to "anon";

grant update on table "community"."deleted_posts_log" to "anon";

grant delete on table "community"."deleted_posts_log" to "authenticated";

grant insert on table "community"."deleted_posts_log" to "authenticated";

grant references on table "community"."deleted_posts_log" to "authenticated";

grant select on table "community"."deleted_posts_log" to "authenticated";

grant trigger on table "community"."deleted_posts_log" to "authenticated";

grant truncate on table "community"."deleted_posts_log" to "authenticated";

grant update on table "community"."deleted_posts_log" to "authenticated";

grant delete on table "community"."deleted_posts_log" to "service_role";

grant insert on table "community"."deleted_posts_log" to "service_role";

grant references on table "community"."deleted_posts_log" to "service_role";

grant select on table "community"."deleted_posts_log" to "service_role";

grant trigger on table "community"."deleted_posts_log" to "service_role";

grant truncate on table "community"."deleted_posts_log" to "service_role";

grant update on table "community"."deleted_posts_log" to "service_role";

grant delete on table "community"."post_tags" to "service_role";

grant insert on table "community"."post_tags" to "service_role";

grant references on table "community"."post_tags" to "service_role";

grant select on table "community"."post_tags" to "service_role";

grant trigger on table "community"."post_tags" to "service_role";

grant truncate on table "community"."post_tags" to "service_role";

grant update on table "community"."post_tags" to "service_role";

grant delete on table "community"."posts" to "service_role";

grant insert on table "community"."posts" to "service_role";

grant references on table "community"."posts" to "service_role";

grant select on table "community"."posts" to "service_role";

grant trigger on table "community"."posts" to "service_role";

grant truncate on table "community"."posts" to "service_role";

grant update on table "community"."posts" to "service_role";

grant delete on table "community"."profiles" to "anon";

grant insert on table "community"."profiles" to "anon";

grant references on table "community"."profiles" to "anon";

grant select on table "community"."profiles" to "anon";

grant trigger on table "community"."profiles" to "anon";

grant truncate on table "community"."profiles" to "anon";

grant update on table "community"."profiles" to "anon";

grant delete on table "community"."profiles" to "authenticated";

grant insert on table "community"."profiles" to "authenticated";

grant references on table "community"."profiles" to "authenticated";

grant select on table "community"."profiles" to "authenticated";

grant trigger on table "community"."profiles" to "authenticated";

grant truncate on table "community"."profiles" to "authenticated";

grant update on table "community"."profiles" to "authenticated";

grant delete on table "community"."profiles" to "service_role";

grant insert on table "community"."profiles" to "service_role";

grant references on table "community"."profiles" to "service_role";

grant select on table "community"."profiles" to "service_role";

grant trigger on table "community"."profiles" to "service_role";

grant truncate on table "community"."profiles" to "service_role";

grant update on table "community"."profiles" to "service_role";

grant delete on table "community"."reactions" to "service_role";

grant insert on table "community"."reactions" to "service_role";

grant references on table "community"."reactions" to "service_role";

grant select on table "community"."reactions" to "service_role";

grant trigger on table "community"."reactions" to "service_role";

grant truncate on table "community"."reactions" to "service_role";

grant update on table "community"."reactions" to "service_role";

grant delete on table "community"."reported_posts" to "service_role";

grant insert on table "community"."reported_posts" to "service_role";

grant references on table "community"."reported_posts" to "service_role";

grant select on table "community"."reported_posts" to "service_role";

grant trigger on table "community"."reported_posts" to "service_role";

grant truncate on table "community"."reported_posts" to "service_role";

grant update on table "community"."reported_posts" to "service_role";

grant delete on table "community"."tags" to "service_role";

grant insert on table "community"."tags" to "service_role";

grant references on table "community"."tags" to "service_role";

grant select on table "community"."tags" to "service_role";

grant trigger on table "community"."tags" to "service_role";

grant truncate on table "community"."tags" to "service_role";

grant update on table "community"."tags" to "service_role";

CREATE TRIGGER after_comment_created_activity AFTER INSERT ON community.comments FOR EACH ROW EXECUTE FUNCTION community.create_comment_activity();

CREATE TRIGGER after_comment_created_profile AFTER INSERT ON community.comments FOR EACH ROW EXECUTE FUNCTION community.create_profile();

CREATE TRIGGER after_post_created_activity AFTER INSERT ON community.posts FOR EACH ROW EXECUTE FUNCTION community.create_post_activity();

CREATE TRIGGER after_post_created_profile AFTER INSERT ON community.posts FOR EACH ROW EXECUTE FUNCTION community.create_profile();


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.community_update_profile()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
  -- Check if a profile with the user id exists in community.profiles
  IF EXISTS (SELECT 1 FROM community.profiles WHERE id = NEW.id) THEN
    -- If profile exists, update the name and emoji
    UPDATE community.profiles
    SET 
      name = NEW.name,
      emoji = NEW.emoji
    WHERE id = NEW.id;
  END IF;
  
  RETURN NEW;
END;$function$
;

CREATE TRIGGER after_user_update AFTER UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION community_update_profile();


