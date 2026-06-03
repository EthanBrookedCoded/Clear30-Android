-- Add role column (existing admins default to 'admin')
ALTER TABLE platform.admins
  ADD COLUMN role text NOT NULL DEFAULT 'admin';

ALTER TABLE platform.admins
  ADD CONSTRAINT admins_role_check CHECK (role IN ('admin', 'peer_support'));

-- New function to return caller's role (NULL if not an admin)
CREATE OR REPLACE FUNCTION public.get_admin_role()
RETURNS text
LANGUAGE plpgsql
STABLE SECURITY DEFINER
AS $function$
DECLARE
  uid uuid;
  admin_role text;
BEGIN
  SELECT auth.uid() INTO uid;
  IF uid IS NULL THEN RETURN NULL; END IF;
  SELECT role INTO admin_role FROM platform.admins WHERE auth_id = uid;
  RETURN admin_role;
END;
$function$;
