drop policy "Disable all access" on "comms"."sms_blocked";

create policy "Admin Check"
on "comms"."sms_blocked"
as permissive
for all
to public
using (admin_check());



