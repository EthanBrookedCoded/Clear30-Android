drop policy "Select to authed" on "library"."support_items";

create policy "Select to authed"
on "library"."support_items"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));



