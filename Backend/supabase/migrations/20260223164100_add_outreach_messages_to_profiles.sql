ALTER TABLE marketing.profiles
  ADD COLUMN IF NOT EXISTS instagram_outreach_message TEXT,
  ADD COLUMN IF NOT EXISTS tiktok_outreach_message TEXT;
