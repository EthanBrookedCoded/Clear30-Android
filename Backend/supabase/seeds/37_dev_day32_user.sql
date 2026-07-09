-- ============================================================
-- DEV "DAY 32" USER — phone 15555550100 (test OTP 123456)
-- ============================================================
-- A one-tap local dev login that lands on program day 32 with ALL daily
-- content unlocked. Sign in with phone +1 555 555 0100 and code 123456
-- (config.toml [auth.sms.test_otp]). No Mailpit/email needed.
--
-- How it works:
--   • auth.users: a fixed-id phone user so the row is stable across db reset.
--   • public.users: start_date = now() - 32 days  → the app treats it as day 32,
--     so every clear30 lesson (days 0-30) is unlocked in the Today feed.
--   • program_assessment_responses: a `clear30` response so program_get_messages
--     serves the 30-day program (31 daily lessons, one per day). Empty responses
--     return the base, always-shown lesson for every day (verified: days 0-30).
--
-- Re-runnable: ON CONFLICT guards + a delete/insert make `supabase db reset`
-- idempotent.
-- ============================================================

-- ── auth.users (phone) ──────────────────────────────────────
INSERT INTO auth.users (
    instance_id, id, aud, role,
    email, encrypted_password, email_confirmed_at, invited_at,
    confirmation_token, confirmation_sent_at, recovery_token, recovery_sent_at,
    email_change_token_new, email_change, email_change_sent_at, last_sign_in_at,
    raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at,
    phone, phone_confirmed_at, phone_change, phone_change_token, phone_change_sent_at,
    email_change_token_current, email_change_confirm_status, banned_until,
    reauthentication_token, reauthentication_sent_at, is_sso_user, deleted_at, is_anonymous
) VALUES (
    '00000000-0000-0000-0000-000000000000',
    'aacc0d0c-cefd-414c-a2e8-ca8b92d66211',
    'authenticated', 'authenticated',
    NULL, NULL, NULL, NULL,
    '', NULL, '', NULL,
    '', '', NULL, now(),
    '{"provider": "phone", "providers": ["phone"]}', '{}', NULL, now(), now(),
    '15555550100', now(), '', '', NULL,
    '', 0, NULL,
    '', NULL, false, NULL, false
)
ON CONFLICT (id) DO NOTHING;

-- ── public.users (day 32) ───────────────────────────────────
INSERT INTO public.users (id, name, emoji, platform, auth_id, phone_number, start_date, created_at)
VALUES (
    'dev-day32', 'Dev', '🌱', 'android',
    'aacc0d0c-cefd-414c-a2e8-ca8b92d66211', '15555550100',
    now() - interval '32 days', now()
)
ON CONFLICT (id) DO UPDATE
    SET start_date  = EXCLUDED.start_date,
        auth_id     = EXCLUDED.auth_id,
        phone_number = EXCLUDED.phone_number;

-- ── clear30 assessment response (unlocks the 30-day program) ─
DELETE FROM programs.program_assessment_responses WHERE user_id = 'dev-day32';
INSERT INTO programs.program_assessment_responses (user_id, assessment, responses, "timestamp")
VALUES ('dev-day32', 'clear30', '{}'::jsonb, now());
