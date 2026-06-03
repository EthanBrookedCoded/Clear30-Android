-- Add app_version column to users table
-- Tracks the iOS app version last used by this user
-- Updated via pushProgramData() alongside timezone

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS app_version TEXT DEFAULT NULL;

COMMENT ON COLUMN public.users.app_version IS 'The iOS app version last used by this user';
