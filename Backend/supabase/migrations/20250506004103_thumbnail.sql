set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.get_filtered_posts(start_range integer, end_range integer, tag_ids uuid[] DEFAULT NULL::uuid[], only_my_posts boolean DEFAULT false, exclude_pinned boolean DEFAULT false, min_comments integer DEFAULT 0, min_date timestamp without time zone DEFAULT NULL::timestamp without time zone, sort_by text DEFAULT 'recent'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'community', 'public'
AS $function$DECLARE
    v_user_id text;
    v_total_count bigint;
    v_result jsonb;
BEGIN
    -- Validate sort_by parameter
    IF sort_by NOT IN ('recent', 'views', 'engagement') THEN
        RAISE EXCEPTION 'Invalid sort_by parameter. Must be one of: recent, views, engagement';
    END IF;

    -- Get authenticated user's ID for filtering
    -- We need this regardless of only_my_posts to check post ownership
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
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND (NOT exclude_pinned OR NOT p.is_pinned) -- Skip pinned posts if exclude_pinned is true
        AND (NOT only_my_posts OR p.user_id = v_user_id) -- Filter by author if only_my_posts is true
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        AND COALESCE(cc.count, 0) >= min_comments -- Apply minimum comment threshold
        AND (min_date IS NULL OR p.created_at >= min_date) -- Filter by minimum date
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
        SELECT DISTINCT 
            p.*,
            COALESCE(cc.count, 0) as comments_count,
            COALESCE(rc.count, 0) as reactions_count,
            -- Calculate engagement score: views + (comments*5) + (reactions*3)
            (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3)) as engagement_score,
            -- Include the sorting expressions in the SELECT list
            CASE WHEN sort_by = 'recent' AND NOT exclude_pinned THEN p.is_pinned END as sort_pinned,
            CASE WHEN sort_by = 'views' THEN p.view_count
                 WHEN sort_by = 'engagement' THEN (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3))
                 ELSE NULL 
            END as sort_metric,
            CASE WHEN sort_by = 'recent' THEN p.created_at END as sort_date
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        LEFT JOIN reaction_counts rc ON rc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND (NOT exclude_pinned OR NOT p.is_pinned)
        AND (NOT only_my_posts OR p.user_id = v_user_id)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        AND COALESCE(cc.count, 0) >= min_comments
        AND (min_date IS NULL OR p.created_at >= min_date) -- Filter by minimum date
        ORDER BY 
            sort_pinned DESC,
            sort_metric DESC NULLS LAST,
            sort_date DESC NULLS LAST,
            -- Always include created_at as secondary sort for consistency
            p.created_at DESC
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
END;$function$
;


