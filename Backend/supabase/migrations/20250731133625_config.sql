create sequence "library"."config_id_seq";

create table "library"."config" (
    "id" bigint not null default nextval('library.config_id_seq'::regclass),
    "app_version" text not null,
    "enabled" boolean not null default true,
    "created_at" timestamp with time zone default now()
);


alter table "library"."config" enable row level security;

alter sequence "library"."config_id_seq" owned by "library"."config"."id";

CREATE UNIQUE INDEX config_pkey ON library.config USING btree (id);

CREATE INDEX idx_config_app_version ON library.config USING btree (app_version);

CREATE UNIQUE INDEX idx_config_app_version_unique ON library.config USING btree (app_version) WHERE (enabled = true);

alter table "library"."config" add constraint "config_pkey" PRIMARY KEY using index "config_pkey";

grant delete on table "library"."config" to "anon";

grant insert on table "library"."config" to "anon";

grant references on table "library"."config" to "anon";

grant select on table "library"."config" to "anon";

grant trigger on table "library"."config" to "anon";

grant truncate on table "library"."config" to "anon";

grant update on table "library"."config" to "anon";

grant delete on table "library"."config" to "authenticated";

grant insert on table "library"."config" to "authenticated";

grant references on table "library"."config" to "authenticated";

grant select on table "library"."config" to "authenticated";

grant trigger on table "library"."config" to "authenticated";

grant truncate on table "library"."config" to "authenticated";

grant update on table "library"."config" to "authenticated";

grant delete on table "library"."config" to "service_role";

grant insert on table "library"."config" to "service_role";

grant references on table "library"."config" to "service_role";

grant select on table "library"."config" to "service_role";

grant trigger on table "library"."config" to "service_role";

grant truncate on table "library"."config" to "service_role";

grant update on table "library"."config" to "service_role";

create policy "Allow read access to config"
on "library"."config"
as permissive
for select
to public
using (true);



