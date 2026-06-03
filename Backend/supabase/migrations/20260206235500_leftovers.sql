alter table "schools"."all_universities" drop constraint "all_universities_name_key";

drop index if exists "schools"."all_universities_name_key";

alter table "comms"."peer_messages" alter column "scheduled_for" set default now()::timestamp with time zone;

grant delete on table "library"."sms_start_soon" to "anon";

grant insert on table "library"."sms_start_soon" to "anon";

grant references on table "library"."sms_start_soon" to "anon";

grant trigger on table "library"."sms_start_soon" to "anon";

grant truncate on table "library"."sms_start_soon" to "anon";

grant update on table "library"."sms_start_soon" to "anon";

grant delete on table "library"."sms_start_soon" to "authenticated";

grant insert on table "library"."sms_start_soon" to "authenticated";

grant references on table "library"."sms_start_soon" to "authenticated";

grant trigger on table "library"."sms_start_soon" to "authenticated";

grant truncate on table "library"."sms_start_soon" to "authenticated";

grant update on table "library"."sms_start_soon" to "authenticated";

grant delete on table "library"."sms_start_soon" to "service_role";

grant insert on table "library"."sms_start_soon" to "service_role";

grant references on table "library"."sms_start_soon" to "service_role";

grant trigger on table "library"."sms_start_soon" to "service_role";

grant truncate on table "library"."sms_start_soon" to "service_role";

grant update on table "library"."sms_start_soon" to "service_role";


