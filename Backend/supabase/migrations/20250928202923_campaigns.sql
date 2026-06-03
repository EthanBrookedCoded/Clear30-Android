create table "library"."campaigns" (
    "id" text not null,
    "active" boolean not null default true,
    "created_at" timestamp with time zone not null default now()
);


alter table "library"."campaigns" enable row level security;

CREATE UNIQUE INDEX campaigns_pkey ON library.campaigns USING btree (id);

alter table "library"."campaigns" add constraint "campaigns_pkey" PRIMARY KEY using index "campaigns_pkey";

grant delete on table "library"."campaigns" to "anon";

grant insert on table "library"."campaigns" to "anon";

grant references on table "library"."campaigns" to "anon";

grant select on table "library"."campaigns" to "anon";

grant trigger on table "library"."campaigns" to "anon";

grant truncate on table "library"."campaigns" to "anon";

grant update on table "library"."campaigns" to "anon";

grant delete on table "library"."campaigns" to "authenticated";

grant insert on table "library"."campaigns" to "authenticated";

grant references on table "library"."campaigns" to "authenticated";

grant select on table "library"."campaigns" to "authenticated";

grant trigger on table "library"."campaigns" to "authenticated";

grant truncate on table "library"."campaigns" to "authenticated";

grant update on table "library"."campaigns" to "authenticated";

grant delete on table "library"."campaigns" to "service_role";

grant insert on table "library"."campaigns" to "service_role";

grant references on table "library"."campaigns" to "service_role";

grant select on table "library"."campaigns" to "service_role";

grant trigger on table "library"."campaigns" to "service_role";

grant truncate on table "library"."campaigns" to "service_role";

grant update on table "library"."campaigns" to "service_role";


