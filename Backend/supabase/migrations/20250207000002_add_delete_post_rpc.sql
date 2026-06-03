-- Create deleted_posts_log table first
CREATE TABLE IF NOT EXISTS community.deleted_posts_log (
    id uuid NOT NULL,
    user_id text NOT NULL,
    title text NOT NULL,
    deleted_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_by_user_id text REFERENCES public.users(id),
    deleted_by_admin boolean DEFAULT false,
    metadata jsonb -- Store additional info if needed
);

-- Add indexes to optimize queries
CREATE INDEX IF NOT EXISTS idx_comments_parent_comment_id 
ON community.comments(parent_comment_id);

CREATE INDEX IF NOT EXISTS idx_posts_user_id 
ON community.posts(user_id);

-- Add RPC function for post deletion
CREATE OR REPLACE FUNCTION community.delete_post(post_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = community, public
AS $$
DECLARE
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

    -- Get the user_id from public.users using auth_id
    SELECT u.id, EXISTS(SELECT 1 FROM public.admins a WHERE a.auth_id = u.auth_id)
    INTO v_user_id, v_is_admin
    FROM public.users u
    WHERE u.auth_id = v_auth_id;

    -- If no user found in public.users, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User profile not found. Auth ID: %', v_auth_id;
    END IF;

    -- Get the post's user_id
    SELECT user_id INTO v_post_user_id
    FROM community.posts
    WHERE id = post_id;

    -- Check if post exists
    IF v_post_user_id IS NULL THEN
        RAISE EXCEPTION 'Post not found';
    END IF;

    -- Check if user is the post owner or an admin
    IF v_post_user_id != v_user_id AND NOT v_is_admin THEN
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

    -- Finally delete the post itself
    DELETE FROM community.posts p
    WHERE p.id = delete_post.post_id;

    RETURN true;
END;
$$;

-- Add comment for the function
COMMENT ON FUNCTION community.delete_post IS 'Deletes a post and all its related data (comments, reactions, tags, etc.). Only the post owner or admin can delete posts.';

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION community.delete_post TO authenticated;

-- Enable RLS on deleted_posts_log
ALTER TABLE community.deleted_posts_log ENABLE ROW LEVEL SECURITY;

-- Add policy for deleted_posts_log
CREATE POLICY "Only admins can view deleted posts log"
    ON community.deleted_posts_log
    FOR ALL
    TO authenticated
    USING (public.admin_check()); 