create sequence "library"."downsell_messages_id_seq";

create table "library"."downsell_messages" (
    "id" bigint not null default nextval('library.downsell_messages_id_seq'::regclass),
    "day_offset" integer not null,
    "desc" text not null,
    "message" text not null,
    "created_at" timestamp with time zone not null default now()
);


alter table "library"."downsell_messages" enable row level security;

alter sequence "library"."downsell_messages_id_seq" owned by "library"."downsell_messages"."id";

CREATE UNIQUE INDEX downsell_messages_pkey ON library.downsell_messages USING btree (id);

CREATE INDEX idx_downsell_messages_day_offset ON library.downsell_messages USING btree (day_offset);

alter table "library"."downsell_messages" add constraint "downsell_messages_pkey" PRIMARY KEY using index "downsell_messages_pkey";

alter table "library"."downsell_messages" add constraint "downsell_messages_day_offset_check" CHECK ((day_offset >= 0)) not valid;

alter table "library"."downsell_messages" validate constraint "downsell_messages_day_offset_check";

create policy "Disable public access"
on "library"."downsell_messages"
as permissive
for all
to public
using (false);



