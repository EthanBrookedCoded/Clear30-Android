-- Add goal column to schools table for cross-user goal persistence
-- (variant-two onboarding users can see the goal set by variant-one user)
ALTER TABLE schools.schools ADD COLUMN IF NOT EXISTS goal jsonb DEFAULT '{}'::jsonb;

-- 3. Auto-notify Slack on every support ticket insert
CREATE OR REPLACE FUNCTION schools.notify_support_ticket()
RETURNS trigger AS $$
BEGIN
  INSERT INTO comms.slack_notifications (channel_type, title, message, metadata, priority)
  VALUES (
    'school_support_tickets',
    NEW.category || ' — ' || NEW.subject,
    NEW.user_name || ' (' || NEW.user_email || ') from school ' || NEW.school_id || E'\n' || NEW.message,
    jsonb_build_object(
      'school_id', NEW.school_id,
      'user_id', NEW.user_id,
      'user_email', NEW.user_email,
      'category', NEW.category,
      'ticket_id', NEW.id
    ),
    'normal'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER support_ticket_slack_notify
  AFTER INSERT ON schools.support_tickets
  FOR EACH ROW
  EXECUTE FUNCTION schools.notify_support_ticket();
