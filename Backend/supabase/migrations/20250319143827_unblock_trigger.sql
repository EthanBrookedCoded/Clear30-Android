CREATE OR REPLACE FUNCTION auth.unblock_phone_number()
RETURNS TRIGGER AS $$
BEGIN
    -- Only proceed if the user has a phone number
    IF NEW.phone IS NULL OR NEW.phone = '' THEN
        RETURN NEW;
    END IF;

    -- Check if the phone number is blocked with a soft block
    DELETE FROM comms.sms_blocked
    WHERE phone_number = NEW.phone AND hard_stop = false;

    -- Return the NEW record to allow the insert to proceed
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create the trigger on the auth.users table
DROP TRIGGER IF EXISTS after_sign_up_unblock ON auth.users;
CREATE TRIGGER after_sign_up_unblock
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.unblock_phone_number();