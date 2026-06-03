-- ============================================
-- Replace Edge Function Webhooks with RPC Functions
-- Created: January 14, 2026
-- 
-- This migration replaces HTTP-based edge function calls
-- with direct RPC functions that insert into slack_notifications.
-- This eliminates network overhead and simplifies the architecture.
-- ============================================

-- ============================================
-- PART 1: Create RPC Functions
-- ============================================

-- Function: comms.notify_slack_dr_fred
-- Triggered on INSERT to comms.dr_fred (only for inbound messages)
CREATE OR REPLACE FUNCTION comms.notify_slack_dr_fred()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_user_name TEXT;
  v_user_email TEXT;
  v_user_phone TEXT;
  v_timestamp TEXT;
BEGIN
  -- Only process inbound messages (outbound = false)
  IF NEW.outbound = true THEN
    RETURN NEW;
  END IF;

  -- Get user name, email and phone from public.users
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
    'fred',
    'New Dr. Fred Message',
    NEW.text,
    jsonb_build_object(
      'message_id', NEW.id,
      'user_id', NEW.user_id,
      'user_name', v_user_name,
      'user_email', v_user_email,
      'user_phone', v_user_phone,
      'received_at', v_timestamp,
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '👨‍⚕️ New Dr. Fred Message'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*User:* ' || COALESCE(NEW.user_id, 'N/A')
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Name:* ' || v_user_name
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
              'text', '*Received at:* ' || v_timestamp
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Message ID:* ' || NEW.id
            )
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Message:*' || chr(10) || NEW.text
          )
        ),
        jsonb_build_object(
          'type', 'actions',
          'elements', jsonb_build_array(
            jsonb_build_object(
              'type', 'button',
              'text', jsonb_build_object(
                'type', 'plain_text',
                'text', 'Reply in Panel'
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
$$;

-- Function: community.notify_slack_reported_post
-- Triggered on INSERT to community.reported_posts
CREATE OR REPLACE FUNCTION community.notify_slack_reported_post()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_post_title TEXT;
  v_post_body TEXT;
  v_post_content_type TEXT;
  v_post_video_url TEXT;
  v_post_author_id TEXT;
  v_post_content TEXT;
  v_reporter_username TEXT;
  v_timestamp TEXT;
BEGIN
  -- Format timestamp
  v_timestamp := to_char(NEW.created_at, 'MM/DD/YYYY, HH12:MI:SS AM');

  -- Get reporter username (use user_id or 'Anonymous')
  v_reporter_username := COALESCE(NEW.user_id, 'Anonymous');

  -- Fetch post details if post_id exists
  IF NEW.post_id IS NOT NULL THEN
    SELECT 
      title,
      body,
      content_type,
      video_url,
      user_id
    INTO 
      v_post_title,
      v_post_body,
      v_post_content_type,
      v_post_video_url,
      v_post_author_id
    FROM community.posts
    WHERE id = NEW.post_id;

    -- Build post content string
    IF v_post_body IS NOT NULL THEN
      v_post_content := v_post_body;
    ELSIF v_post_content_type = 'video' AND v_post_video_url IS NOT NULL THEN
      v_post_content := 'Video post: ' || v_post_video_url;
    ELSE
      v_post_content := 'No content';
    END IF;
  ELSE
    v_post_content := 'Post content not available';
    v_post_title := 'Untitled post';
    v_post_author_id := 'Unknown';
  END IF;

  -- Default values
  v_post_title := COALESCE(v_post_title, 'Untitled post');
  v_post_author_id := COALESCE(v_post_author_id, 'Unknown');

  -- Insert into slack_notifications
  INSERT INTO comms.slack_notifications (
    channel_type,
    title,
    message,
    metadata,
    status,
    priority
  ) VALUES (
    'community',
    'New Post Reported',
    'Post "' || v_post_title || '" reported by ' || v_reporter_username,
    jsonb_build_object(
      'report_id', NEW.id,
      'post_id', NEW.post_id,
      'post_author', v_post_author_id,
      'reporter', v_reporter_username,
      'reported_at', v_timestamp,
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '⚠️ New Post Reported'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Post:* ' || v_post_title
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Author:* ' || v_post_author_id
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Reported by:* ' || v_reporter_username
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Reported at:* ' || v_timestamp
            )
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Content:*' || chr(10) || v_post_content
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
    'high'
  );

  RETURN NEW;
END;
$$;

-- Function: comms.notify_slack_feedback
-- Triggered on INSERT to comms.feedback
CREATE OR REPLACE FUNCTION comms.notify_slack_feedback()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_user_phone TEXT;
  v_user_identifier TEXT;
  v_timestamp TEXT;
BEGIN
  -- Format timestamp
  v_timestamp := to_char(NEW.timestamp, 'MM/DD/YYYY, HH12:MI:SS AM');

  -- Fetch user phone number if user_id exists
  IF NEW.user_id IS NOT NULL THEN
    SELECT phone_number
    INTO v_user_phone
    FROM public.users
    WHERE id = NEW.user_id
    LIMIT 1;
  END IF;

  -- Format user identifier
  v_user_identifier := COALESCE(NEW.user_id, 'Anonymous');

  -- Insert into slack_notifications
  INSERT INTO comms.slack_notifications (
    channel_type,
    title,
    message,
    metadata,
    status,
    priority
  ) VALUES (
    'feedback',
    'New Feedback Received',
    NEW.feedback,
    jsonb_build_object(
      'feedback_id', NEW.id,
      'feedback_type', NEW.type,
      'user', v_user_identifier,
      'user_phone', COALESCE(v_user_phone, 'N/A'),
      'submitted', v_timestamp,
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '📝 New Feedback Received'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Type:* ' || NEW.type
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*User:* ' || v_user_identifier
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Phone:* ' || COALESCE(v_user_phone, 'N/A')
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Submitted:* ' || v_timestamp
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Feedback ID:* ' || NEW.id
            )
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Message:*' || chr(10) || NEW.feedback
          )
        )
      )
    ),
    'pending',
    'normal'
  );

  RETURN NEW;
END;
$$;

-- ============================================
-- PART 2: Replace Triggers
-- ============================================

-- Drop old triggers that call edge functions
DROP TRIGGER IF EXISTS dr_fred_notify_team ON comms.dr_fred;
DROP TRIGGER IF EXISTS community_report_notify_team ON community.reported_posts;
DROP TRIGGER IF EXISTS feedback_notify ON comms.feedback;

-- Create new triggers that call RPC functions
CREATE TRIGGER dr_fred_notify_team
  AFTER INSERT ON comms.dr_fred
  FOR EACH ROW
  EXECUTE FUNCTION comms.notify_slack_dr_fred();

CREATE TRIGGER community_report_notify_team
  AFTER INSERT ON community.reported_posts
  FOR EACH ROW
  EXECUTE FUNCTION community.notify_slack_reported_post();

CREATE TRIGGER feedback_notify
  AFTER INSERT ON comms.feedback
  FOR EACH ROW
  EXECUTE FUNCTION comms.notify_slack_feedback();

-- ============================================
-- Notes:
-- ============================================
-- After this migration:
-- 1. The edge functions (dr_fred_notify_team, community_report_notify_team, feedback_notify)
--    can be removed from the functions folder if desired, as they are no longer called.
-- 2. The triggers now execute synchronously within the database transaction,
--    which is faster and more reliable than HTTP calls.
-- 3. All notifications are still inserted into comms.slack_notifications with the
--    same structure, so the existing notification_send edge function will continue
--    to work for actually sending to Slack.
