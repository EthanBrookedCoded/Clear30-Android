
-- Insert a default view-multiplier setting (set to 1)
INSERT INTO community.settings (id, value) 
VALUES ('view-multiplier', '1')
ON CONFLICT (id) DO NOTHING; 