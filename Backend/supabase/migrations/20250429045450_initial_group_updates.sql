drop policy "Disable access for all users" on "groups"."group_activity";

drop policy "Disable access for all users" on "groups"."group_notes";

drop policy "Disable access for all users" on "groups"."group_pings";

drop policy "Disable access for all users" on "groups"."group_subscriptions";

drop policy "Disable access for all users" on "groups"."groups";

alter table "groups"."group_members" add column "joined_at" timestamp with time zone default now();

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION groups.add_member(group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id, joined_at)
    VALUES (current_user_id, add_member.group_id, NOW())
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (add_member.group_id, current_user_id, 'joined');
END;
$function$
;

CREATE OR REPLACE FUNCTION groups.check_user_group(user_id text, group_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
BEGIN
    -- Check if the user is in the specified group
    RETURN EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.user_id = check_user_group.user_id
        AND gm.group_id = check_user_group.group_id
    );
END;
$function$
;

CREATE OR REPLACE FUNCTION groups.delete(group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public', 'groups'
AS $function$
BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE group_notes.group_id = delete.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE group_activity.group_id = delete.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE group_members.group_id = delete.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.id = delete.group_id;
END;
$function$
;

CREATE OR REPLACE FUNCTION groups.get()
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
DECLARE
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
END;
$function$
;

CREATE OR REPLACE FUNCTION groups.remove_member(user_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
DECLARE
    current_user_id text;
    current_group_id uuid;
BEGIN
    -- Get the current user's ID using public.get_user_id()
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'You must be logged in to remove members';
    END IF;

    -- Get the user's group using the groups.get_user_group() function
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;

    -- Step 1: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = current_group_id
      AND (gn.to_member_id = remove_member.user_id OR gn.from_member_id = remove_member.user_id);

    -- Step 2: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = remove_member.user_id
      AND gs.group_id = current_group_id;

    -- Step 3: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = remove_member.user_id
      AND ga.group_id = current_group_id;

    -- Step 4: Remove the user from the group_members table (do this last)
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = remove_member.user_id
      AND gm.group_id = current_group_id;

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = current_group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM groups.delete(current_group_id);
    END IF;
END;
$function$
;

create policy "Users can add for self"
on "groups"."group_activity"
as permissive
for insert
to public
with check ((( SELECT (get_user_id() = group_activity.user_id)) AND ( SELECT (groups.get_user_group() = group_activity.group_id))));


create policy "Users can read for own group"
on "groups"."group_activity"
as permissive
for select
to public
using (( SELECT (groups.get_user_group() = group_activity.group_id)));


create policy "Users can add for self"
on "groups"."group_notes"
as permissive
for insert
to public
with check ((( SELECT (get_user_id() = group_notes.from_member_id)) AND ( SELECT (groups.get_user_group() = group_notes.group_id)) AND ( SELECT groups.check_user_group(group_notes.to_member_id, group_notes.group_id) AS check_user_group)));


create policy "Users can read own notes"
on "groups"."group_notes"
as permissive
for select
to public
using ((( SELECT (get_user_id() = group_notes.to_member_id)) AND ( SELECT (groups.get_user_group() = group_notes.group_id))));


create policy "Users can add"
on "groups"."group_pings"
as permissive
for insert
to public
with check ((( SELECT (get_user_id() = group_pings.from_user_id)) AND ( SELECT (groups.get_user_group() = group_pings.group_id)) AND ( SELECT groups.check_user_group(group_pings.to_user_id, group_pings.group_id) AS check_user_group)));


create policy "User can add for self"
on "groups"."group_subscriptions"
as permissive
for insert
to public
with check ((( SELECT (get_user_id() = group_subscriptions.user_id)) AND ( SELECT (groups.get_user_group() = group_subscriptions.group_id)) AND ( SELECT groups.check_user_group(group_subscriptions.subscribed_to, group_subscriptions.group_id) AS check_user_group)));


create policy "Insert for authed users"
on "groups"."groups"
as permissive
for insert
to public
with check (( SELECT (get_user_id() IS NOT NULL)));



drop policy "Select to all" on "payment"."one_time_offers";

revoke delete on table "payment"."domain_allowlist" from "anon";

revoke insert on table "payment"."domain_allowlist" from "anon";

revoke references on table "payment"."domain_allowlist" from "anon";

revoke select on table "payment"."domain_allowlist" from "anon";

revoke trigger on table "payment"."domain_allowlist" from "anon";

revoke truncate on table "payment"."domain_allowlist" from "anon";

revoke update on table "payment"."domain_allowlist" from "anon";

revoke delete on table "payment"."domain_allowlist" from "authenticated";

revoke insert on table "payment"."domain_allowlist" from "authenticated";

revoke references on table "payment"."domain_allowlist" from "authenticated";

revoke select on table "payment"."domain_allowlist" from "authenticated";

revoke trigger on table "payment"."domain_allowlist" from "authenticated";

revoke truncate on table "payment"."domain_allowlist" from "authenticated";

revoke update on table "payment"."domain_allowlist" from "authenticated";

revoke delete on table "payment"."domain_allowlist" from "service_role";

revoke insert on table "payment"."domain_allowlist" from "service_role";

revoke references on table "payment"."domain_allowlist" from "service_role";

revoke select on table "payment"."domain_allowlist" from "service_role";

revoke trigger on table "payment"."domain_allowlist" from "service_role";

revoke truncate on table "payment"."domain_allowlist" from "service_role";

revoke update on table "payment"."domain_allowlist" from "service_role";

revoke delete on table "payment"."one_time_offers" from "anon";

revoke insert on table "payment"."one_time_offers" from "anon";

revoke references on table "payment"."one_time_offers" from "anon";

revoke select on table "payment"."one_time_offers" from "anon";

revoke trigger on table "payment"."one_time_offers" from "anon";

revoke truncate on table "payment"."one_time_offers" from "anon";

revoke update on table "payment"."one_time_offers" from "anon";

revoke delete on table "payment"."one_time_offers" from "authenticated";

revoke insert on table "payment"."one_time_offers" from "authenticated";

revoke references on table "payment"."one_time_offers" from "authenticated";

revoke select on table "payment"."one_time_offers" from "authenticated";

revoke trigger on table "payment"."one_time_offers" from "authenticated";

revoke truncate on table "payment"."one_time_offers" from "authenticated";

revoke update on table "payment"."one_time_offers" from "authenticated";

revoke delete on table "payment"."one_time_offers" from "service_role";

revoke insert on table "payment"."one_time_offers" from "service_role";

revoke references on table "payment"."one_time_offers" from "service_role";

revoke select on table "payment"."one_time_offers" from "service_role";

revoke trigger on table "payment"."one_time_offers" from "service_role";

revoke truncate on table "payment"."one_time_offers" from "service_role";

revoke update on table "payment"."one_time_offers" from "service_role";

revoke delete on table "payment"."promo_codes" from "anon";

revoke insert on table "payment"."promo_codes" from "anon";

revoke references on table "payment"."promo_codes" from "anon";

revoke select on table "payment"."promo_codes" from "anon";

revoke trigger on table "payment"."promo_codes" from "anon";

revoke truncate on table "payment"."promo_codes" from "anon";

revoke update on table "payment"."promo_codes" from "anon";

revoke delete on table "payment"."promo_codes" from "authenticated";

revoke insert on table "payment"."promo_codes" from "authenticated";

revoke references on table "payment"."promo_codes" from "authenticated";

revoke select on table "payment"."promo_codes" from "authenticated";

revoke trigger on table "payment"."promo_codes" from "authenticated";

revoke truncate on table "payment"."promo_codes" from "authenticated";

revoke update on table "payment"."promo_codes" from "authenticated";

revoke delete on table "payment"."promo_codes" from "service_role";

revoke insert on table "payment"."promo_codes" from "service_role";

revoke references on table "payment"."promo_codes" from "service_role";

revoke select on table "payment"."promo_codes" from "service_role";

revoke trigger on table "payment"."promo_codes" from "service_role";

revoke truncate on table "payment"."promo_codes" from "service_role";

revoke update on table "payment"."promo_codes" from "service_role";

revoke delete on table "payment"."sale" from "anon";

revoke insert on table "payment"."sale" from "anon";

revoke references on table "payment"."sale" from "anon";

revoke select on table "payment"."sale" from "anon";

revoke trigger on table "payment"."sale" from "anon";

revoke truncate on table "payment"."sale" from "anon";

revoke update on table "payment"."sale" from "anon";

revoke delete on table "payment"."sale" from "authenticated";

revoke insert on table "payment"."sale" from "authenticated";

revoke references on table "payment"."sale" from "authenticated";

revoke select on table "payment"."sale" from "authenticated";

revoke trigger on table "payment"."sale" from "authenticated";

revoke truncate on table "payment"."sale" from "authenticated";

revoke update on table "payment"."sale" from "authenticated";

revoke delete on table "payment"."sale" from "service_role";

revoke insert on table "payment"."sale" from "service_role";

revoke references on table "payment"."sale" from "service_role";

revoke select on table "payment"."sale" from "service_role";

revoke trigger on table "payment"."sale" from "service_role";

revoke truncate on table "payment"."sale" from "service_role";

revoke update on table "payment"."sale" from "service_role";

revoke delete on table "payment"."sale_users" from "anon";

revoke insert on table "payment"."sale_users" from "anon";

revoke references on table "payment"."sale_users" from "anon";

revoke select on table "payment"."sale_users" from "anon";

revoke trigger on table "payment"."sale_users" from "anon";

revoke truncate on table "payment"."sale_users" from "anon";

revoke update on table "payment"."sale_users" from "anon";

revoke delete on table "payment"."sale_users" from "authenticated";

revoke insert on table "payment"."sale_users" from "authenticated";

revoke references on table "payment"."sale_users" from "authenticated";

revoke select on table "payment"."sale_users" from "authenticated";

revoke trigger on table "payment"."sale_users" from "authenticated";

revoke truncate on table "payment"."sale_users" from "authenticated";

revoke update on table "payment"."sale_users" from "authenticated";

revoke delete on table "payment"."sale_users" from "service_role";

revoke insert on table "payment"."sale_users" from "service_role";

revoke references on table "payment"."sale_users" from "service_role";

revoke select on table "payment"."sale_users" from "service_role";

revoke trigger on table "payment"."sale_users" from "service_role";

revoke truncate on table "payment"."sale_users" from "service_role";

revoke update on table "payment"."sale_users" from "service_role";

alter table "payment"."one_time_offers" drop constraint "one_time_offers_pkey";

drop index if exists "payment"."one_time_offers_pkey";

drop table "payment"."one_time_offers";


