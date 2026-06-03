create table "programs"."program_guides" (
    "id" text not null,
    "title" text not null,
    "subtitle" text not null,
    "content" text not null,
    "published_on" timestamp with time zone not null,
    "thumbnail" text not null
);


alter table "programs"."program_guides" enable row level security;

CREATE UNIQUE INDEX program_guides_pkey ON programs.program_guides USING btree (id);

alter table "programs"."program_guides" add constraint "program_guides_pkey" PRIMARY KEY using index "program_guides_pkey";

create policy "Disable access for all"
on "programs"."program_guides"
as permissive
for all
to public
using (false);



