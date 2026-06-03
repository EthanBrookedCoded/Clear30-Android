drop policy "Stats are viewable by authenticated users" on "achievements"."stats";

alter table "achievements"."stats" add column "active" boolean not null default true;

create policy "Access to authed (& active)"
on "achievements"."stats"
as permissive
for select
to public
using (((auth.uid() IS NOT NULL) AND (active IS TRUE)));



