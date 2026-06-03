-- Add phone_number column to marketing.creators table
ALTER TABLE marketing.creators
ADD COLUMN phone_number TEXT;

-- Optional: Add a comment describing the column
COMMENT ON COLUMN marketing.creators.phone_number IS 'Creator contact phone number';