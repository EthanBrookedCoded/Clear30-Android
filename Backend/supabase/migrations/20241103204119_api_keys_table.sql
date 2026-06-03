create table "public"."api_keys" (
    "id" uuid not null default uuid_generate_v4(),
    "key" text not null,
    "created_at" timestamp with time zone default timezone('utc'::text, now())
);


alter table "public"."api_keys" enable row level security;

CREATE UNIQUE INDEX api_keys_key_key ON public.api_keys USING btree (key);

CREATE UNIQUE INDEX api_keys_pkey ON public.api_keys USING btree (id);

alter table "public"."api_keys" add constraint "api_keys_pkey" PRIMARY KEY using index "api_keys_pkey";

alter table "public"."api_keys" add constraint "api_keys_key_key" UNIQUE using index "api_keys_key_key";

grant delete on table "public"."api_keys" to "anon";

grant insert on table "public"."api_keys" to "anon";

grant references on table "public"."api_keys" to "anon";

grant select on table "public"."api_keys" to "anon";

grant trigger on table "public"."api_keys" to "anon";

grant truncate on table "public"."api_keys" to "anon";

grant update on table "public"."api_keys" to "anon";

grant delete on table "public"."api_keys" to "authenticated";

grant insert on table "public"."api_keys" to "authenticated";

grant references on table "public"."api_keys" to "authenticated";

grant select on table "public"."api_keys" to "authenticated";

grant trigger on table "public"."api_keys" to "authenticated";

grant truncate on table "public"."api_keys" to "authenticated";

grant update on table "public"."api_keys" to "authenticated";

grant delete on table "public"."api_keys" to "service_role";

grant insert on table "public"."api_keys" to "service_role";

grant references on table "public"."api_keys" to "service_role";

grant select on table "public"."api_keys" to "service_role";

grant trigger on table "public"."api_keys" to "service_role";

grant truncate on table "public"."api_keys" to "service_role";

grant update on table "public"."api_keys" to "service_role";

create policy "Disable public access for all users"
on "public"."api_keys"
as permissive
for all
to public
using (false);



