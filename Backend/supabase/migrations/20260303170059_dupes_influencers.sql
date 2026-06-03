-- Drop global unique indexes that prevent duplicates across accounts
DROP INDEX IF EXISTS marketing.idx_creators_instagram_handle;
DROP INDEX IF EXISTS marketing.idx_creators_tiktok_handle;

-- Create composite unique indexes scoped to outreach account
-- This allows the same handle to exist under different outreach accounts
CREATE UNIQUE INDEX idx_creators_instagram_handle_account
  ON marketing.creators (lower(instagram_handle), instagram_outreach_account_id)
  WHERE instagram_handle IS NOT NULL
    AND instagram_outreach_account_id IS NOT NULL
    AND deleted_at IS NULL;

CREATE UNIQUE INDEX idx_creators_tiktok_handle_account
  ON marketing.creators (lower(tiktok_handle), tiktok_outreach_account_id)
  WHERE tiktok_handle IS NOT NULL
    AND tiktok_outreach_account_id IS NOT NULL
    AND deleted_at IS NULL;

-- Update check_creator_exists RPC to support per-account scoping
CREATE OR REPLACE FUNCTION marketing.check_creator_exists(
  p_platform text DEFAULT NULL,
  p_handle text DEFAULT NULL,
  p_outreach_account_id uuid DEFAULT NULL
)
RETURNS TABLE(
  id uuid,
  instagram_handle text,
  tiktok_handle text,
  first_reached_out_at timestamp with time zone,
  added_by_profile_id uuid,
  status text
)
LANGUAGE sql
STABLE SECURITY DEFINER
AS $function$
  SELECT c.id, c.instagram_handle, c.tiktok_handle,
         c.first_reached_out_at, c.added_by_profile_id, c.status
  FROM marketing.creators c
  WHERE c.deleted_at IS NULL
    AND (
      (p_platform = 'instagram'
        AND lower(c.instagram_handle) = lower(p_handle)
        AND (p_outreach_account_id IS NULL OR c.instagram_outreach_account_id = p_outreach_account_id))
      OR
      (p_platform = 'tiktok'
        AND lower(c.tiktok_handle) = lower(p_handle)
        AND (p_outreach_account_id IS NULL OR c.tiktok_outreach_account_id = p_outreach_account_id))
    )
  LIMIT 1;
$function$;
