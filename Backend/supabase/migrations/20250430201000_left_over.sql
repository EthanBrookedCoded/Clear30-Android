set check_function_bodies = off;

CREATE OR REPLACE FUNCTION groups.add_checkin_activity(activity_type text DEFAULT NULL::text, timezone text DEFAULT 'UTC'::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
DECLARE
    current_user_id text;
    current_group_id uuid;
    activity_timestamp timestamptz := NOW();
    mapped_activity_type group_activity_type;
    activity_date date;
    timezone_valid boolean;
BEGIN
    -- Validate the timezone
    SELECT EXISTS (
        SELECT 1 FROM pg_timezone_names WHERE name = timezone
    ) INTO timezone_valid;
    
    IF NOT timezone_valid THEN
        RAISE EXCEPTION 'Invalid timezone: %', timezone;
    END IF;

    -- Get the current user's ID
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'You must be logged in to record an activity';
    END IF;

    -- Get the user's group
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;
    
    -- Calculate the calendar date in the provided timezone
    activity_date := (activity_timestamp AT TIME ZONE timezone)::date;
    
    -- If activity_type is NULL, just remove all 'smoked' and 'sober' activities for the day
    IF activity_type IS NULL THEN
        DELETE FROM groups.group_activity
        WHERE group_id = current_group_id
          AND user_id = current_user_id
          AND activity IN ('smoked', 'sober')
          AND ((timestamp AT TIME ZONE timezone)::date = activity_date);
          
        -- No new activity to insert, so return
        RETURN;
    END IF;
    
    -- Otherwise, map the activity string to the appropriate activity type
    CASE activity_type
        WHEN 'smoked' THEN mapped_activity_type := 'smoked'::group_activity_type;
        WHEN 'sober' THEN mapped_activity_type := 'sober'::group_activity_type;
        ELSE
            RAISE EXCEPTION 'Invalid activity type: %. Only "smoked", "sober", or NULL are allowed.', activity_type;
    END CASE;
    
    -- For 'smoked' or 'sober' activities, remove conflicting activities on the same calendar day
    DELETE FROM groups.group_activity
    WHERE group_id = current_group_id
      AND user_id = current_user_id
      AND activity IN ('smoked', 'sober')
      AND ((timestamp AT TIME ZONE timezone)::date = activity_date);
    
    -- Insert the new activity
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    VALUES (current_group_id, current_user_id, mapped_activity_type, activity_timestamp);
    
END;
$function$
;

create policy "User can delete for self"
on "groups"."group_subscriptions"
as permissive
for delete
to public
using ((( SELECT (get_user_id() = group_subscriptions.user_id)) AND ( SELECT (groups.get_user_group() = group_subscriptions.group_id)) AND ( SELECT groups.check_user_group(group_subscriptions.subscribed_to, group_subscriptions.group_id) AS check_user_group)));


create policy "Enable update for users based on email"
on "groups"."groups"
as permissive
for update
to public
using (( SELECT (groups.get_user_group() = groups.id)))
with check (( SELECT (groups.get_user_group() = groups.id)));



