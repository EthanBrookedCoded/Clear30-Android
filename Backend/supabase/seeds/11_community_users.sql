-- Begin transaction
BEGIN;

-- Insert a test user into auth.users
INSERT INTO auth.users (
    id,
    email,
    encrypted_password,
    email_confirmed_at,
    created_at,
    updated_at,
    raw_app_meta_data,
    raw_user_meta_data,
    aud,
    role,
    is_super_admin
) VALUES (
    '12345678-1234-1234-1234-123456789012'::uuid,
    'test@example.com',
    -- Password is 'password123' - using Supabase Auth compatible bcrypt hash
    '$2a$10$.5nUEXoHDC5VXR5oXUQkEOXf0ruhLCUcb0H5oHiA4UgqAwXKGQQK.',
    NOW(),
    NOW(),
    NOW(),
    '{"provider":"email","providers":["email"]}',
    '{"name":"Test User"}',
    'authenticated',
    'authenticated',
    false
) ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    encrypted_password = EXCLUDED.encrypted_password,
    email_confirmed_at = EXCLUDED.email_confirmed_at,
    raw_app_meta_data = EXCLUDED.raw_app_meta_data,
    raw_user_meta_data = EXCLUDED.raw_user_meta_data,
    aud = EXCLUDED.aud,
    role = EXCLUDED.role;

-- Insert corresponding user into public.users
INSERT INTO public.users (
    id,
    auth_id,
    name,
    emoji,
    days_sober,
    initial_frequency,
    show_in_group_rank
) VALUES (
    'test-user-id-123',
    '12345678-1234-1234-1234-123456789012'::uuid,
    'Test User',
    '👤',
    ARRAY[true, true, true]::boolean[],
    7,
    true
) ON CONFLICT (auth_id) DO NOTHING;

-- Create some test posts for this user
INSERT INTO community.posts (
    id,
    user_id,
    title,
    content_type,
    body,
    is_pinned,
    is_hidden,
    view_count,
    created_at
) VALUES 
(
    '11111111-1111-1111-1111-111111111111'::uuid,
    'test-user-id-123',
    'Test Post 1 by Test User',
    'text',
    'This is a test post created by our test user. It contains some sample content for testing purposes.',
    false,
    false,
    10,
    NOW()
),
(
    '22222222-2222-2222-2222-222222222222'::uuid,
    'test-user-id-123',
    'Test Post 2 by Test User',
    'text',
    'This is another test post by our test user. We can use this to test deletion and other operations.',
    false,
    false,
    5,
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Add some tags to the test posts
INSERT INTO community.post_tags (post_id, tag_id)
SELECT 
    '11111111-1111-1111-1111-111111111111'::uuid,
    id
FROM community.tags 
WHERE name IN ('Tips', 'Discussion')
ON CONFLICT DO NOTHING;

INSERT INTO community.post_tags (post_id, tag_id)
SELECT 
    '22222222-2222-2222-2222-222222222222'::uuid,
    id
FROM community.tags 
WHERE name IN ('Question', 'Support')
ON CONFLICT DO NOTHING;

-- Add some test comments
INSERT INTO community.comments (
    id,
    post_id,
    user_id,
    body,
    created_at
) VALUES 
(
    '33333333-3333-3333-3333-333333333333'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'test-user-id-123',
    'This is a test comment on the first post',
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Add some test reactions
INSERT INTO community.reactions (
    post_id,
    user_id,
    emoji
) VALUES 
(
    '11111111-1111-1111-1111-111111111111'::uuid,
    'test-user-id-123',
    '👍'
) ON CONFLICT DO NOTHING;

-- Make the test user an admin (optional, uncomment if needed)
-- INSERT INTO public.admins (auth_id) 
-- VALUES ('12345678-1234-1234-1234-123456789012'::uuid)
-- ON CONFLICT DO NOTHING;

COMMIT;

-- Output the test credentials (these will appear in the migration output)
DO $$
BEGIN
    RAISE NOTICE 'Test User Created:';
    RAISE NOTICE 'Email: test@example.com';
    RAISE NOTICE 'Password: password123';
    RAISE NOTICE 'Auth ID: 12345678-1234-1234-1234-123456789012';
    RAISE NOTICE 'User ID: test-user-id-123';
    RAISE NOTICE 'Test Post IDs:';
    RAISE NOTICE '  Post 1: 11111111-1111-1111-1111-111111111111';
    RAISE NOTICE '  Post 2: 22222222-2222-2222-2222-222222222222';
END $$; 