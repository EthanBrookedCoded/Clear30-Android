drop policy "Disable all access" on "library"."sms_schedule";

revoke delete on table "library"."sms_schedule" from "authenticated";

revoke insert on table "library"."sms_schedule" from "authenticated";

revoke references on table "library"."sms_schedule" from "authenticated";

revoke select on table "library"."sms_schedule" from "authenticated";

revoke trigger on table "library"."sms_schedule" from "authenticated";

revoke truncate on table "library"."sms_schedule" from "authenticated";

revoke update on table "library"."sms_schedule" from "authenticated";

revoke delete on table "library"."sms_schedule" from "service_role";

revoke insert on table "library"."sms_schedule" from "service_role";

revoke references on table "library"."sms_schedule" from "service_role";

revoke select on table "library"."sms_schedule" from "service_role";

revoke trigger on table "library"."sms_schedule" from "service_role";

revoke truncate on table "library"."sms_schedule" from "service_role";

revoke update on table "library"."sms_schedule" from "service_role";

alter table "library"."sms_schedule" drop constraint "sms_schedule_pkey";

drop index if exists "library"."sms_schedule_pkey";

drop table "library"."sms_schedule";

create policy "Disable all access"
on "library"."sms_eow_summary"
as permissive
for all
to public
using (false);


create policy "Disable all access"
on "library"."sms_inactive"
as permissive
for all
to public
using (false);



