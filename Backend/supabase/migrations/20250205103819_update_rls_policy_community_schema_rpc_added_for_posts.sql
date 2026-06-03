CREATE UNIQUE INDEX tags_name_key ON community.tags USING btree (name);

alter table "community"."tags" add constraint "tags_name_key" UNIQUE using index "tags_name_key";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.create_post_with_tags(p_title text, p_content_type text, p_body text, p_video_url text, p_user_id text, p_tags text[] DEFAULT '{}'::text[])
 RETURNS uuid
 LANGUAGE plpgsql
AS $function$DECLARE
  v_post_id uuid;
  v_tag_id uuid;
  v_tag TEXT;
BEGIN
  -- Input validation
  IF p_title IS NULL OR p_content_type IS NULL THEN
    RAISE EXCEPTION 'Title and content_type are required fields';
  END IF;

  IF p_content_type NOT IN ('text', 'video') THEN
    RAISE EXCEPTION 'Invalid content_type. Must be either text or video';
  END IF;

  IF p_content_type = 'text' AND p_body IS NULL THEN
    RAISE EXCEPTION 'Body is required for text posts';
  END IF;

  IF p_content_type = 'video' AND p_video_url IS NULL THEN
    RAISE EXCEPTION 'Video URL is required for video posts';
  END IF;

  -- Log the start of the operation
  RAISE LOG 'Creating new post with title: %, content_type: %, user_id: %', p_title, p_content_type, p_user_id;

  -- First create the post
  INSERT INTO community.posts (
    title,
    content_type,
    body,
    video_url,
    user_id,
    is_pinned,
    is_hidden,
    created_at,
    updated_at
  ) VALUES (
    p_title,
    p_content_type,
    p_body,
    p_video_url,
    p_user_id,
    false,
    false,
    NOW(),
    NULL
  ) RETURNING id INTO v_post_id;

  -- Log successful post creation
  RAISE LOG 'Successfully created post with ID: %', v_post_id;

  -- Then handle each tag
  IF array_length(p_tags, 1) > 0 THEN
    FOREACH v_tag IN ARRAY p_tags
    LOOP
      -- Log tag processing
      RAISE LOG 'Processing tag: %', v_tag;

      -- Try to find existing tag or create new one
      INSERT INTO community.tags (name, created_at)
      VALUES (v_tag, NOW())
      ON CONFLICT (name) 
      DO UPDATE SET name = EXCLUDED.name
      RETURNING id INTO v_tag_id;

      -- Create post_tag connection
      INSERT INTO community.post_tags (post_id, tag_id, created_at)
      VALUES (v_post_id, v_tag_id, NOW());

      -- Log successful tag association
      RAISE LOG 'Associated tag % (ID: %) with post %', v_tag, v_tag_id, v_post_id;
    END LOOP;
  END IF;

  RETURN v_post_id;
EXCEPTION
  WHEN others THEN
    -- Log any errors that occur
    RAISE LOG 'Error in create_post_with_tags: %', SQLERRM;
    RAISE EXCEPTION 'An error occurred while creating the post: %', SQLERRM;
END;$function$
;

grant delete on table "community"."comments" to "anon";

grant insert on table "community"."comments" to "anon";

grant references on table "community"."comments" to "anon";

grant select on table "community"."comments" to "anon";

grant trigger on table "community"."comments" to "anon";

grant truncate on table "community"."comments" to "anon";

grant update on table "community"."comments" to "anon";

grant delete on table "community"."comments" to "authenticated";

grant insert on table "community"."comments" to "authenticated";

grant references on table "community"."comments" to "authenticated";

grant select on table "community"."comments" to "authenticated";

grant trigger on table "community"."comments" to "authenticated";

grant truncate on table "community"."comments" to "authenticated";

grant update on table "community"."comments" to "authenticated";

grant delete on table "community"."post_tags" to "anon";

grant insert on table "community"."post_tags" to "anon";

grant references on table "community"."post_tags" to "anon";

grant select on table "community"."post_tags" to "anon";

grant trigger on table "community"."post_tags" to "anon";

grant truncate on table "community"."post_tags" to "anon";

grant update on table "community"."post_tags" to "anon";

grant delete on table "community"."post_tags" to "authenticated";

grant insert on table "community"."post_tags" to "authenticated";

grant references on table "community"."post_tags" to "authenticated";

grant select on table "community"."post_tags" to "authenticated";

grant trigger on table "community"."post_tags" to "authenticated";

grant truncate on table "community"."post_tags" to "authenticated";

grant update on table "community"."post_tags" to "authenticated";

grant delete on table "community"."posts" to "anon";

grant insert on table "community"."posts" to "anon";

grant references on table "community"."posts" to "anon";

grant select on table "community"."posts" to "anon";

grant trigger on table "community"."posts" to "anon";

grant truncate on table "community"."posts" to "anon";

grant update on table "community"."posts" to "anon";

grant delete on table "community"."posts" to "authenticated";

grant insert on table "community"."posts" to "authenticated";

grant references on table "community"."posts" to "authenticated";

grant select on table "community"."posts" to "authenticated";

grant trigger on table "community"."posts" to "authenticated";

grant truncate on table "community"."posts" to "authenticated";

grant update on table "community"."posts" to "authenticated";

grant delete on table "community"."reactions" to "anon";

grant insert on table "community"."reactions" to "anon";

grant references on table "community"."reactions" to "anon";

grant select on table "community"."reactions" to "anon";

grant trigger on table "community"."reactions" to "anon";

grant truncate on table "community"."reactions" to "anon";

grant update on table "community"."reactions" to "anon";

grant delete on table "community"."reactions" to "authenticated";

grant insert on table "community"."reactions" to "authenticated";

grant references on table "community"."reactions" to "authenticated";

grant select on table "community"."reactions" to "authenticated";

grant trigger on table "community"."reactions" to "authenticated";

grant truncate on table "community"."reactions" to "authenticated";

grant update on table "community"."reactions" to "authenticated";

grant delete on table "community"."reported_posts" to "anon";

grant insert on table "community"."reported_posts" to "anon";

grant references on table "community"."reported_posts" to "anon";

grant select on table "community"."reported_posts" to "anon";

grant trigger on table "community"."reported_posts" to "anon";

grant truncate on table "community"."reported_posts" to "anon";

grant update on table "community"."reported_posts" to "anon";

grant delete on table "community"."reported_posts" to "authenticated";

grant insert on table "community"."reported_posts" to "authenticated";

grant references on table "community"."reported_posts" to "authenticated";

grant select on table "community"."reported_posts" to "authenticated";

grant trigger on table "community"."reported_posts" to "authenticated";

grant truncate on table "community"."reported_posts" to "authenticated";

grant update on table "community"."reported_posts" to "authenticated";

grant delete on table "community"."tags" to "anon";

grant insert on table "community"."tags" to "anon";

grant references on table "community"."tags" to "anon";

grant select on table "community"."tags" to "anon";

grant trigger on table "community"."tags" to "anon";

grant truncate on table "community"."tags" to "anon";

grant update on table "community"."tags" to "anon";

grant delete on table "community"."tags" to "authenticated";

grant insert on table "community"."tags" to "authenticated";

grant references on table "community"."tags" to "authenticated";

grant select on table "community"."tags" to "authenticated";

grant trigger on table "community"."tags" to "authenticated";

grant truncate on table "community"."tags" to "authenticated";

grant update on table "community"."tags" to "authenticated";

create policy "comments"
on "community"."comments"
as permissive
for all
to anon
using (true);


create policy "post_tags"
on "community"."post_tags"
as permissive
for all
to anon
using (true);


create policy "Posts"
on "community"."posts"
as permissive
for all
to anon
using (true);


create policy "reactions"
on "community"."reactions"
as permissive
for all
to public
using (true);


create policy "reported_posts"
on "community"."reported_posts"
as permissive
for all
to anon
using (true);


create policy "tags"
on "community"."tags"
as permissive
for all
to anon
using (true);



