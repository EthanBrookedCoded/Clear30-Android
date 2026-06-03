drop function if exists "public"."get_group"(group_id uuid, user_id text);

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_group(group_id uuid, user_id text)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- Step 1: Check if the user is part of the group
    IF NOT EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.group_id = get_group.group_id
          AND gm.user_id = get_group.user_id
    ) THEN
        -- Raise a custom exception with a custom SQLSTATE
        RAISE EXCEPTION 'User % is not part of the group %', get_group.user_id, get_group.group_id
            USING ERRCODE = 'P0001';
    END IF;

    -- Step 2: Return group details if the user is part of the group
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
                        'daysSober', COALESCE(
                            (SELECT days_sober FROM programs.convert_day_info_to_legacy(u.day_info)),
                            ARRAY[]::boolean[]
                        ),
                        'initialFrequency', 0.5,
                        'showInRank', u.show_in_group_rank,
                        'startDate', (SELECT start_date FROM programs.convert_day_info_to_legacy(u.day_info)),
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
                        ),
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', date_trunc('second', gn.timestamp)
                                )
                            )
                            FROM groups.group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', date_trunc('second', ga.timestamp)  -- Truncate to seconds
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
                WHERE gs.group_id = get_group.group_id
                AND gs.user_id = get_group.user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = get_group.group_id
    );
END;$function$
;


