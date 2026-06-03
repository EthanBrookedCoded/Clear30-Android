-- Enable Supabase Realtime for comms.dr_fred
-- Required for the DrFredChatViewModel's Realtime subscription to work.
-- Without this, inserts/updates on comms.dr_fred are not broadcast to iOS clients.
ALTER PUBLICATION supabase_realtime ADD TABLE comms.dr_fred;
