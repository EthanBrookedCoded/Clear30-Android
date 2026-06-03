set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.notify_slack_peer_message()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user record;
    v_message_text text;
    v_title text;
BEGIN
    -- Only notify on inbound manual messages (from user to us)
    IF NEW.outbound = true OR NEW.type != 'manual' THEN
        RETURN NEW;
    END IF;

    -- Get user name for context
    SELECT name INTO v_user
    FROM public.users
    WHERE id = NEW.user_id;

    v_message_text := left(NEW.text, 500);
    v_title := 'New Peer Message from ' || coalesce(v_user.name, 'Unknown User');

    INSERT INTO comms.slack_notifications (
        channel_type, title, message, metadata, status, priority
    ) VALUES (
        'peer',
        v_title,
        v_message_text,
        jsonb_build_object(
            'user_id', NEW.user_id,
            'message_id', NEW.id,
            'user_name', coalesce(v_user.name, 'Unknown'),
            'blocks', jsonb_build_array(
                jsonb_build_object(
                    'type', 'header',
                    'text', jsonb_build_object(
                        'type', 'plain_text',
                        'text', v_title
                    )
                ),
                jsonb_build_object(
                    'type', 'section',
                    'text', jsonb_build_object(
                        'type', 'mrkdwn',
                        'text', v_message_text
                    )
                ),
                jsonb_build_object(
                    'type', 'context',
                    'elements', jsonb_build_array(
                        jsonb_build_object(
                            'type', 'mrkdwn',
                            'text', '*User ID:* ' || NEW.user_id
                        )
                    )
                )
            )
        ),
        'pending',
        'normal'
    );

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'notify_slack_peer_message failed: %', SQLERRM;
    RETURN NEW;
END;
$function$
;

CREATE TRIGGER notify_slack_peer_message AFTER INSERT ON comms.peer_messages FOR EACH ROW EXECUTE FUNCTION comms.notify_slack_peer_message();


