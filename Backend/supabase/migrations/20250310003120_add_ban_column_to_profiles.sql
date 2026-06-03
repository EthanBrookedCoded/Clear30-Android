-- Add a new column 'is_ban' to the 'profiles' table
ALTER TABLE community.profiles
ADD COLUMN is_ban BOOLEAN DEFAULT FALSE;

-- Add a comment to describe the purpose of the 'ban' column
COMMENT ON COLUMN community.profiles.is_ban IS 'Indicates whether the profile is banned (true) or not (false). Default is false.';
