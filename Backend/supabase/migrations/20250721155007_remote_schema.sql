drop policy "Admin and own user" on "comms"."feedback";

alter table "comms"."feedback" alter column "timestamp" set default now();

create policy "Admin only"
on "comms"."feedback"
as permissive
for select
to public
using (( SELECT admin_check() AS admin_check));



