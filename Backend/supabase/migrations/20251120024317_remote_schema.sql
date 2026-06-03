create policy "Admin only"
on "payment"."domain_allowlist"
as permissive
for select
to public
using (( SELECT admin_check() AS admin_check));



