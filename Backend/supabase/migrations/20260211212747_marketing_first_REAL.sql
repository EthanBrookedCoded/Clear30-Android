-- =============================================
-- MARKETING SCHEMA MIGRATION
-- =============================================
-- Creates the complete outreach/creator CRM system
-- under the `marketing` schema for integration into
-- an existing Supabase project.
--
-- Includes:
--   - Helper functions (SECURITY DEFINER) for RLS
--   - Comprehensive RLS policies (SELECT/INSERT/UPDATE/DELETE)
--   - Proper GRANTs for PostgREST roles
-- =============================================

-- =============================================
-- 1. SCHEMA + ENUM
-- =============================================

CREATE SCHEMA IF NOT EXISTS marketing;

CREATE TYPE marketing.user_role AS ENUM ('va', 'admin');

-- =============================================
-- 2. TABLES (in dependency order)
-- =============================================

-- Outreach accounts: Instagram/TikTok source accounts for VAs
CREATE TABLE marketing.outreach_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  platform TEXT NOT NULL CHECK (platform IN ('instagram', 'tiktok')),
  handle TEXT NOT NULL,
  account_key TEXT GENERATED ALWAYS AS (platform || ':' || LOWER(handle)) STORED,
  niche TEXT,
  display_name TEXT,
  is_active BOOLEAN NOT NULL DEFAULT true,
  notes TEXT,
  deleted_at TIMESTAMPTZ DEFAULT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT unique_outreach_account_key UNIQUE (account_key)
);

-- Profiles: user accounts linked to Supabase Auth
CREATE TABLE marketing.profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  full_name TEXT NOT NULL,
  email TEXT NOT NULL,
  role marketing.user_role NOT NULL DEFAULT 'va',
  instagram_outreach_account_id UUID REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL,
  tiktok_outreach_account_id UUID REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Creators: canonical creator registry
CREATE TABLE marketing.creators (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  platform TEXT NOT NULL CHECK (platform IN ('instagram', 'tiktok')),
  handle TEXT NOT NULL,
  creator_key TEXT GENERATED ALWAYS AS (platform || ':' || LOWER(handle)) STORED,
  full_name TEXT,
  first_name TEXT,
  primary_profile_url TEXT,
  niche TEXT,
  email TEXT,
  would_add BOOLEAN DEFAULT NULL,
  status TEXT NOT NULL DEFAULT 'reached_out' CHECK (status IN (
    'reached_out', 'sent_cal_link', 'call_scheduled', 'negotiating', 'not_interested', 'future', 'closed'
  )),
  meeting_doc TEXT,
  added_by_profile_id UUID REFERENCES marketing.profiles(id) ON DELETE SET NULL,
  sourced_via_outreach_account_id UUID REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL,
  first_reached_out_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_reached_out_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT,
  deleted_at TIMESTAMPTZ DEFAULT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT unique_creator_key UNIQUE (creator_key)
);

-- Creator submissions: audit log of all submissions
CREATE TABLE marketing.creator_submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  submitted_by_va_id UUID NOT NULL REFERENCES marketing.profiles(id) ON DELETE CASCADE,
  outreach_account_id UUID REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL,
  platform TEXT,
  handle TEXT,
  creator_key TEXT,
  profile_url TEXT,
  niche TEXT,
  submitted_name TEXT,
  email TEXT,
  result TEXT NOT NULL CHECK (result IN ('inserted', 'duplicate', 'invalid')),
  matched_creator_id UUID REFERENCES marketing.creators(id) ON DELETE SET NULL,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT
);

-- Creator notes: notes attached to creators
CREATE TABLE marketing.creator_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id UUID NOT NULL REFERENCES marketing.creators(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  created_by_profile_id UUID NOT NULL REFERENCES marketing.profiles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Creator status changes: status change activity log
CREATE TABLE marketing.creator_status_changes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id UUID NOT NULL REFERENCES marketing.creators(id) ON DELETE CASCADE,
  old_status TEXT NOT NULL,
  new_status TEXT NOT NULL,
  changed_by_profile_id UUID NOT NULL REFERENCES marketing.profiles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- 3. INDEXES
-- =============================================

CREATE INDEX idx_mk_profiles_auth_user_id ON marketing.profiles(auth_user_id);
CREATE INDEX idx_mk_creators_creator_key ON marketing.creators(creator_key);
CREATE INDEX idx_mk_creators_platform_handle ON marketing.creators(platform, LOWER(handle));
CREATE INDEX idx_mk_creators_deleted_at ON marketing.creators(deleted_at);
CREATE INDEX idx_mk_outreach_accounts_deleted_at ON marketing.outreach_accounts(deleted_at);
CREATE INDEX idx_mk_submissions_va_id ON marketing.creator_submissions(submitted_by_va_id, submitted_at);
CREATE INDEX idx_mk_submissions_result ON marketing.creator_submissions(result, submitted_at);
CREATE INDEX idx_mk_creator_notes_creator_id ON marketing.creator_notes(creator_id);
CREATE INDEX idx_mk_status_changes_creator_id ON marketing.creator_status_changes(creator_id);

-- =============================================
-- 4. TRIGGER FUNCTION
-- =============================================

CREATE OR REPLACE FUNCTION marketing.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_mk_outreach_accounts_updated_at
  BEFORE UPDATE ON marketing.outreach_accounts
  FOR EACH ROW EXECUTE FUNCTION marketing.update_updated_at();

CREATE TRIGGER tr_mk_profiles_updated_at
  BEFORE UPDATE ON marketing.profiles
  FOR EACH ROW EXECUTE FUNCTION marketing.update_updated_at();

CREATE TRIGGER tr_mk_creators_updated_at
  BEFORE UPDATE ON marketing.creators
  FOR EACH ROW EXECUTE FUNCTION marketing.update_updated_at();

-- =============================================
-- 5. HELPER FUNCTIONS (SECURITY DEFINER)
-- =============================================
-- These bypass RLS on profiles to avoid circular
-- dependencies when RLS policies reference them.

-- Returns the role ('va' or 'admin') for the current authenticated user
CREATE OR REPLACE FUNCTION marketing.get_user_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT role::TEXT FROM marketing.profiles
  WHERE auth_user_id = auth.uid()
$$;

-- Returns the profile UUID for the current authenticated user
CREATE OR REPLACE FUNCTION marketing.get_profile_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT id FROM marketing.profiles
  WHERE auth_user_id = auth.uid()
$$;

-- Checks if a creator exists by creator_key (global, bypasses RLS)
-- Needed because VAs can only see their own creators, but
-- duplicate detection must check across all VAs.
CREATE OR REPLACE FUNCTION marketing.check_creator_exists(p_creator_key TEXT)
RETURNS TABLE(id UUID, handle TEXT, first_reached_out_at TIMESTAMPTZ, added_by_profile_id UUID, status TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT c.id, c.handle, c.first_reached_out_at, c.added_by_profile_id, c.status
  FROM marketing.creators c
  WHERE c.creator_key = p_creator_key
  AND c.deleted_at IS NULL
  LIMIT 1
$$;

-- =============================================
-- 6. ROW LEVEL SECURITY
-- =============================================

ALTER TABLE marketing.outreach_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketing.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketing.creators ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketing.creator_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketing.creator_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketing.creator_status_changes ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------
-- PROFILES
-- ---------------------------------------------

-- All authenticated can view all profiles (internal tool; needed for joins)
CREATE POLICY "Authenticated users can view all profiles"
  ON marketing.profiles
  FOR SELECT TO authenticated
  USING (true);

-- Users can only update their own profile
CREATE POLICY "Users can update own profile"
  ON marketing.profiles
  FOR UPDATE TO authenticated
  USING (auth_user_id = auth.uid())
  WITH CHECK (auth_user_id = auth.uid());

-- ---------------------------------------------
-- OUTREACH ACCOUNTS
-- ---------------------------------------------

-- Authenticated can view non-deleted accounts
CREATE POLICY "View active outreach accounts"
  ON marketing.outreach_accounts
  FOR SELECT TO authenticated
  USING (deleted_at IS NULL);

-- Only admins can create outreach accounts
CREATE POLICY "Admins can create outreach accounts"
  ON marketing.outreach_accounts
  FOR INSERT TO authenticated
  WITH CHECK (marketing.get_user_role() = 'admin');

-- Only admins can update (including soft-delete) outreach accounts
CREATE POLICY "Admins can update outreach accounts"
  ON marketing.outreach_accounts
  FOR UPDATE TO authenticated
  USING (marketing.get_user_role() = 'admin')
  WITH CHECK (marketing.get_user_role() = 'admin');

-- ---------------------------------------------
-- CREATORS
-- ---------------------------------------------

-- VAs see only their own non-deleted creators; admins see all non-deleted
CREATE POLICY "Users can view creators"
  ON marketing.creators
  FOR SELECT TO authenticated
  USING (
    deleted_at IS NULL
    AND (
      marketing.get_user_role() = 'admin'
      OR added_by_profile_id = marketing.get_profile_id()
    )
  );

-- Authenticated users can insert creators (must be their own profile)
CREATE POLICY "Authenticated users can insert creators"
  ON marketing.creators
  FOR INSERT TO authenticated
  WITH CHECK (added_by_profile_id = marketing.get_profile_id());

-- VAs can update their own creators; admins can update any
CREATE POLICY "Users can update creators"
  ON marketing.creators
  FOR UPDATE TO authenticated
  USING (
    marketing.get_user_role() = 'admin'
    OR added_by_profile_id = marketing.get_profile_id()
  )
  WITH CHECK (
    marketing.get_user_role() = 'admin'
    OR added_by_profile_id = marketing.get_profile_id()
  );

-- ---------------------------------------------
-- CREATOR SUBMISSIONS
-- ---------------------------------------------

-- View own submissions only
CREATE POLICY "View own submissions"
  ON marketing.creator_submissions
  FOR SELECT TO authenticated
  USING (submitted_by_va_id = marketing.get_profile_id());

-- Authenticated can insert submissions (must be own profile)
CREATE POLICY "Authenticated users can insert submissions"
  ON marketing.creator_submissions
  FOR INSERT TO authenticated
  WITH CHECK (submitted_by_va_id = marketing.get_profile_id());

-- ---------------------------------------------
-- CREATOR NOTES
-- ---------------------------------------------

-- All authenticated can view notes
CREATE POLICY "Authenticated users can view notes"
  ON marketing.creator_notes
  FOR SELECT TO authenticated
  USING (true);

-- Authenticated can insert notes (must be own profile)
CREATE POLICY "Authenticated users can insert notes"
  ON marketing.creator_notes
  FOR INSERT TO authenticated
  WITH CHECK (created_by_profile_id = marketing.get_profile_id());

-- Users can only delete their own notes
CREATE POLICY "Users can delete own notes"
  ON marketing.creator_notes
  FOR DELETE TO authenticated
  USING (created_by_profile_id = marketing.get_profile_id());

-- ---------------------------------------------
-- CREATOR STATUS CHANGES
-- ---------------------------------------------

-- All authenticated can view status changes
CREATE POLICY "Authenticated users can view status changes"
  ON marketing.creator_status_changes
  FOR SELECT TO authenticated
  USING (true);

-- Authenticated can insert status changes (must be own profile)
CREATE POLICY "Authenticated users can insert status changes"
  ON marketing.creator_status_changes
  FOR INSERT TO authenticated
  WITH CHECK (changed_by_profile_id = marketing.get_profile_id());

-- =============================================
-- 7. GRANTS
-- =============================================

-- Allow PostgREST roles to use the marketing schema
GRANT USAGE ON SCHEMA marketing TO anon, authenticated;

-- Table permissions (RLS enforces the actual access control)
GRANT SELECT ON ALL TABLES IN SCHEMA marketing TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA marketing TO authenticated;

-- Function execution
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA marketing TO authenticated;

-- Default privileges for any future tables/functions in this schema
ALTER DEFAULT PRIVILEGES IN SCHEMA marketing
  GRANT SELECT ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA marketing
  GRANT INSERT, UPDATE, DELETE ON TABLES TO authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA marketing
  GRANT EXECUTE ON FUNCTIONS TO authenticated;
