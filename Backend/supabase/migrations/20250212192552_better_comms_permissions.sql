grant delete on table "comms"."dr_fred" to "authenticated";

grant references on table "comms"."dr_fred" to "authenticated";

grant trigger on table "comms"."dr_fred" to "authenticated";

grant truncate on table "comms"."dr_fred" to "authenticated";

grant delete on table "comms"."dr_fred" to "service_role";

grant insert on table "comms"."dr_fred" to "service_role";

grant references on table "comms"."dr_fred" to "service_role";

grant select on table "comms"."dr_fred" to "service_role";

grant trigger on table "comms"."dr_fred" to "service_role";

grant truncate on table "comms"."dr_fred" to "service_role";

grant update on table "comms"."dr_fred" to "service_role";

grant delete on table "comms"."sms_messages" to "authenticated";

grant references on table "comms"."sms_messages" to "authenticated";

grant trigger on table "comms"."sms_messages" to "authenticated";

grant truncate on table "comms"."sms_messages" to "authenticated";

grant delete on table "comms"."sms_statuses" to "authenticated";

grant references on table "comms"."sms_statuses" to "authenticated";

grant trigger on table "comms"."sms_statuses" to "authenticated";

grant truncate on table "comms"."sms_statuses" to "authenticated";


grant usage on schema "comms" to "service_role";
grant usage on schema "comms" to "authenticated";