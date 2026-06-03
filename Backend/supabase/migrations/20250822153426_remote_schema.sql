drop policy "Select for all" on "webapps"."sup_supplements";

alter table "webapps"."sup_supplements" add column "active" boolean not null default true;

create policy "Select for all"
on "webapps"."sup_supplements"
as permissive
for select
to public
using ((active = true));



