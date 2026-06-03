set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.rem_group_mem(user_id text, group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Step 1: Remove all activities related to the user and group
    DELETE FROM group_activity ga
    WHERE ga.user_id = rem_group_mem.user_id
      AND ga.group_id = rem_group_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM group_members gm
    WHERE gm.user_id = rem_group_mem.user_id
      AND gm.group_id = rem_group_mem.group_id;

    -- Step 3: Remove all subscriptions for the user and group
    DELETE FROM group_subscriptions gs
    WHERE gs.user_id = rem_group_mem.user_id
      AND gs.group_id = rem_group_mem.group_id;

    -- Step 4: Remove all related group notes (sent or received by the user)
    DELETE FROM group_notes gn
    WHERE gn.group_id = rem_group_mem.group_id
      AND (gn.to_member_id = rem_group_mem.user_id OR gn.from_member_id = rem_group_mem.user_id);

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = rem_group_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM delete_group(rem_group_mem.group_id);
    END IF;
END;$function$
;


