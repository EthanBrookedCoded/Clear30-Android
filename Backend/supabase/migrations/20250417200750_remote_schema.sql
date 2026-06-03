alter table "comms"."sms_statuses" drop constraint "sms_statuses_message_id_fkey";

alter table "comms"."sms_statuses" add constraint "sms_statuses_message_id_fkey" FOREIGN KEY (message_id) REFERENCES comms.sms_messages(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "comms"."sms_statuses" validate constraint "sms_statuses_message_id_fkey";

grant delete on table "comms"."sms_broadcasts" to "anon";

grant insert on table "comms"."sms_broadcasts" to "anon";

grant references on table "comms"."sms_broadcasts" to "anon";

grant select on table "comms"."sms_broadcasts" to "anon";

grant trigger on table "comms"."sms_broadcasts" to "anon";

grant truncate on table "comms"."sms_broadcasts" to "anon";

grant update on table "comms"."sms_broadcasts" to "anon";

grant delete on table "comms"."sms_broadcasts" to "authenticated";

grant insert on table "comms"."sms_broadcasts" to "authenticated";

grant references on table "comms"."sms_broadcasts" to "authenticated";

grant select on table "comms"."sms_broadcasts" to "authenticated";

grant trigger on table "comms"."sms_broadcasts" to "authenticated";

grant truncate on table "comms"."sms_broadcasts" to "authenticated";

grant update on table "comms"."sms_broadcasts" to "authenticated";

grant delete on table "comms"."sms_broadcasts" to "service_role";

grant insert on table "comms"."sms_broadcasts" to "service_role";

grant references on table "comms"."sms_broadcasts" to "service_role";

grant select on table "comms"."sms_broadcasts" to "service_role";

grant trigger on table "comms"."sms_broadcasts" to "service_role";

grant truncate on table "comms"."sms_broadcasts" to "service_role";

grant update on table "comms"."sms_broadcasts" to "service_role";

grant delete on table "comms"."sms_notify_team_summaries" to "anon";

grant insert on table "comms"."sms_notify_team_summaries" to "anon";

grant references on table "comms"."sms_notify_team_summaries" to "anon";

grant select on table "comms"."sms_notify_team_summaries" to "anon";

grant trigger on table "comms"."sms_notify_team_summaries" to "anon";

grant truncate on table "comms"."sms_notify_team_summaries" to "anon";

grant update on table "comms"."sms_notify_team_summaries" to "anon";

grant delete on table "comms"."sms_notify_team_summaries" to "authenticated";

grant insert on table "comms"."sms_notify_team_summaries" to "authenticated";

grant references on table "comms"."sms_notify_team_summaries" to "authenticated";

grant select on table "comms"."sms_notify_team_summaries" to "authenticated";

grant trigger on table "comms"."sms_notify_team_summaries" to "authenticated";

grant truncate on table "comms"."sms_notify_team_summaries" to "authenticated";

grant update on table "comms"."sms_notify_team_summaries" to "authenticated";


