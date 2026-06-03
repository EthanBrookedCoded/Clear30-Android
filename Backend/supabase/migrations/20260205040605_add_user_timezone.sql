-- Add timezone column to public.users
-- Stores IANA timezone identifier (e.g., 'America/New_York', 'America/Los_Angeles')
-- Used by SMS scheduling edge functions to calculate "today" in user's local timezone

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS timezone text DEFAULT 'America/New_York';

COMMENT ON COLUMN public.users.timezone IS 'IANA timezone identifier (e.g., America/New_York). Used for SMS scheduling.';
