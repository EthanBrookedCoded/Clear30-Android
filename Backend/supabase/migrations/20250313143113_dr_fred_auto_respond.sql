-- Create a function that will automatically respond to first-time messages to Dr. Fred
CREATE OR REPLACE FUNCTION comms.dr_fred_auto_respond()
RETURNS TRIGGER AS $$
DECLARE
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
    VALUES (NEW.user_id, 'Dr. Fred is an expert providing feedback and support on your weed break but is not a medical doctor.
This service is intended to offer general guidance and feedback on your experience taking a break from weed - it is not a professional therapy service.
Responses may take up to 24 hours.

If you are experiencing a mental health crisis or medical emergency, immediately contact emergency 911 or 988 services or seek professional care.
This feedback does not constitute medical advice. Always consult a qualified healthcare or mental health professional for personalized advice.', true);
  END IF;
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create a trigger that will fire after each insert on comms.dr_fred
DROP TRIGGER IF EXISTS dr_fred_auto_respond_trigger ON comms.dr_fred;
CREATE TRIGGER dr_fred_auto_respond_trigger
AFTER INSERT ON comms.dr_fred
FOR EACH ROW
EXECUTE FUNCTION comms.dr_fred_auto_respond();
