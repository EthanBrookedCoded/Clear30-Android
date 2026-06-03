drop policy "Admin only" on "comms"."feedback";

alter table "comms"."feedback" add column "type" text not null default 'text'::text;

create policy "Admin and own user"
on "comms"."feedback"
as permissive
for select
to public
using ((( SELECT admin_check() AS admin_check) OR (user_id = get_user_id())));



