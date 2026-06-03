grant delete on table "library"."sms_schedule" to "service_role";

grant insert on table "library"."sms_schedule" to "service_role";

grant references on table "library"."sms_schedule" to "service_role";

grant select on table "library"."sms_schedule" to "service_role";

grant trigger on table "library"."sms_schedule" to "service_role";

grant truncate on table "library"."sms_schedule" to "service_role";

grant update on table "library"."sms_schedule" to "service_role";

create policy "Disable all access"
on "library"."sms_schedule"
as permissive
for all
to public
using (false);
