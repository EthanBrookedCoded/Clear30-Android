-- Optimized indexes for peer_messages queries

-- Drop the existing user_id index that doesn't cover all filter conditions
DROP INDEX IF EXISTS comms.idx_peer_messages_user_id;

-- Main chat query index: covers user_id + deleted + scheduled_for filter + created_at ordering
-- This composite index allows PostgreSQL to efficiently filter and sort for the chat load query
CREATE INDEX idx_peer_messages_user_chat
ON comms.peer_messages (user_id, scheduled_for, created_at DESC)
WHERE deleted = false;

-- Unread count query index: optimized for counting unread outbound messages
-- Covers: outbound = true, scheduled_for <= now(), read_at IS NULL
CREATE INDEX idx_peer_messages_unread
ON comms.peer_messages (user_id, scheduled_for)
WHERE outbound = true AND read_at IS NULL AND deleted = false;
