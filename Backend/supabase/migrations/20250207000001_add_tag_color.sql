-- Add color column with hex color validation
ALTER TABLE community.tags ADD COLUMN color text;

-- Add check constraint to ensure valid hex color format (e.g., #FF0000 or #ff0000)
ALTER TABLE community.tags 
    ADD CONSTRAINT valid_hex_color 
    CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$');

-- Add comment for the color column
COMMENT ON COLUMN community.tags.color IS 'Hex color code for the tag (e.g., #FF0000 for red)';

-- Set default colors based on tag type
UPDATE community.tags 
SET color = CASE type
    WHEN 'program' THEN '#4A90E2'  -- Blue
    WHEN 'day' THEN '#7ED321'      -- Green
    WHEN 'event' THEN '#F5A623'    -- Orange
    WHEN 'general' THEN '#9B9B9B'  -- Gray
END
WHERE color IS NULL; 