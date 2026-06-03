create table "community"."profiles" (
    "id" text not null,
    "name" text not null,
    "emoji" text not null
);


alter table "community"."profiles" enable row level security;

CREATE UNIQUE INDEX profiles_pkey ON community.profiles USING btree (id);

alter table "community"."profiles" add constraint "profiles_pkey" PRIMARY KEY using index "profiles_pkey";

alter table "community"."profiles" add constraint "profiles_id_fkey" FOREIGN KEY (id) REFERENCES users(id) ON UPDATE CASCADE not valid;

alter table "community"."profiles" validate constraint "profiles_id_fkey";

set check_function_bodies = off;

create policy "Public select"
on "community"."profiles"
as permissive
for select
to public
using (true);



