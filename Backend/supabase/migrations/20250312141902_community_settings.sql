create table "community"."settings" (
    "id" text not null,
    "value" text not null,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone
);


alter table "community"."settings" enable row level security;

CREATE UNIQUE INDEX settings_pkey ON community.settings USING btree (id);

alter table "community"."settings" add constraint "settings_pkey" PRIMARY KEY using index "settings_pkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION community.increment_view_count(post_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    multiplier INTEGER := 1;
BEGIN
    -- Check if view-multiplier setting exists
    SELECT 
        CASE 
            WHEN value ~ '^[0-9]+$' THEN value::INTEGER 
            ELSE 1 
        END INTO multiplier
    FROM community.settings
    WHERE id = 'view-multiplier'
    LIMIT 1;
    
    -- If no valid multiplier found, default to 1
    IF multiplier IS NULL OR multiplier < 1 THEN
        multiplier := 1;
    END IF;
    
    -- Update the post view count using the multiplier
    UPDATE community.posts 
    SET view_count = view_count + multiplier 
    WHERE id = post_id;
END;
$function$
;

grant select on table "community"."settings" to "anon";

grant select on table "community"."settings" to "authenticated";

grant delete on table "community"."settings" to "service_role";

grant insert on table "community"."settings" to "service_role";

grant references on table "community"."settings" to "service_role";

grant select on table "community"."settings" to "service_role";

grant trigger on table "community"."settings" to "service_role";

grant truncate on table "community"."settings" to "service_role";

grant update on table "community"."settings" to "service_role";

create policy "Disable all access"
on "community"."settings"
as permissive
for all
to public
using (false);



