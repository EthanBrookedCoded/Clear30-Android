CREATE OR REPLACE FUNCTION community.create_post_with_tags(
  p_title TEXT,
  p_content_type TEXT,
  p_user_id TEXT,
  p_body TEXT DEFAULT NULL,
  p_video_url TEXT DEFAULT NULL,
  p_thumbnail_url TEXT DEFAULT NULL,
  p_tags TEXT[] DEFAULT '{}'::TEXT[],
  p_journal_entry_id INT DEFAULT NULL
)
RETURNS UUID AS $$
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
    thumbnail_url,
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
    p_thumbnail_url,
    p_user_id,
    false,
    false,
    NOW(),
    NULL
  ) RETURNING id INTO v_post_id;

  -- Log successful post creation
  RAISE LOG 'Successfully created post with ID: %', v_post_id;

  -- Update journal entry with the new post ID if provided
  IF p_journal_entry_id IS NOT NULL THEN
    UPDATE journal.journal_entries
      SET community_post_id = v_post_id
      WHERE id = p_journal_entry_id AND user_id = p_user_id;
  END IF;

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
$$ LANGUAGE plpgsql SECURITY DEFINER;