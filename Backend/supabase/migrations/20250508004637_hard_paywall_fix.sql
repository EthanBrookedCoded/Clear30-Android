create policy "Access to all"
on "payment"."hard_paywalls"
as permissive
for select
to public
using (true);



