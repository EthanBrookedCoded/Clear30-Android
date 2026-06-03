CREATE OR REPLACE FUNCTION schools.notify_campus_submission()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $function$
DECLARE
  v_university_name TEXT;
  v_contact_name TEXT;
  v_contact_email TEXT;
  v_contact_phone TEXT;
  v_contact_title TEXT;
  v_undergrad_enrollment TEXT;
  v_estimated_students TEXT;
  v_lms TEXT;
  v_sso TEXT;
  v_it_contact_name TEXT;
  v_it_contact_email TEXT;
  v_distribution TEXT;
  v_launch_timing TEXT;
  v_populations TEXT;
  v_timestamp TEXT;
BEGIN
  v_university_name      := COALESCE(NEW.submission #>> '{campus_info,university_name}', 'Unknown University');
  v_contact_name         := COALESCE(NEW.submission #>> '{campus_info,contact_name}', 'N/A');
  v_contact_email        := COALESCE(NEW.submission #>> '{campus_info,contact_email}', 'N/A');
  v_contact_phone        := COALESCE(NEW.submission #>> '{campus_info,contact_phone}', 'N/A');
  v_contact_title        := COALESCE(NEW.submission #>> '{campus_info,contact_title}', 'N/A');
  v_undergrad_enrollment := COALESCE(NEW.submission #>> '{campus_info,undergraduate_enrollment}', 'N/A');
  v_estimated_students   := COALESCE(NEW.submission #>> '{campus_info,estimated_students}', 'N/A');

  v_lms                  := COALESCE(NEW.submission #>> '{implementation,lms}', 'N/A');
  v_sso                  := COALESCE(NEW.submission #>> '{implementation,sso}', 'N/A');
  v_it_contact_name      := COALESCE(NEW.submission #>> '{implementation,it_contact_name}', 'N/A');
  v_it_contact_email     := COALESCE(NEW.submission #>> '{implementation,it_contact_email}', 'N/A');

  SELECT COALESCE(string_agg(value, ', '), 'N/A')
    INTO v_distribution
  FROM jsonb_array_elements_text(COALESCE(NEW.submission #> '{implementation,distribution_methods}', '[]'::jsonb));

  v_launch_timing        := COALESCE(NEW.submission #>> '{student_population,launch_timing}', 'N/A');

  SELECT COALESCE(string_agg(value, ', '), 'N/A')
    INTO v_populations
  FROM jsonb_array_elements_text(COALESCE(NEW.submission #> '{student_population,populations}', '[]'::jsonb));

  v_timestamp := to_char(NEW.created_at, 'MM/DD/YYYY, HH12:MI:SS AM');

  INSERT INTO comms.slack_notifications (
    channel_type,
    title,
    message,
    metadata,
    status,
    priority
  ) VALUES (
    'school_support_tickets',
    'New Campus Form Submission — ' || v_university_name,
    v_contact_name || ' (' || v_contact_email || ') from ' || v_university_name,
    jsonb_build_object(
      'submission_id', NEW.id,
      'university_name', v_university_name,
      'contact_name', v_contact_name,
      'contact_email', v_contact_email,
      'submitted_at', v_timestamp,
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '🎓 New Campus Form Submission'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object('type','mrkdwn','text','*University:* ' || v_university_name),
            jsonb_build_object('type','mrkdwn','text','*Submitted:* ' || v_timestamp),
            jsonb_build_object('type','mrkdwn','text','*Contact:* ' || v_contact_name),
            jsonb_build_object('type','mrkdwn','text','*Title:* ' || v_contact_title),
            jsonb_build_object('type','mrkdwn','text','*Email:* ' || v_contact_email),
            jsonb_build_object('type','mrkdwn','text','*Phone:* ' || v_contact_phone),
            jsonb_build_object('type','mrkdwn','text','*Undergrad Enrollment:* ' || v_undergrad_enrollment),
            jsonb_build_object('type','mrkdwn','text','*Estimated Students:* ' || v_estimated_students)
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object('type','mrkdwn','text','*LMS:* ' || v_lms),
            jsonb_build_object('type','mrkdwn','text','*SSO:* ' || v_sso),
            jsonb_build_object('type','mrkdwn','text','*IT Contact:* ' || v_it_contact_name),
            jsonb_build_object('type','mrkdwn','text','*IT Email:* ' || v_it_contact_email),
            jsonb_build_object('type','mrkdwn','text','*Launch Timing:* ' || v_launch_timing),
            jsonb_build_object('type','mrkdwn','text','*Distribution:* ' || v_distribution)
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Populations:*' || chr(10) || v_populations
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Submission ID:* `' || NEW.id::text || '`'
          )
        )
      )
    ),
    'pending',
    'normal'
  );

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS campus_submission_slack_notify ON schools.campus_submissions;

CREATE TRIGGER campus_submission_slack_notify
AFTER INSERT ON schools.campus_submissions
FOR EACH ROW
EXECUTE FUNCTION schools.notify_campus_submission();
