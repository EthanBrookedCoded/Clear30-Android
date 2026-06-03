alter table "community"."posts" add column "thumbnail_url" text;


CREATE OR REPLACE FUNCTION community.create_post_with_tags(
  p_title TEXT,
  p_content_type TEXT,
  p_body TEXT,
  p_video_url TEXT,
  p_thumbnail_url TEXT, -- New parameter added
  p_user_id TEXT,
  p_tags TEXT[]
) RETURNS UUID 
  SECURITY DEFINER
  AS $$
DECLARE
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
    thumbnail_url, -- New column added
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
    p_thumbnail_url, -- New parameter included
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
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION community.get_filtered_posts(start_range integer, end_range integer, tag_ids uuid[] DEFAULT NULL::uuid[], only_my_posts boolean DEFAULT false, include_pinned boolean DEFAULT true)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'community', 'public'
AS $function$DECLARE
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
        AND (include_pinned OR NOT p.is_pinned)
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
END;$function$
;
