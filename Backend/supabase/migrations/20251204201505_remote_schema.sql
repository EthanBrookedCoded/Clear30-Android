drop policy "Disable all access" on "payment"."product_discount_codes";

create policy "Enable public access"
on "payment"."product_discount_codes"
as permissive
for select
to public
using (( SELECT (auth.uid() IS NOT NULL)));

