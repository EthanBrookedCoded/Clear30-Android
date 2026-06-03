create table "public"."group_subscriptions" (
    "id" uuid not null default gen_random_uuid(),
    "group_id" uuid not null,
    "user_id" text not null,
    "subscribed_to" text not null,
    "timestamp" timestamp with time zone not null default now()
);


alter table "public"."group_subscriptions" enable row level security;

CREATE UNIQUE INDEX group_subscriptions_pkey ON public.group_subscriptions USING btree (id);

alter table "public"."group_subscriptions" add constraint "group_subscriptions_pkey" PRIMARY KEY using index "group_subscriptions_pkey";

alter table "public"."group_subscriptions" add constraint "group_subscriptions_group_id_fkey" FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_subscriptions" validate constraint "group_subscriptions_group_id_fkey";

alter table "public"."group_subscriptions" add constraint "group_subscriptions_subscribed_to_fkey" FOREIGN KEY (subscribed_to) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_subscriptions" validate constraint "group_subscriptions_subscribed_to_fkey";

alter table "public"."group_subscriptions" add constraint "group_subscriptions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_subscriptions" validate constraint "group_subscriptions_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.update_group_subscriptions(group_id uuid, user_id text, subscriptions jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$BEGIN
    -- 1. Check if the user is in the group
    IF NOT EXISTS (
        SELECT 1 
        FROM group_members
        WHERE group_members.group_id = update_group_subscriptions.group_id
        AND group_members.user_id = update_group_subscriptions.user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of the group';
    END IF;

    -- 2. Remove all existing rows in group_subscriptions for the group_id and user_id
    DELETE FROM group_subscriptions
    WHERE group_subscriptions.group_id = update_group_subscriptions.group_id
    AND group_subscriptions.user_id = update_group_subscriptions.user_id;

    -- 3. Insert new subscriptions into group_subscriptions
    INSERT INTO group_subscriptions (group_id, user_id, subscribed_to)
    SELECT update_group_subscriptions.group_id, update_group_subscriptions.user_id, value::TEXT
    FROM jsonb_array_elements_text(update_group_subscriptions.subscriptions) AS value;

END;$function$
;

grant delete on table "public"."group_subscriptions" to "anon";

grant insert on table "public"."group_subscriptions" to "anon";

grant references on table "public"."group_subscriptions" to "anon";

grant select on table "public"."group_subscriptions" to "anon";

grant trigger on table "public"."group_subscriptions" to "anon";

grant truncate on table "public"."group_subscriptions" to "anon";

grant update on table "public"."group_subscriptions" to "anon";

grant delete on table "public"."group_subscriptions" to "authenticated";

grant insert on table "public"."group_subscriptions" to "authenticated";

grant references on table "public"."group_subscriptions" to "authenticated";

grant select on table "public"."group_subscriptions" to "authenticated";

grant trigger on table "public"."group_subscriptions" to "authenticated";

grant truncate on table "public"."group_subscriptions" to "authenticated";

grant update on table "public"."group_subscriptions" to "authenticated";

grant delete on table "public"."group_subscriptions" to "service_role";

grant insert on table "public"."group_subscriptions" to "service_role";

grant references on table "public"."group_subscriptions" to "service_role";

grant select on table "public"."group_subscriptions" to "service_role";

grant trigger on table "public"."group_subscriptions" to "service_role";

grant truncate on table "public"."group_subscriptions" to "service_role";

grant update on table "public"."group_subscriptions" to "service_role";

create policy "No public access"
on "public"."group_subscriptions"
as permissive
for all
to public
using (false);



