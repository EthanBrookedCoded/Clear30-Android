-- Function to cancel pending notification when message is read
CREATE OR REPLACE FUNCTION comms.cancel_peer_message_notification()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $function$
BEGIN
    -- Only act when read_at changes from NULL to a value
    IF OLD.read_at IS NULL AND NEW.read_at IS NOT NULL THEN
        UPDATE comms.notifications
        SET status = 'cancelled'
        WHERE metadata->>'type' = 'peer_message'
          AND (metadata->>'message_id')::bigint = NEW.id
          AND status = 'pending';
    END IF;
    RETURN NEW;
END;
$function$;

-- Trigger on UPDATE of peer_messages
CREATE TRIGGER peer_message_read_cancel_notification_trigger
    AFTER UPDATE ON comms.peer_messages
    FOR EACH ROW
    WHEN (OLD.read_at IS NULL AND NEW.read_at IS NOT NULL)
    EXECUTE FUNCTION comms.cancel_peer_message_notification();
