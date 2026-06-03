drop policy "Disable public access" on "library"."downsell_messages";

alter table "library"."downsell_messages" drop constraint "downsell_messages_day_offset_check";

alter table "library"."downsell_messages" drop constraint "downsell_messages_pkey";

drop index if exists "library"."downsell_messages_pkey";

drop index if exists "library"."idx_downsell_messages_day_offset";

drop table "library"."downsell_messages";

drop sequence if exists "library"."downsell_messages_id_seq";

create sequence "library"."sms_downsell_id_seq";

create table "library"."sms_downsell" (
    "id" bigint not null default nextval('library.sms_downsell_id_seq'::regclass),
    "day_offset" integer not null,
    "desc" text not null,
    "message" text not null,
    "created_at" timestamp with time zone not null default now()
);

alter sequence "library"."sms_downsell_id_seq" owned by "library"."sms_downsell"."id";

alter table "library"."sms_downsell" enable row level security;

CREATE UNIQUE INDEX downsell_messages_pkey ON library.sms_downsell USING btree (id);

CREATE INDEX idx_downsell_messages_day_offset ON library.sms_downsell USING btree (day_offset);

alter table "library"."sms_downsell" add constraint "downsell_messages_pkey" PRIMARY KEY using index "downsell_messages_pkey";

alter table "library"."sms_downsell" add constraint "downsell_messages_day_offset_check" CHECK ((day_offset >= 0)) not valid;

alter table "library"."sms_downsell" validate constraint "downsell_messages_day_offset_check";

create policy "Disable public access"
on "library"."sms_downsell"
as permissive
for all
to public
using (false);



