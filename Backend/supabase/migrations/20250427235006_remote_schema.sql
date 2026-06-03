drop policy "Disable access for all" on "library"."one_offs";

create policy "Select to all"
on "library"."one_offs"
as permissive
for select
to public
using (true);



drop function if exists "public"."get_payment_settings"();


