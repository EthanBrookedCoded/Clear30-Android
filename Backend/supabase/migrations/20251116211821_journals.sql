create schema if not exists "journal";

create sequence "journal"."journal_entries_id_seq";

create table "journal"."journal_entries" (
    "id" integer not null default nextval('journal.journal_entries_id_seq'::regclass),
    "user_id" text not null,
    "title" text not null,
    "content" text not null,
    "is_video" boolean default false,
    "date" timestamp with time zone not null,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now()
);


alter table "journal"."journal_entries" enable row level security;

alter sequence "journal"."journal_entries_id_seq" owned by "journal"."journal_entries"."id";

CREATE INDEX idx_journal_entries_created_at ON journal.journal_entries USING btree (created_at DESC);

CREATE INDEX idx_journal_entries_date ON journal.journal_entries USING btree (date DESC);

CREATE INDEX idx_journal_entries_user_id ON journal.journal_entries USING btree (user_id);

CREATE UNIQUE INDEX journal_entries_pkey ON journal.journal_entries USING btree (id);

alter table "journal"."journal_entries" add constraint "journal_entries_pkey" PRIMARY KEY using index "journal_entries_pkey";

alter table "journal"."journal_entries" add constraint "journal_entries_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "journal"."journal_entries" validate constraint "journal_entries_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION journal.update_updated_at_column()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$function$
;

grant delete on table "journal"."journal_entries" to "authenticated";

grant insert on table "journal"."journal_entries" to "authenticated";

grant references on table "journal"."journal_entries" to "authenticated";

grant select on table "journal"."journal_entries" to "authenticated";

grant trigger on table "journal"."journal_entries" to "authenticated";

grant truncate on table "journal"."journal_entries" to "authenticated";

grant update on table "journal"."journal_entries" to "authenticated";

create policy "Users can delete their own journal entries"
on "journal"."journal_entries"
as permissive
for delete
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can insert their own journal entries"
on "journal"."journal_entries"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can update their own journal entries"
on "journal"."journal_entries"
as permissive
for update
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can view their own journal entries"
on "journal"."journal_entries"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


CREATE TRIGGER update_journal_entries_updated_at BEFORE UPDATE ON journal.journal_entries FOR EACH ROW EXECUTE FUNCTION journal.update_updated_at_column();


grant usage on schema "journal" to "anon";
grant usage on schema "journal" to "authenticated";
grant usage on schema "journal" to "service_role";