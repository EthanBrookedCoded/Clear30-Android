-- Migration: Add scheduled_for column to peer_messages for delayed message delivery
-- This enables the peer_day_schedule_manual function to schedule messages for future delivery

--------------------------------------------------------------------------------
-- 1. ADD SCHEDULED_FOR COLUMN
--------------------------------------------------------------------------------

ALTER TABLE comms.peer_messages
ADD COLUMN IF NOT EXISTS scheduled_for TIMESTAMPTZ;

-- Index for efficient scheduled message processing
CREATE INDEX IF NOT EXISTS idx_peer_messages_scheduled
ON comms.peer_messages(scheduled_for)
WHERE scheduled_for IS NOT NULL AND notification_sent IS NULL;

--------------------------------------------------------------------------------
-- 2. MODIFY NOTIFICATION TRIGGER
--------------------------------------------------------------------------------

-- Update the trigger function to only send notifications for messages that are due now
-- Messages with a future scheduled_for will be processed by the cron job
CREATE OR REPLACE FUNCTION comms.queue_peer_message_notification()
RETURNS TRIGGER AS $$
BEGIN
    -- Only queue notification for outbound messages that are due now
    -- Skip if scheduled_for is set and is in the future
    IF NEW.outbound = true AND (NEW.scheduled_for IS NULL OR NEW.scheduled_for <= now()) THEN
        INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp, status)
        VALUES (
            NEW.user_id,
            '💬 Julian',
            LEFT(NEW.text, 100),
            jsonb_build_object('type', 'peer_message', 'message_id', NEW.id),
            now(),
            'pending'
        );

        -- Mark the message as notification queued
        UPDATE comms.peer_messages
        SET notification_sent = now()
        WHERE id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
