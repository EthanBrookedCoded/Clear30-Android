-- Main function to get filtered posts with range-based pagination
CREATE OR REPLACE FUNCTION community.get_filtered_posts(
    start_range int,                       -- Start of range (inclusive)
    end_range int,                         -- End of range (inclusive)
    tag_ids uuid[] DEFAULT NULL,           -- Array of tag IDs to filter by
    only_my_posts boolean DEFAULT false    -- Whether to show only the authenticated user's posts
)
RETURNS jsonb                              -- Return single JSON object
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = community, public
AS $$
DECLARE
    v_user_id text;
    v_total_count bigint;
    v_result jsonb;
BEGIN
    -- Get authenticated user's ID if only_my_posts is true
    IF only_my_posts THEN
        SELECT u.id INTO v_user_id
        FROM users u
        WHERE u.auth_id = auth.uid();

        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'User not found';
        END IF;
    END IF;

    -- Get total count first
    WITH base_query AS (
        SELECT DISTINCT p.id
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        WHERE NOT p.is_hidden
        AND (v_user_id IS NULL OR p.user_id = v_user_id)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
    )
    SELECT COUNT(*) INTO v_total_count FROM base_query;

    -- Get posts data and return as single JSON object
    WITH filtered_posts AS (
        SELECT DISTINCT p.*
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        WHERE NOT p.is_hidden
        AND (CASE WHEN only_my_posts THEN p.user_id = v_user_id ELSE true END)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        ORDER BY p.is_pinned DESC, p.created_at DESC
        OFFSET start_range LIMIT (end_range - start_range + 1)
    ),
    post_data AS (
        SELECT 
            p.id,
            p.user_id,
            p.title,
            p.content_type,
            p.body,
            p.video_url,
            p.is_pinned,
            p.is_hidden,
            p.is_flagged_by_llm,
            p.view_count,
            p.created_at,
            p.updated_at,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'tag', jsonb_build_object(
                            'id', t.id,
                            'name', t.name
                        )
                    )
                )
                FROM community.post_tags pt
                JOIN community.tags t ON t.id = pt.tag_id
                WHERE pt.post_id = p.id
            ) as post_tags,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'emoji', r.emoji,
                        'user_id', r.user_id
                    )
                )
                FROM community.reactions r
                WHERE r.post_id = p.id
            ) as reactions,
            (
                SELECT COUNT(*)
                FROM community.comments c
                WHERE c.post_id = p.id
            ) as comments_count
        FROM filtered_posts p
    )
    SELECT jsonb_build_object(
        'data', COALESCE(jsonb_agg(to_jsonb(post_data.*)), '[]'::jsonb),
        'total_count', v_total_count
    ) INTO v_result
    FROM post_data;

    RETURN v_result;
END;
$$;

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION community.get_filtered_posts TO anon;
GRANT EXECUTE ON FUNCTION community.get_filtered_posts TO authenticated;
GRANT EXECUTE ON FUNCTION community.get_filtered_posts TO authenticator;

-- Add helpful comment
COMMENT ON FUNCTION community.get_filtered_posts IS 'Fetches filtered posts with range-based pagination, tag filtering, and optional my-posts-only filter. Returns a single JSON object with posts data and total count.'; 