CREATE OR REPLACE FUNCTION community.delete_comment(comment_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'community', 'public'
AS $function$
DECLARE
    v_user_id text;
    v_comment_user_id text;
    v_is_admin boolean;
    v_auth_id uuid;
BEGIN
    v_auth_id := auth.uid();
    IF v_auth_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT EXISTS(SELECT 1 FROM platform.admins a WHERE a.auth_id = v_auth_id)
    INTO v_is_admin;

    IF NOT v_is_admin THEN
        v_user_id := public.get_user_id();
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'User profile not found';
        END IF;
    END IF;

    SELECT user_id INTO v_comment_user_id
    FROM community.comments WHERE id = delete_comment.comment_id;

    IF v_comment_user_id IS NULL THEN
        RAISE EXCEPTION 'Comment not found';
    END IF;

    IF NOT v_is_admin AND v_comment_user_id != v_user_id THEN
        RAISE EXCEPTION 'Only the comment owner or admin can delete the comment';
    END IF;

    -- Delete child comments (replies) recursively
    WITH RECURSIVE comment_tree AS (
        SELECT c.id FROM community.comments c WHERE c.parent_comment_id = delete_comment.comment_id
        UNION ALL
        SELECT c.id FROM community.comments c INNER JOIN comment_tree ct ON c.parent_comment_id = ct.id
    )
    DELETE FROM community.comments c WHERE c.id IN (SELECT id FROM comment_tree);

    -- Delete activities about comment
    DELETE FROM community.activities a
    WHERE a.entity_type = 'comment' AND a.entity_id::text = delete_comment.comment_id::text;

    -- Delete the comment itself
    DELETE FROM community.comments c WHERE c.id = delete_comment.comment_id;

    RETURN true;
END;
$function$;
