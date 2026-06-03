set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.find_testimonial_candidates()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_inserted INTEGER := 0;
    v_candidates RECORD;
    v_candidate_list TEXT := '';
    v_metadata JSONB;
BEGIN
    -- Insert new candidates who meet all criteria
    INSERT INTO comms.video_testimonial_candidates (user_id, sober_days, last_check_in_at)
    SELECT
        u.id,
        comms.calculate_sober_days(u.id) as sober_days,
        comms.get_last_check_in_date(u.id) as last_check_in_at
    FROM public.users u
    WHERE
        -- Not already a candidate
        u.id NOT IN (SELECT user_id FROM comms.video_testimonial_candidates WHERE user_id IS NOT NULL)
        -- Not already submitted a testimonial
        AND u.id NOT IN (SELECT user_id FROM comms.video_testimonials WHERE user_id IS NOT NULL)
        -- Sober for > 30 days
        AND comms.calculate_sober_days(u.id) > 30
        -- Checked in within last 5 days (based on last date in day_info)
        AND comms.get_last_check_in_date(u.id) > NOW() - INTERVAL '5 days'
    ON CONFLICT (user_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    -- Send daily summary Slack notification if any new candidates found
    IF v_inserted > 0 THEN
        -- Build list of new candidates (added in this run)
        FOR v_candidates IN
            SELECT c.user_id, c.sober_days, u.name
            FROM comms.video_testimonial_candidates c
            JOIN public.users u ON u.id = c.user_id
            WHERE c.created_at > NOW() - INTERVAL '1 minute'
            ORDER BY c.sober_days DESC
            LIMIT 10
        LOOP
            v_candidate_list := v_candidate_list ||
                E'• *' || COALESCE(v_candidates.name, 'Unknown') || '* - ' ||
                v_candidates.sober_days || ' days sober' || E'\n';
        END LOOP;

        v_metadata := jsonb_build_object(
            'blocks', jsonb_build_array(
                jsonb_build_object(
                    'type', 'header',
                    'text', jsonb_build_object(
                        'type', 'plain_text',
                        'text', '🎬 Daily Testimonial Candidates Report'
                    )
                ),
                jsonb_build_object(
                    'type', 'section',
                    'text', jsonb_build_object(
                        'type', 'mrkdwn',
                        'text', '*Found ' || v_inserted || ' new candidate(s) today:*' || E'\n\n' || v_candidate_list
                    )
                ),
                jsonb_build_object(
                    'type', 'context',
                    'elements', jsonb_build_array(
                        jsonb_build_object(
                            'type', 'mrkdwn',
                            'text', 'Criteria: 30+ sober days, checked in within last 5 days, no prior submission'
                        )
                    )
                )
            )
        );

        INSERT INTO comms.slack_notifications (
            channel_type, title, message, metadata, status, priority
        ) VALUES (
            'testimonials',
            'Daily Testimonial Candidates',
            'Found ' || v_inserted || ' new testimonial candidate(s)',
            v_metadata,
            'pending',
            'normal'
        );
    END IF;

    RETURN v_inserted;
END;$function$
;

CREATE OR REPLACE FUNCTION comms.notify_slack_video_testimonial()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
  v_user_name TEXT;
  v_video_urls TEXT;
  v_timestamp TEXT;
  v_video_url_array TEXT[];
  v_section_key TEXT;
BEGIN
  -- Get user name from public.users
  -- Match user_id to either public.users.id or public.users.auth_id
  IF NEW.user_id IS NOT NULL THEN
    SELECT 
      COALESCE(pu.name, 'N/A') as name
    INTO v_user_name
    FROM public.users pu
    WHERE pu.id = NEW.user_id 
       OR pu.auth_id::text = NEW.user_id
    LIMIT 1;
  END IF;

  -- Default value if still null
  v_user_name := COALESCE(v_user_name, 'N/A');

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


