set check_function_bodies = off;

CREATE OR REPLACE FUNCTION groups.get()
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$DECLARE
    current_user_id text;
    current_group_id uuid;
BEGIN
    -- Get the current user's ID using public.get_user_id()
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Get the user's group using the groups.get_user_group() function
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;

    -- Return group details for the user's group
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'showInRank', u.show_in_group_rank,
                        'joinDate', gm.joined_at,
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        )
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'notes', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'fromMemberID', gn.from_member_id,
                        'message', gn.message,
                        'timestamp', gn.timestamp
                    )
                )
                FROM groups.group_notes gn
                WHERE gn.to_member_id = current_user_id
                AND gn.group_id = g.id
            ), '[]'::json),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'id', ga.id,
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', TO_CHAR(ga.timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"+0000"')
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json),
            'subscribed', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'subscribedTo', gs.subscribed_to
                    )
                ), '[]'::json)
                FROM groups.group_subscriptions gs
                WHERE gs.group_id = current_group_id
                AND gs.user_id = current_user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = current_group_id
    );
END;$function$
;


