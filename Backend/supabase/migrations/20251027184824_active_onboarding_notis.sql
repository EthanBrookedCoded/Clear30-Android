drop policy "Access to all" on "library"."push_abandoned_onboarding";

alter table "library"."push_abandoned_onboarding" add column "active" boolean not null default true;

create policy "Access to all"
on "library"."push_abandoned_onboarding"
as permissive
for select
to public
using (active);



