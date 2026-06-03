drop policy "Select to authed" on "library"."support_items";

alter table "library"."support_items" add column "active" boolean not null default true;

create policy "Select to authed"
on "library"."support_items"
as permissive
for select
to public
using (((( SELECT auth.uid() AS uid) IS NOT NULL) AND (active = true)));



