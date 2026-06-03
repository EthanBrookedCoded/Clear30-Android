drop trigger if exists "before_comment_deleted" on "community"."comments";

drop trigger if exists "before_post_deleted" on "community"."posts";

drop function if exists "community"."create_comment_deletion_activity"();

drop function if exists "community"."create_post_deletion_activity"();

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.create_comment_activity()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_post_author_id text;
    v_parent_comment_author_id text;
BEGIN
    -- Don't create activity if the actor would be the recipient
    IF NEW.parent_comment_id IS NULL THEN
        -- Direct comment on post
        SELECT user_id INTO v_post_author_id
        FROM community.posts
        WHERE id = NEW.post_id;

        -- Only create activity if the commenter is not the post author
        IF NEW.user_id <> v_post_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_post_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'commented'::community.activity_action
            );
        END IF;
    ELSE
        -- Reply to comment
        SELECT user_id INTO v_parent_comment_author_id
        FROM community.comments
        WHERE id = NEW.parent_comment_id;

        -- Only create activity if the replier is not the parent comment author
        IF NEW.user_id <> v_parent_comment_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_parent_comment_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'replied'::community.activity_action
            );
        END IF;
    END IF;

    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent comment creation
        RAISE WARNING 'Error creating activity for comment %: %', NEW.id, SQLERRM;
        RETURN NEW;
END;$function$
;

CREATE OR REPLACE FUNCTION community.delete_post(post_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'community', 'public'
AS $function$DECLARE
    v_auth_id uuid;
    v_user_id text;
    v_post_user_id text;
    v_is_admin boolean;
BEGIN
    -- Get the auth_id of the authenticated user
    v_auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF v_auth_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    -- First check if the user is an admin (regardless of being in public.users)
    SELECT EXISTS(SELECT 1 FROM public.admins a WHERE a.auth_id = v_auth_id) 
    INTO v_is_admin;

    -- If user is an admin, they can proceed without further user checks
    IF NOT v_is_admin THEN
        -- Non-admin users must be in the public.users table
        SELECT u.id
        INTO v_user_id
        FROM public.users u
        WHERE u.auth_id = v_auth_id;
        
        -- If not in public.users, raise an exception
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'User profile not found. Auth ID: %', v_auth_id;
        END IF;
    END IF;

    -- Get the post's user_id
    SELECT user_id INTO v_post_user_id
    FROM community.posts
    WHERE id = post_id;

    -- Check if post exists
    IF v_post_user_id IS NULL THEN
        RAISE EXCEPTION 'Post not found';
    END IF;

    -- Admin users can delete any post
    -- Non-admin users can only delete their own posts
    IF NOT v_is_admin AND v_post_user_id != v_user_id THEN
        RAISE EXCEPTION 'Only the post owner or admin can delete the post';
    END IF;

    -- Delete all reactions for the post
    DELETE FROM community.reactions r
    WHERE r.post_id = delete_post.post_id;

    -- Delete all post tags
    DELETE FROM community.post_tags pt
    WHERE pt.post_id = delete_post.post_id;

    -- Delete all reported posts entries
    DELETE FROM community.reported_posts rp
    WHERE rp.post_id = delete_post.post_id;

    -- Delete all comments and their replies using recursive CTE
    WITH RECURSIVE comment_tree AS (
        -- Base case: get all direct comments for the post
        SELECT c.id
        FROM community.comments c
        WHERE c.post_id = delete_post.post_id
        
        UNION ALL
        
        -- Recursive case: get all replies to comments
        SELECT c.id
        FROM community.comments c
        INNER JOIN comment_tree ct ON c.parent_comment_id = ct.id
    )
    DELETE FROM community.comments c
    WHERE c.id IN (SELECT id FROM comment_tree)
    OR c.post_id = delete_post.post_id;

    -- Log additional information about who deleted the post
    INSERT INTO community.deleted_posts_log (
        id, 
        user_id, 
        title, 
        metadata,
        deleted_by_user_id,
        deleted_by_admin
    )
    SELECT 
        p.id,
        p.user_id,
        p.title,
        jsonb_build_object(
            'content_type', p.content_type,
            'view_count', p.view_count,
            'is_pinned', p.is_pinned,
            'is_hidden', p.is_hidden,
            'is_flagged_by_llm', p.is_flagged_by_llm,
            'deleted_by', v_user_id,
            'was_admin_deletion', v_is_admin
        ),
        v_user_id,
        v_is_admin
    FROM community.posts p
    WHERE p.id = delete_post.post_id;

    -- Delete activies about post
    DELETE FROM community.activities a
    WHERE a.entity_type = 'post' AND a.entity_id::text = delete_post.post_id::text;


    -- Finally delete the post itself
    DELETE FROM community.posts p
    WHERE p.id = delete_post.post_id;

    RETURN true;
END;$function$
;

CREATE OR REPLACE FUNCTION community.get_activity_feed(start_range integer, end_range integer)
 RETURNS TABLE(id uuid, created_at timestamp with time zone, is_read boolean, message text, entity_type community.activity_entity_type, entity_id uuid, post_id uuid, action community.activity_action)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'community', 'public'
AS $function$DECLARE
    v_user_id text;
BEGIN
    -- Get the authenticated user's ID
    SELECT u.id INTO v_user_id
    FROM public.users u
    WHERE u.auth_id = auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    RETURN QUERY
    WITH post_titles AS (
        -- Get titles for existing
        SELECT 
            p.id AS post_id,
            p.title AS title
        FROM community.posts p
    )
    SELECT 
        a.id,
        a.created_at,
        a.is_read,
        CASE 
            -- Created something
            WHEN a.action = 'created' THEN 
                CASE 
                    -- You created post
                    WHEN a.actor_id = a.recipient_id THEN 
                        'You created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                    -- Someone else created a post
                    ELSE
                        actor.name || ' created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                END
            WHEN a.action = 'commented' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You commented on your post saying "' || c.body || '"'
                    ELSE actor.name || ' commented on your post saying "' || c.body || '"'
                END
            WHEN a.action = 'replied' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You replied to your comment saying "' || c.body || '"'
                    ELSE actor.name || ' replied to your comment saying "' || c.body || '"'
                END
        END,
        a.entity_type,
        a.entity_id,
        COALESCE(c.post_id, p.id) as post_id,
        a.action
    FROM community.activities a
    JOIN public.users actor ON actor.id = a.actor_id
    LEFT JOIN community.posts p ON 
        (a.entity_type = 'post' AND a.entity_id = p.id)
    LEFT JOIN community.comments c ON 
        (a.entity_type = 'comment' AND a.entity_id = c.id)
    WHERE a.recipient_id = v_user_id
    ORDER BY a.created_at DESC
    OFFSET start_range LIMIT (end_range - start_range + 1);
END;$function$
;


