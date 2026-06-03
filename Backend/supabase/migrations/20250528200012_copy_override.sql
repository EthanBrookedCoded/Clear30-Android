create table "library"."general_copy" (
    "id" text not null,
    "copy" text not null,
    "updated_at" timestamp with time zone not null default now()
);


alter table "library"."general_copy" enable row level security;

CREATE UNIQUE INDEX general_copy_pkey ON library.general_copy USING btree (id);

alter table "library"."general_copy" add constraint "general_copy_pkey" PRIMARY KEY using index "general_copy_pkey";

create policy "All can access"
on "library"."general_copy"
as permissive
for select
to public
using (true);



