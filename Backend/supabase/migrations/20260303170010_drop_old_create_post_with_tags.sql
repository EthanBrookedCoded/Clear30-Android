-- Drop the old function overload (original param order) that conflicts with the new signature
DROP FUNCTION IF EXISTS community.create_post_with_tags(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT[]);