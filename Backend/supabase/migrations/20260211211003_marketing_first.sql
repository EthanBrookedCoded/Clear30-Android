-- =============================================
-- OUTREACH SYSTEM STANDALONE MIGRATION
-- =============================================
-- This migration creates the complete outreach/creator CRM system
-- for a new Supabase project. It includes:
--   - outreach_accounts: Instagram/TikTok source accounts for VAs
--   - profiles: User accounts (linked to Supabase Auth)
--   - creators: Canonical creator registry
--   - creator_submissions: Audit log of all submissions
--   - creator_notes: Notes attached to creators
--   - creator_status_changes: Status change activity log
-- =============================================

-- =============================================
-- 1. ENUM TYPES
-- =============================================

CREATE TYPE user_role AS ENUM ('va', 'admin');

-- =============================================
-- 2. TABLES (in dependency order)
-- =============================================

-- ---------------------------------------------
-- OUTREACH ACCOUNTS TABLE
-- Instagram/TikTok source accounts for VAs
-- ---------------------------------------------
CREATE TABLE outreach_accounts (
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

-- ---------------------------------------------
-- PROFILES TABLE
-- User accounts linked to Supabase Auth
-- ---------------------------------------------
CREATE TABLE profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  full_name TEXT NOT NULL,
  email TEXT NOT NULL,
  role user_role NOT NULL DEFAULT 'va',
  instagram_outreach_account_id UUID REFERENCES outreach_accounts(id) ON DELETE SET NULL,
  tiktok_outreach_account_id UUID REFERENCES outreach_accounts(id) ON DELETE SET NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------
-- CREATORS TABLE
-- Canonical creator registry
-- ---------------------------------------------
CREATE TABLE creators (
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
    'reached_out', 'negotiating', 'not_interested', 'closed'
  )),
  added_by_profile_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
  sourced_via_outreach_account_id UUID REFERENCES outreach_accounts(id) ON DELETE SET NULL,
  first_reached_out_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_reached_out_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT,
  deleted_at TIMESTAMPTZ DEFAULT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT unique_creator_key UNIQUE (creator_key)
);

-- ---------------------------------------------
-- CREATOR SUBMISSIONS TABLE
-- Audit log of all submissions
-- ---------------------------------------------
CREATE TABLE creator_submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  submitted_by_va_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  outreach_account_id UUID REFERENCES outreach_accounts(id) ON DELETE SET NULL,
  platform TEXT,
  handle TEXT,
  creator_key TEXT,
  profile_url TEXT,
  niche TEXT,
  submitted_name TEXT,
  email TEXT,
  result TEXT NOT NULL CHECK (result IN ('inserted', 'duplicate', 'invalid')),
  matched_creator_id UUID REFERENCES creators(id) ON DELETE SET NULL,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT
);

-- ---------------------------------------------
-- CREATOR NOTES TABLE
-- Notes attached to creators
-- ---------------------------------------------
CREATE TABLE creator_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  created_by_profile_id UUID NOT NULL REFERENCES profiles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------
-- CREATOR STATUS CHANGES TABLE
-- Status change activity log
-- ---------------------------------------------
CREATE TABLE creator_status_changes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
  old_status TEXT NOT NULL,
  new_status TEXT NOT NULL,
  changed_by_profile_id UUID NOT NULL REFERENCES profiles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- 3. INDEXES
-- =============================================

-- Profiles
CREATE INDEX idx_profiles_auth_user_id ON profiles(auth_user_id);

-- Creators
CREATE INDEX idx_creators_creator_key ON creators(creator_key);
CREATE INDEX idx_creators_platform_handle ON creators(platform, LOWER(handle));
CREATE INDEX idx_creators_deleted_at ON creators(deleted_at);

-- Outreach accounts
CREATE INDEX idx_outreach_accounts_deleted_at ON outreach_accounts(deleted_at);

-- Creator submissions
CREATE INDEX idx_submissions_va_id ON creator_submissions(submitted_by_va_id, submitted_at);
CREATE INDEX idx_submissions_result ON creator_submissions(result, submitted_at);

-- Creator notes
CREATE INDEX idx_creator_notes_creator_id ON creator_notes(creator_id);

-- Creator status changes
CREATE INDEX idx_status_changes_creator_id ON creator_status_changes(creator_id);

-- =============================================
-- 4. TRIGGER FUNCTION
-- =============================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
CREATE TRIGGER tr_outreach_accounts_updated_at
  BEFORE UPDATE ON outreach_accounts
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER tr_profiles_updated_at
  BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER tr_creators_updated_at
  BEFORE UPDATE ON creators
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 5. ROW LEVEL SECURITY
-- =============================================

ALTER TABLE outreach_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE creators ENABLE ROW LEVEL SECURITY;
ALTER TABLE creator_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE creator_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE creator_status_changes ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------
-- Outreach accounts: authenticated can view active
-- ---------------------------------------------
CREATE POLICY "View active outreach accounts" ON outreach_accounts
  FOR SELECT TO authenticated USING (is_active = true AND deleted_at IS NULL);

-- ---------------------------------------------
-- Profiles: view/update own record
-- ---------------------------------------------
CREATE POLICY "View own profile" ON profiles
  FOR SELECT USING (auth.uid() = auth_user_id);

CREATE POLICY "Update own profile" ON profiles
  FOR UPDATE USING (auth.uid() = auth_user_id);

-- ---------------------------------------------
-- Creators: authenticated can view all (for duplicate checking)
-- ---------------------------------------------
CREATE POLICY "View all creators" ON creators
  FOR SELECT TO authenticated USING (true);

-- ---------------------------------------------
-- Creator submissions: view own
-- ---------------------------------------------
CREATE POLICY "View own submissions" ON creator_submissions
  FOR SELECT USING (
    submitted_by_va_id = (SELECT id FROM profiles WHERE auth_user_id = auth.uid())
  );

-- ---------------------------------------------
-- Creator notes: authenticated can view all
-- ---------------------------------------------
CREATE POLICY "Authenticated users can view notes" ON creator_notes
  FOR SELECT TO authenticated USING (true);

-- ---------------------------------------------
-- Creator status changes: view for creators you have access to
-- ---------------------------------------------
CREATE POLICY "View status changes" ON creator_status_changes
  FOR SELECT TO authenticated USING (true);
