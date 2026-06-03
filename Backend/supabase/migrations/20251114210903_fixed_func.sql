drop function if exists "programs"."fetch_messages_by_ids"(message_ids integer[]);

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION programs.fetch_messages_by_ids(message_ids bigint[])
 RETURNS TABLE(id bigint, day integer, stage text, title text, subtitle text, body text, page_info jsonb, meditation jsonb, resources jsonb, claire_prompts jsonb, journal_prompts jsonb, notification_title text, notification_body text, thumbnail_url text, video_url text, instagram_videos jsonb, carousel_images jsonb, question_id text, question_response text, stage_title text, stage_subtitle text, stage_body text, stage_color1 text, stage_color2 text, stage_fred_experience text)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;


