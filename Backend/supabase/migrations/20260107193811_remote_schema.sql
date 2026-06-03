set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.notify_slack_video_testimonial()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
  v_user_name TEXT;
  v_user_email TEXT;
  v_user_phone TEXT;
  v_video_urls TEXT;
  v_timestamp TEXT;
  v_video_url_array TEXT[];
  v_section_key TEXT;
BEGIN
  -- Get user name, email and phone from public.users
  -- Match user_id to either public.users.id or public.users.auth_id
  IF NEW.user_id IS NOT NULL THEN
    SELECT 
      COALESCE(pu.name, 'N/A') as name,
      COALESCE(pu.email, 'N/A') as email,
      COALESCE(pu.phone_number, 'N/A') as phone
    INTO v_user_name, v_user_email, v_user_phone
    FROM public.users pu
    WHERE pu.id = NEW.user_id 
       OR pu.auth_id::text = NEW.user_id
    LIMIT 1;
  END IF;

  -- Default values if still null
  v_user_name := COALESCE(v_user_name, 'N/A');
  v_user_email := COALESCE(v_user_email, 'N/A');
  v_user_phone := COALESCE(v_user_phone, 'N/A');

  -- Extract video URLs from sections JSONB
  -- Build an array of "section_name: <url|View Video>" strings with Slack hyperlink format
  IF NEW.sections IS NOT NULL THEN
    SELECT COALESCE(
      array_agg(
        initcap(replace(section_key, '_', ' ')) || ': <' || (NEW.sections->>section_key) || '|View Video>'
      ), 
      ARRAY[]::TEXT[]
    )
    INTO v_video_url_array
    FROM jsonb_object_keys(NEW.sections) AS section_key;
    
    -- Join array into formatted text with newlines
    v_video_urls := array_to_string(v_video_url_array, chr(10));
  ELSE
    v_video_urls := 'No videos';
  END IF;

  -- Format timestamp
  v_timestamp := to_char(NEW.created_at, 'MM/DD/YYYY, HH12:MI:SS AM');

  -- Insert into slack_notifications
  INSERT INTO comms.slack_notifications (
    channel_type,
    title,
    message,
    metadata,
    status,
    priority
  ) VALUES (
    'testimonials',
    'New Video Testimonial Submitted',
    'New video testimonial submitted by ' || COALESCE(v_user_name, NEW.user_id, 'Unknown User'),
    jsonb_build_object(
      'testimonial_id', NEW.id,
      'user_id', NEW.user_id,
      'user_name', v_user_name,
      'user_email', v_user_email,
      'user_phone', v_user_phone,
      'video_urls', v_video_urls,
      'payment_info', NEW.payment_info,
      'submitted_at', v_timestamp,
      'source', COALESCE(NEW.source, 'N/A'),
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '🎥 New Video Testimonial'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Name:* ' || v_user_name
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*User ID:* ' || COALESCE(NEW.user_id, 'N/A')
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Email:* ' || v_user_email
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Phone:* ' || v_user_phone
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Submitted:* ' || v_timestamp
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Source:* ' || COALESCE(NEW.source, 'N/A')
            )
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Video URLs:*' || chr(10) || COALESCE(v_video_urls, 'No videos')
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Payment Info:*' || chr(10) || COALESCE(NEW.payment_info, 'N/A')
          )
        ),
        jsonb_build_object(
          'type', 'actions',
          'elements', jsonb_build_array(
            jsonb_build_object(
              'type', 'button',
              'text', jsonb_build_object(
                'type', 'plain_text',
                'text', 'Review in Panel'
              ),
              'url', 'https://clear30.org/panel'
            )
          )
        )
      )
    ),
    'pending',
    'normal'
  );

  RETURN NEW;
END;
$function$
;

CREATE TRIGGER video_testimonials_slack_notification AFTER INSERT ON comms.video_testimonials FOR EACH ROW EXECUTE FUNCTION comms.notify_slack_video_testimonial();


