set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.add_group_ping(group_id uuid, from_user_id text, to_user_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    IF EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.to_user_id
    )
    AND EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.from_user_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO group_pings (group_id, from_user_id, to_user_id)
        VALUES (add_group_ping.group_id, add_group_ping.from_user_id, add_group_ping.to_user_id);
    END IF;
END;$function$
;