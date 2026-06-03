-- Check if a creator handle is blocked (active/declined) across ALL outreach accounts
CREATE OR REPLACE FUNCTION marketing.check_creator_blocked_status(
  p_platform text,
  p_handle text
)
RETURNS TABLE(
  id uuid,
  status text,
  instagram_handle text,
  tiktok_handle text,
  added_by_profile_id uuid
)
LANGUAGE sql
STABLE SECURITY DEFINER
AS $function$
  SELECT c.id, c.status, c.instagram_handle, c.tiktok_handle, c.added_by_profile_id
  FROM marketing.creators c
  WHERE c.deleted_at IS NULL
    AND c.status IN ('active', 'declined')
    AND (
      (p_platform = 'instagram' AND lower(c.instagram_handle) = lower(p_handle))
      OR
      (p_platform = 'tiktok' AND lower(c.tiktok_handle) = lower(p_handle))
    )
  LIMIT 1;
$function$;
