-- Create RPC function to fetch program messages by their IDs

CREATE OR REPLACE FUNCTION programs.fetch_messages_by_ids(message_ids INTEGER[])
RETURNS TABLE (
    id INTEGER,
    day INTEGER,
    stage INTEGER,
    title TEXT,
    subtitle TEXT,
    body TEXT,
    page_info JSONB,
    meditation JSONB,
    resources JSONB,
    claire_prompts JSONB,
    journal_prompts JSONB,
    notification_title TEXT,
    notification_body TEXT,
    thumbnail_url TEXT,
    video_url TEXT,
    instagram_videos JSONB,
    carousel_images JSONB,
    question_id TEXT,
    question_response TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        m.id,
        m.day,
        m.stage,
        m.title,
        m.subtitle,
        m.body,
        m.page_info,
        m.meditation,
        m.resources,
        m.claire_prompts,
        m.journal_prompts,
        m.notification_title,
        m.notification_body,
        m.thumbnail_url,
        m.video_url,
        m.instagram_videos,
        m.carousel_images,
        m.question_id,
        m.question_response
    FROM programs.program_messages m
    WHERE m.id = ANY(message_ids);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION programs.fetch_messages_by_ids TO authenticated;

