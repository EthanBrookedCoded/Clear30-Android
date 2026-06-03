create schema if not exists "webapps";

grant usage on schema webapps to "anon";
grant usage on schema webapps to "authenticated";
grant usage on schema webapps to "service_role";

create sequence "webapps"."sup_supplements_id_seq";

create sequence "webapps"."sup_tags_id_seq";

create table "webapps"."sup_supplements" (
    "id" integer not null default nextval('webapps.sup_supplements_id_seq'::regclass),
    "title" character varying(100) not null,
    "tag_names" text[] not null,
    "heading" character varying(200) not null,
    "subheading" text,
    "proof" jsonb not null,
    "instructions" jsonb not null,
    "caution" text,
    "amazon_link" character varying(500),
    "created_at" timestamp without time zone default CURRENT_TIMESTAMP,
    "updated_at" timestamp without time zone default CURRENT_TIMESTAMP
);


alter table "webapps"."sup_supplements" enable row level security;

create table "webapps"."sup_tags" (
    "id" integer not null default nextval('webapps.sup_tags_id_seq'::regclass),
    "name" character varying(50) not null,
    "headline" character varying(100) not null,
    "color" character varying(7) not null
);


alter table "webapps"."sup_tags" enable row level security;

alter sequence "webapps"."sup_supplements_id_seq" owned by "webapps"."sup_supplements"."id";

alter sequence "webapps"."sup_tags_id_seq" owned by "webapps"."sup_tags"."id";

CREATE INDEX idx_sup_supplements_instructions ON webapps.sup_supplements USING gin (instructions);

CREATE INDEX idx_sup_supplements_proof ON webapps.sup_supplements USING gin (proof);

CREATE INDEX idx_sup_supplements_tag_names ON webapps.sup_supplements USING gin (tag_names);

CREATE UNIQUE INDEX sup_supplements_pkey ON webapps.sup_supplements USING btree (id);

CREATE UNIQUE INDEX sup_tags_name_key ON webapps.sup_tags USING btree (name);

CREATE UNIQUE INDEX sup_tags_pkey ON webapps.sup_tags USING btree (id);

alter table "webapps"."sup_supplements" add constraint "sup_supplements_pkey" PRIMARY KEY using index "sup_supplements_pkey";

alter table "webapps"."sup_tags" add constraint "sup_tags_pkey" PRIMARY KEY using index "sup_tags_pkey";

alter table "webapps"."sup_tags" add constraint "sup_tags_name_key" UNIQUE using index "sup_tags_name_key";

grant delete on table "webapps"."sup_supplements" to "anon";

grant insert on table "webapps"."sup_supplements" to "anon";

grant references on table "webapps"."sup_supplements" to "anon";

grant select on table "webapps"."sup_supplements" to "anon";

grant trigger on table "webapps"."sup_supplements" to "anon";

grant truncate on table "webapps"."sup_supplements" to "anon";

grant update on table "webapps"."sup_supplements" to "anon";

grant delete on table "webapps"."sup_supplements" to "authenticated";

grant insert on table "webapps"."sup_supplements" to "authenticated";

grant references on table "webapps"."sup_supplements" to "authenticated";

grant select on table "webapps"."sup_supplements" to "authenticated";

grant trigger on table "webapps"."sup_supplements" to "authenticated";

grant truncate on table "webapps"."sup_supplements" to "authenticated";

grant update on table "webapps"."sup_supplements" to "authenticated";

grant delete on table "webapps"."sup_supplements" to "service_role";

grant insert on table "webapps"."sup_supplements" to "service_role";

grant references on table "webapps"."sup_supplements" to "service_role";

grant select on table "webapps"."sup_supplements" to "service_role";

grant trigger on table "webapps"."sup_supplements" to "service_role";

grant truncate on table "webapps"."sup_supplements" to "service_role";

grant update on table "webapps"."sup_supplements" to "service_role";

grant delete on table "webapps"."sup_tags" to "anon";

grant insert on table "webapps"."sup_tags" to "anon";

grant references on table "webapps"."sup_tags" to "anon";

grant select on table "webapps"."sup_tags" to "anon";

grant trigger on table "webapps"."sup_tags" to "anon";

grant truncate on table "webapps"."sup_tags" to "anon";

grant update on table "webapps"."sup_tags" to "anon";

grant delete on table "webapps"."sup_tags" to "authenticated";

grant insert on table "webapps"."sup_tags" to "authenticated";

grant references on table "webapps"."sup_tags" to "authenticated";

grant select on table "webapps"."sup_tags" to "authenticated";

grant trigger on table "webapps"."sup_tags" to "authenticated";

grant truncate on table "webapps"."sup_tags" to "authenticated";

grant update on table "webapps"."sup_tags" to "authenticated";

grant delete on table "webapps"."sup_tags" to "service_role";

grant insert on table "webapps"."sup_tags" to "service_role";

grant references on table "webapps"."sup_tags" to "service_role";

grant select on table "webapps"."sup_tags" to "service_role";

grant trigger on table "webapps"."sup_tags" to "service_role";

grant truncate on table "webapps"."sup_tags" to "service_role";

grant update on table "webapps"."sup_tags" to "service_role";

create policy "Select for all"
on "webapps"."sup_supplements"
as permissive
for select
to public
using (true);


create policy "Select for all"
on "webapps"."sup_tags"
as permissive
for select
to public
using (true);



