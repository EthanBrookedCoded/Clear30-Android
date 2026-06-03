-- Drop the old function
DROP FUNCTION IF EXISTS programs.fetch_messages_by_ids(INTEGER[]);

-- Create updated RPC function to fetch program messages with their stages
CREATE OR REPLACE FUNCTION programs.fetch_messages_by_ids(message_ids INTEGER[])
RETURNS TABLE (
    id INTEGER,
    day INTEGER,
    stage TEXT,
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
    question_response TEXT,
    -- Stage data
    stage_title TEXT,
    stage_subtitle TEXT,
    stage_body TEXT,
    stage_color1 TEXT,
    stage_color2 TEXT,
    stage_fred_experience TEXT
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
        m.question_response,
        -- Join with stages table
        s.title as stage_title,
        s.subtitle as stage_subtitle,
        s.body as stage_body,
        s.color1 as stage_color1,
        s.color2 as stage_color2,
        s.fred_experience as stage_fred_experience
    FROM programs.program_messages m
    LEFT JOIN programs.program_stages s ON m.stage = s.stage
    WHERE m.id = ANY(message_ids);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION programs.fetch_messages_by_ids(INTEGER[]) TO authenticated;

