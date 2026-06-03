CREATE OR REPLACE FUNCTION community.get_post_by_id(p_post_id uuid)
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
AS $$
    SELECT jsonb_build_object(
        'id', p.id,
        'user_id', p.user_id,
        'title', p.title,
        'content_type', p.content_type,
        'body', p.body,
        'video_url', p.video_url,
        'thumbnail_url', p.thumbnail_url,
        'view_count', p.view_count,
        'is_pinned', p.is_pinned,
        'is_hidden', p.is_hidden,
        'is_flagged_by_llm', p.is_flagged_by_llm,
        'created_at', p.created_at,
        'updated_at', p.updated_at,
        'post_tags', (
            SELECT COALESCE(jsonb_agg(jsonb_build_object(
                'tag', jsonb_build_object(
                    'id', t.id, 'name', t.name, 'type', t.type, 'color', t.color
                )
            )), '[]'::jsonb)
            FROM community.post_tags pt
            JOIN community.tags t ON t.id = pt.tag_id
            WHERE pt.post_id = p.id
        ),
        'reaction_counts', (
            SELECT COALESCE(jsonb_agg(jsonb_build_object('emoji', emoji, 'count', cnt)), '[]'::jsonb)
            FROM (
                SELECT emoji, COUNT(*) AS cnt
                FROM community.reactions
                WHERE post_id = p.id
                GROUP BY emoji
            ) rc
        ),
        'comments', (
            SELECT COALESCE(jsonb_agg(row_to_json(c.*)), '[]'::jsonb)
            FROM community.comments c
            WHERE c.post_id = p.id
        )
    )
    FROM community.posts p
    WHERE p.id = p_post_id;
$$;
