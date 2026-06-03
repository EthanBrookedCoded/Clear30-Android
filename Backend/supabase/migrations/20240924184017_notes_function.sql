set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.add_group_note(group_id uuid, to_member_id text, from_member_id text, message text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Check if both to_member_id and from_member_id are in the group
    IF EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.to_member_id
    )
    AND EXISTS (
        SELECT 1 FROM group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.from_member_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO group_notes (group_id, to_member_id, from_member_id, message, timestamp)
        VALUES (add_group_note.group_id, add_group_note.to_member_id, add_group_note.from_member_id, add_group_note.message, NOW());

        -- Insert a new row into the group_activity table with the "note" activity
        INSERT INTO group_activity (group_id, user_id, activity)
        VALUES (add_group_note.group_id, add_group_note.from_member_id, 'message');
    END IF;
END;$function$
;


