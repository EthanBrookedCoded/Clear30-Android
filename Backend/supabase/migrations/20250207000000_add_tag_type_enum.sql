-- Create the tag_type enum
CREATE TYPE community.tag_type AS ENUM ('program', 'day', 'event', 'general');

-- Add the tag_type column to the tags table with a default value
ALTER TABLE community.tags ADD COLUMN type community.tag_type NOT NULL DEFAULT 'general';

-- Add an index on the type column for better query performance
CREATE INDEX tags_type_idx ON community.tags(type);

-- Comment on the enum type for documentation
COMMENT ON TYPE community.tag_type IS 'Categories of tags: program (for program-specific tags), day (for day-specific content), event (for events), and general (for miscellaneous tags)'; 