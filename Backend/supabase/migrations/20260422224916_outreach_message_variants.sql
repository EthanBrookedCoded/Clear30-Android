-- Outreach message A/B variants.
-- Each user can define N message variants per platform. When a creator is added,
-- one active variant is chosen at random and stamped onto creators.outreach_variant_id
-- so results can be analyzed later by variant.

CREATE TABLE marketing.outreach_message_variants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id uuid NOT NULL REFERENCES marketing.profiles(id) ON DELETE CASCADE,
    platform text NOT NULL CHECK (platform IN ('instagram', 'tiktok')),
    label text NOT NULL,
    message text NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX outreach_message_variants_profile_platform_active_idx
    ON marketing.outreach_message_variants (profile_id, platform)
    WHERE is_active;

ALTER TABLE marketing.creators
    ADD COLUMN outreach_variant_id uuid
    REFERENCES marketing.outreach_message_variants(id) ON DELETE SET NULL;

ALTER TABLE marketing.outreach_message_variants ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Can select if role"
    ON marketing.outreach_message_variants
    FOR SELECT
    USING ((SELECT marketing.get_user_role()) IS NOT NULL);

CREATE POLICY "Can insert own"
    ON marketing.outreach_message_variants
    FOR INSERT
    TO authenticated
    WITH CHECK (profile_id = (SELECT marketing.get_profile_id()));

CREATE POLICY "Can update own"
    ON marketing.outreach_message_variants
    FOR UPDATE
    TO authenticated
    USING (profile_id = (SELECT marketing.get_profile_id()))
    WITH CHECK (profile_id = (SELECT marketing.get_profile_id()));

CREATE POLICY "Can delete own"
    ON marketing.outreach_message_variants
    FOR DELETE
    TO authenticated
    USING (profile_id = (SELECT marketing.get_profile_id()));

GRANT ALL ON marketing.outreach_message_variants TO anon, authenticated, service_role;