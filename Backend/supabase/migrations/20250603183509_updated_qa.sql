alter table "programs"."program_messages_qa" add column "notification_body" text;

alter table "programs"."program_messages_qa" add column "notification_title" text;

alter table "programs"."program_messages_qa" add column "thumbnail_url" text;

alter table "programs"."program_messages_qa" add column "video_url" text;

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION programs.update_production_from_qa()
 RETURNS TABLE(updated_count integer, summary text)
 LANGUAGE plpgsql
AS $function$
DECLARE
    rows_updated INTEGER;
    rows_in_qa INTEGER;
BEGIN
    -- Get count of rows in QA table
    SELECT COUNT(*) INTO rows_in_qa FROM programs.program_messages_qa;
    
    -- If no rows in QA, return early
    IF rows_in_qa = 0 THEN
        RETURN QUERY SELECT 0, 'No rows found in QA table'::TEXT;
        RETURN;
    END IF;
    
    -- Perform the update (now including all columns)
    UPDATE programs.program_messages 
    SET 
        day = qa.day,
        title = qa.title,
        subtitle = qa.subtitle,
        body = qa.body,
        program = qa.program,
        question_id = qa.question_id,
        question_response = qa.question_response,
        resources = qa.resources,
        claire_prompts = qa.claire_prompts,
        journal_prompts = qa.journal_prompts,
        meditation = qa.meditation,
        page_info = qa.page_info,
        stage = qa.stage,
        guide_id = qa.guide_id,
        notification_body = qa.notification_body,
        notification_title = qa.notification_title,
        thumbnail_url = qa.thumbnail_url,
        video_url = qa.video_url
    FROM programs.program_messages_qa qa
    WHERE programs.program_messages.id = qa.id;
    
    -- Get the count of updated rows
    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    
    -- Return results
    RETURN QUERY SELECT 
        rows_updated, 
        format('Updated %s rows from %s QA records', rows_updated, rows_in_qa)::TEXT;
END;
$function$
;


