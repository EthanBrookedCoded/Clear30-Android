-- Add foreign key constraint to dr_fred_conversations
ALTER TABLE comms.dr_fred_conversations
ADD CONSTRAINT dr_fred_conversations_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES public.users(id)
ON DELETE CASCADE;

-- Add foreign key constraint to sms_conversations
-- Note: user_id is nullable because the table stores both user and non-user conversations
-- This constraint only applies when user_id IS NOT NULL
ALTER TABLE platform.sms_conversations
ADD CONSTRAINT sms_conversations_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES public.users(id)
ON DELETE CASCADE;
