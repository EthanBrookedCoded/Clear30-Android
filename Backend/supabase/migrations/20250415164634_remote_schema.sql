set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.payment_check_sale()
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
  active_sale_id bigint;
  sale_exists boolean;
  v_user_id text;
  has_participated boolean;
BEGIN
  -- Get the user's ID using the public.get_user_id function
  v_user_id := public.get_user_id();
  
  -- Throw an error if no user found
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Check if there's an active sale (current date between starts_on and ends_on)
  SELECT id INTO active_sale_id
  FROM payment.sale
  WHERE NOW() BETWEEN starts_on AND ends_on
  LIMIT 1;
  
  -- Determine if a sale exists
  sale_exists := active_sale_id IS NOT NULL;
  
  -- If a sale exists
  IF sale_exists THEN
    -- Increment the uses count
    UPDATE payment.sale
    SET uses = uses + 1
    WHERE id = active_sale_id;
    
    -- Add a record to sale_users table (if it doesn't exist already)
    INSERT INTO payment.sale_users (user_id, sale_id)
    VALUES (v_user_id, active_sale_id)
    ON CONFLICT (user_id, sale_id) DO NOTHING;
    
    RETURN TRUE;
  ELSE
    -- No active sale, check if user has participated in any sale before
    SELECT EXISTS (
      SELECT 1 
      FROM payment.sale_users 
      WHERE user_id = v_user_id
    ) INTO has_participated;
    
    -- Return true if user has participated in a sale before
    RETURN has_participated;
  END IF;
END;$function$
;


