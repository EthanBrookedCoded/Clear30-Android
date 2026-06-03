create table "public"."group_pings" (
    "id" uuid not null default gen_random_uuid(),
    "from_user_id" text not null default now(),
    "to_user_id" text,
    "group_id" uuid not null,
    "timestamp" timestamp with time zone not null default now()
);


alter table "public"."group_pings" enable row level security;

CREATE UNIQUE INDEX group_pings_pkey ON public.group_pings USING btree (id);

alter table "public"."group_pings" add constraint "group_pings_pkey" PRIMARY KEY using index "group_pings_pkey";

alter table "public"."group_pings" add constraint "group_pings_from_user_id_fkey" FOREIGN KEY (from_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_pings" validate constraint "group_pings_from_user_id_fkey";

alter table "public"."group_pings" add constraint "group_pings_group_id_fkey" FOREIGN KEY (group_id) REFERENCES groups(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_pings" validate constraint "group_pings_group_id_fkey";

alter table "public"."group_pings" add constraint "group_pings_to_user_id_fkey" FOREIGN KEY (to_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "public"."group_pings" validate constraint "group_pings_to_user_id_fkey";

grant delete on table "public"."group_pings" to "anon";

grant insert on table "public"."group_pings" to "anon";

grant references on table "public"."group_pings" to "anon";

grant select on table "public"."group_pings" to "anon";

grant trigger on table "public"."group_pings" to "anon";

grant truncate on table "public"."group_pings" to "anon";

grant update on table "public"."group_pings" to "anon";

grant delete on table "public"."group_pings" to "authenticated";

grant insert on table "public"."group_pings" to "authenticated";

grant references on table "public"."group_pings" to "authenticated";

grant select on table "public"."group_pings" to "authenticated";

grant trigger on table "public"."group_pings" to "authenticated";

grant truncate on table "public"."group_pings" to "authenticated";

grant update on table "public"."group_pings" to "authenticated";

grant delete on table "public"."group_pings" to "service_role";

grant insert on table "public"."group_pings" to "service_role";

grant references on table "public"."group_pings" to "service_role";

grant select on table "public"."group_pings" to "service_role";

grant trigger on table "public"."group_pings" to "service_role";

grant truncate on table "public"."group_pings" to "service_role";

grant update on table "public"."group_pings" to "service_role";


