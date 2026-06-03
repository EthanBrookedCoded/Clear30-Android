-- Add starred column to marketing.creators
-- Used by VAs to flag/track creators they want to follow up on.
-- Persists across sessions and page refreshes.

ALTER TABLE marketing.creators
  ADD COLUMN IF NOT EXISTS starred boolean NOT NULL DEFAULT false;
