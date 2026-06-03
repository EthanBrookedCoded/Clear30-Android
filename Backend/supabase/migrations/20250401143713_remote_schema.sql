set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.dr_fred_auto_respond()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$DECLARE
  first_message BOOLEAN;
BEGIN
  -- Check if this is the first message from this user (inbound message)
  -- We only want to auto-respond to the first message from each user
  SELECT COUNT(*) = 1 INTO first_message
  FROM comms.dr_fred
  WHERE user_id = NEW.user_id AND outbound = false;
  
  -- If this is the first message from this user and it's inbound (to Dr. Fred)
  IF first_message AND NEW.outbound = false THEN
    -- Insert an automatic response from Dr. Fred
    INSERT INTO comms.dr_fred (user_id, text, outbound)
    VALUES (NEW.user_id, 'Dr. Fred holds a Ph.D. and offers general feedback and support related to taking a break from cannabis. He is not a medical doctor, and this service is not a substitute for medical or mental health treatment. It does not constitute therapy, diagnosis, or medical advice.

Engaging with this service does not create a professional-client or therapist-patient relationship and assumes you are over 18 years old given the terms you accepted. 

This service is intended solely for general guidance and informational purposes. Response times may vary and may take up to 24 hours.

If you are experiencing a mental health crisis, suicidal thoughts, or a medical emergency, call 911 or 988, or seek help from a licensed medical or mental health professional immediately', true);
  END IF;
  
  RETURN NEW;
END;$function$
;


create or replace view "views"."inbound_sms_users" as  SELECT DISTINCT COALESCE(sm.user_id, u.id) AS user_id,
    u.name AS user_name,
    sm.phone_number,
    count(sm.id) AS message_count,
    max(sm.created_at) AS last_message_date
   FROM (comms.sms_messages sm
     LEFT JOIN users u ON (((sm.user_id = u.id) OR (sm.phone_number = u.phone_number))))
  WHERE (sm.outbound = false)
  GROUP BY COALESCE(sm.user_id, u.id), u.name, sm.phone_number
  ORDER BY (max(sm.created_at)) DESC;



