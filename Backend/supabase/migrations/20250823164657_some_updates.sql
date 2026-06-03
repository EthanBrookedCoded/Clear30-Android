alter table "programs"."program_pop_ups" drop column "sheet_data";

alter table "programs"."program_pop_ups" add column "popup" jsonb not null;

grant delete on table "programs"."program_pop_ups" to "anon";

grant insert on table "programs"."program_pop_ups" to "anon";

grant references on table "programs"."program_pop_ups" to "anon";

grant select on table "programs"."program_pop_ups" to "anon";

grant trigger on table "programs"."program_pop_ups" to "anon";

grant truncate on table "programs"."program_pop_ups" to "anon";

grant update on table "programs"."program_pop_ups" to "anon";

grant delete on table "programs"."program_pop_ups" to "authenticated";

grant insert on table "programs"."program_pop_ups" to "authenticated";

grant references on table "programs"."program_pop_ups" to "authenticated";

grant select on table "programs"."program_pop_ups" to "authenticated";

grant trigger on table "programs"."program_pop_ups" to "authenticated";

grant truncate on table "programs"."program_pop_ups" to "authenticated";

grant update on table "programs"."program_pop_ups" to "authenticated";

grant delete on table "programs"."program_pop_ups" to "service_role";

grant insert on table "programs"."program_pop_ups" to "service_role";

grant references on table "programs"."program_pop_ups" to "service_role";

grant select on table "programs"."program_pop_ups" to "service_role";

grant trigger on table "programs"."program_pop_ups" to "service_role";

grant truncate on table "programs"."program_pop_ups" to "service_role";

grant update on table "programs"."program_pop_ups" to "service_role";

create policy "Select to authed"
on "programs"."program_pop_ups"
as permissive
for select
to public
using ((( SELECT auth.uid() AS uid) IS NOT NULL));



