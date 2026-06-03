-- RPC function to update user timezone
-- Called from iOS app on foreground/relevant events to keep timezone current
-- Uses auth.uid() to identify the authenticated user

CREATE OR REPLACE FUNCTION update_user_timezone(p_timezone TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.users
  SET timezone = p_timezone
  WHERE auth_id = auth.uid();
END;
$$;

-- Grant execute to authenticated users
GRANT EXECUTE ON FUNCTION update_user_timezone(TEXT) TO authenticated;
