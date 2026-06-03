-- Optimize get_user_id function for RLS performance
-- 1. Mark as STABLE so PostgreSQL can cache the result within a query
-- 2. For new users, user_id = auth_id, so check users table only for legacy mapping

CREATE OR REPLACE FUNCTION public.get_user_id()
RETURNS text
LANGUAGE plpgsql
STABLE
AS $function$
DECLARE
  auth_uuid uuid;
  legacy_id text;
BEGIN
  auth_uuid := auth.uid();

  IF auth_uuid IS NULL THEN
    RETURN NULL;
  END IF;

  -- Check if legacy user exists with different user_id
  SELECT id INTO legacy_id
  FROM users
  WHERE auth_id = auth_uuid
    AND id != auth_uuid::text;

  -- Return legacy_id if found, otherwise auth_uuid (new user format)
  RETURN COALESCE(legacy_id, auth_uuid::text);
END;
$function$;

-- Optimize admin_check function for RLS performance
-- Mark as STABLE so PostgreSQL can cache the result within a query

CREATE OR REPLACE FUNCTION public.admin_check()
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $function$
DECLARE
  uid uuid;
  is_admin boolean;
BEGIN
  SELECT auth.uid() INTO uid;

  IF uid IS NULL THEN
    RETURN false;
  END IF;

  SELECT EXISTS(SELECT 1 FROM platform.admins WHERE auth_id = uid) INTO is_admin;
  RETURN is_admin;
END;
$function$;
