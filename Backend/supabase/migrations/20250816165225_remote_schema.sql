set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.get_posts_by_titles(titles text[], min_comments integer DEFAULT 1, min_date text DEFAULT NULL::text, sort_by text DEFAULT 'views'::text, limit_count integer DEFAULT NULL::integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user_id text;
    v_total_count bigint;
    v_result jsonb;
BEGIN
    -- Validate sort_by parameter
    IF sort_by NOT IN ('recent', 'views', 'engagement') THEN
        RAISE EXCEPTION 'Invalid sort_by parameter. Must be one of: recent, views, engagement';
    END IF;

    -- Validate limit_count parameter
    IF limit_count IS NOT NULL AND limit_count <= 0 THEN
        RAISE EXCEPTION 'limit_count must be a positive integer or NULL';
    END IF;

    -- Get authenticated user's ID for filtering hidden posts
    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.auth_id = auth.uid();

    -- Get total count with comment count filtering and hidden posts logic
    WITH comment_counts AS (
        SELECT 
            post_id,
            COUNT(*) as count
        FROM community.comments
        GROUP BY post_id
    ),
    base_query AS (
        SELECT DISTINCT p.id
        FROM community.posts p
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND p.content_type = 'text'
        AND p.body != ''
        AND p.title = ANY(titles) -- Safe way to match against array of titles
        AND COALESCE(cc.count, 0) >= min_comments -- Apply minimum comment threshold
        AND (min_date IS NULL OR p.created_at >= min_date::timestamp) -- Filter by minimum date
    )
    SELECT COUNT(*) INTO v_total_count FROM base_query;

    -- Get posts data with engagement metrics
    WITH comment_counts AS (
        SELECT 
            post_id,
            COUNT(*) as count
        FROM community.comments
        GROUP BY post_id
    ),
    reaction_counts AS (
        SELECT
            post_id,
            COUNT(*) as count
        FROM community.reactions
        GROUP BY post_id
    ),
    filtered_posts AS (
        SELECT 
            p.*,
            COALESCE(cc.count, 0) as comments_count,
            COALESCE(rc.count, 0) as reactions_count,
            -- Calculate engagement score: views + (comments*5) + (reactions*3)
            (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3)) as engagement_score
        FROM community.posts p
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        LEFT JOIN reaction_counts rc ON rc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND p.content_type = 'text'
        AND p.body != ''
        AND p.title = ANY(titles) -- Safe way to match against array of titles
        AND COALESCE(cc.count, 0) >= min_comments
        AND (min_date IS NULL OR p.created_at >= min_date::timestamp)
        ORDER BY 
            CASE WHEN sort_by = 'views' THEN p.view_count
                 WHEN sort_by = 'engagement' THEN (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3))
                 ELSE NULL 
            END DESC NULLS LAST,
            CASE WHEN sort_by = 'recent' THEN p.created_at END DESC NULLS LAST,
            -- Always include created_at as secondary sort for consistency
            p.created_at DESC
        LIMIT CASE WHEN limit_count IS NULL THEN NULL ELSE limit_count END
    ),
    post_data AS (
        SELECT 
            p.id,
            p.user_id,
            p.title,
            p.content_type,
            p.body,
            p.video_url,
            p.thumbnail_url,
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
                            'name', t.name,
                            'type', t.type,
                            'color', t.color
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
                        'user_id', r.user_id,
                        'post_id', r.post_id
                    )
                )
                FROM community.reactions r
                WHERE r.post_id = p.id
            ) as reactions,
            p.comments_count,
            p.engagement_score
        FROM filtered_posts p
    )
    SELECT jsonb_build_object(
        'data', COALESCE(jsonb_agg(to_jsonb(post_data.*)), '[]'::jsonb),
        'total_count', v_total_count
    ) INTO v_result
    FROM post_data;

    RETURN v_result;
END;
$function$
;


