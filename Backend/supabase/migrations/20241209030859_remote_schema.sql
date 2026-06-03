alter table "comms"."sms_messages" alter column "user_id" drop default;

alter table "comms"."sms_messages" alter column "user_id" drop not null;

alter table "comms"."sms_messages" enable row level security;

alter table "comms"."sms_statuses" enable row level security;

create policy "Disable access for all"
on "comms"."sms_messages"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "comms"."sms_statuses"
as permissive
for all
to public
using (false);



