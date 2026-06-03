alter table "marketing"."creators" drop constraint "creators_platform_check";

alter table "marketing"."creators" drop constraint "creators_sourced_via_outreach_account_id_fkey";

alter table "marketing"."creators" drop constraint "unique_creator_key";

drop function if exists "marketing"."check_creator_exists"(p_creator_key text);

drop index if exists "marketing"."idx_mk_creators_creator_key";

drop index if exists "marketing"."idx_mk_creators_platform_handle";

drop index if exists "marketing"."unique_creator_key";

alter table "marketing"."creators" drop column "creator_key";

alter table "marketing"."creators" drop column "handle";

alter table "marketing"."creators" drop column "platform";

alter table "marketing"."creators" drop column "primary_profile_url";

alter table "marketing"."creators" drop column "sourced_via_outreach_account_id";

alter table "marketing"."creators" add column "instagram_handle" text;

alter table "marketing"."creators" add column "instagram_outreach_account_id" uuid;

alter table "marketing"."creators" add column "instagram_url" text;

alter table "marketing"."creators" add column "tiktok_handle" text;

alter table "marketing"."creators" add column "tiktok_outreach_account_id" uuid;

alter table "marketing"."creators" add column "tiktok_url" text;

CREATE UNIQUE INDEX idx_creators_instagram_handle ON marketing.creators USING btree (lower(instagram_handle)) WHERE ((instagram_handle IS NOT NULL) AND (deleted_at IS NULL));

CREATE UNIQUE INDEX idx_creators_tiktok_handle ON marketing.creators USING btree (lower(tiktok_handle)) WHERE ((tiktok_handle IS NOT NULL) AND (deleted_at IS NULL));

alter table "marketing"."creators" add constraint "creators_has_handle_check" CHECK (((instagram_handle IS NOT NULL) OR (tiktok_handle IS NOT NULL))) not valid;

alter table "marketing"."creators" validate constraint "creators_has_handle_check";

alter table "marketing"."creators" add constraint "creators_instagram_outreach_account_id_fkey" FOREIGN KEY (instagram_outreach_account_id) REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL not valid;

alter table "marketing"."creators" validate constraint "creators_instagram_outreach_account_id_fkey";

alter table "marketing"."creators" add constraint "creators_tiktok_outreach_account_id_fkey" FOREIGN KEY (tiktok_outreach_account_id) REFERENCES marketing.outreach_accounts(id) ON DELETE SET NULL not valid;

alter table "marketing"."creators" validate constraint "creators_tiktok_outreach_account_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION marketing.add_platform_to_creator(p_creator_id uuid, p_platform text, p_handle text, p_url text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
  IF p_platform = 'instagram' THEN
    UPDATE marketing.creators
    SET instagram_handle = p_handle, instagram_url = p_url, updated_at = now()
    WHERE id = p_creator_id AND deleted_at IS NULL AND instagram_handle IS NULL;
  ELSIF p_platform = 'tiktok' THEN
    UPDATE marketing.creators
    SET tiktok_handle = p_handle, tiktok_url = p_url, updated_at = now()
    WHERE id = p_creator_id AND deleted_at IS NULL AND tiktok_handle IS NULL;
  END IF;
END;
$function$
;

CREATE OR REPLACE FUNCTION marketing.check_creator_exists(p_platform text, p_handle text)
 RETURNS TABLE(id uuid, instagram_handle text, tiktok_handle text, first_reached_out_at timestamp with time zone, added_by_profile_id uuid, status text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  SELECT c.id, c.instagram_handle, c.tiktok_handle,
         c.first_reached_out_at, c.added_by_profile_id, c.status
  FROM marketing.creators c
  WHERE c.deleted_at IS NULL
    AND (
      (p_platform = 'instagram' AND lower(c.instagram_handle) = lower(p_handle))
      OR (p_platform = 'tiktok' AND lower(c.tiktok_handle) = lower(p_handle))
    )
  LIMIT 1
$function$
;


