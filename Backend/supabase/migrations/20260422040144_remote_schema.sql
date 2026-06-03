
  create table "schools"."campus_submissions" (
    "id" uuid not null default gen_random_uuid(),
    "created_at" timestamp with time zone not null default now(),
    "submission" jsonb not null
      );


alter table "schools"."campus_submissions" enable row level security;

CREATE INDEX campus_submissions_created_at_idx ON schools.campus_submissions USING btree (created_at DESC);

CREATE UNIQUE INDEX campus_submissions_pkey ON schools.campus_submissions USING btree (id);

alter table "schools"."campus_submissions" add constraint "campus_submissions_pkey" PRIMARY KEY using index "campus_submissions_pkey";

grant delete on table "schools"."campus_submissions" to "anon";

grant insert on table "schools"."campus_submissions" to "anon";

grant references on table "schools"."campus_submissions" to "anon";

grant select on table "schools"."campus_submissions" to "anon";

grant trigger on table "schools"."campus_submissions" to "anon";

grant truncate on table "schools"."campus_submissions" to "anon";

grant update on table "schools"."campus_submissions" to "anon";

grant delete on table "schools"."campus_submissions" to "authenticated";

grant insert on table "schools"."campus_submissions" to "authenticated";

grant references on table "schools"."campus_submissions" to "authenticated";

grant select on table "schools"."campus_submissions" to "authenticated";

grant trigger on table "schools"."campus_submissions" to "authenticated";

grant truncate on table "schools"."campus_submissions" to "authenticated";

grant update on table "schools"."campus_submissions" to "authenticated";

grant delete on table "schools"."campus_submissions" to "service_role";

grant insert on table "schools"."campus_submissions" to "service_role";

grant references on table "schools"."campus_submissions" to "service_role";

grant select on table "schools"."campus_submissions" to "service_role";

grant trigger on table "schools"."campus_submissions" to "service_role";

grant truncate on table "schools"."campus_submissions" to "service_role";

grant update on table "schools"."campus_submissions" to "service_role";


  create policy "anon can insert"
  on "schools"."campus_submissions"
  as permissive
  for insert
  to public
with check (true);



