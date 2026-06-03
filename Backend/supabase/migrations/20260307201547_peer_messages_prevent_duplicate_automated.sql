-- Prevent race condition where concurrent webhook invocations insert duplicate
-- automated peer messages for the same user on the same day.
CREATE UNIQUE INDEX idx_peer_messages_one_per_type_per_day
ON comms.peer_messages (user_id, type, (timezone('UTC', created_at)::date))
WHERE type IN ('automated', 'automated-event', 'follow-up');
