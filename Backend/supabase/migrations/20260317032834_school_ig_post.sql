-- Add picked_instagram_post column to schools table
-- Stores the Instagram post selection shared across all portal users for a school
ALTER TABLE schools.schools ADD COLUMN IF NOT EXISTS picked_instagram_post jsonb DEFAULT NULL;
