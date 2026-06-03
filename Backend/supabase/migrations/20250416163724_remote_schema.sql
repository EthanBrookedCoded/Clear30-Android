alter table "groups"."group_messages" enable row level security;

grant delete on table "groups"."group_activity" to "service_role";

grant insert on table "groups"."group_activity" to "service_role";

grant references on table "groups"."group_activity" to "service_role";

grant select on table "groups"."group_activity" to "service_role";

grant trigger on table "groups"."group_activity" to "service_role";

grant truncate on table "groups"."group_activity" to "service_role";

grant update on table "groups"."group_activity" to "service_role";

grant delete on table "groups"."group_members" to "service_role";

grant insert on table "groups"."group_members" to "service_role";

grant references on table "groups"."group_members" to "service_role";

grant select on table "groups"."group_members" to "service_role";

grant trigger on table "groups"."group_members" to "service_role";

grant truncate on table "groups"."group_members" to "service_role";

grant update on table "groups"."group_members" to "service_role";

grant delete on table "groups"."group_messages" to "anon";

grant insert on table "groups"."group_messages" to "anon";

grant references on table "groups"."group_messages" to "anon";

grant select on table "groups"."group_messages" to "anon";

grant trigger on table "groups"."group_messages" to "anon";

grant truncate on table "groups"."group_messages" to "anon";

grant update on table "groups"."group_messages" to "anon";

grant delete on table "groups"."group_messages" to "authenticated";

grant insert on table "groups"."group_messages" to "authenticated";

grant references on table "groups"."group_messages" to "authenticated";

grant select on table "groups"."group_messages" to "authenticated";

grant trigger on table "groups"."group_messages" to "authenticated";

grant truncate on table "groups"."group_messages" to "authenticated";

grant update on table "groups"."group_messages" to "authenticated";

grant delete on table "groups"."group_messages" to "service_role";

grant insert on table "groups"."group_messages" to "service_role";

grant references on table "groups"."group_messages" to "service_role";

grant select on table "groups"."group_messages" to "service_role";

grant trigger on table "groups"."group_messages" to "service_role";

grant truncate on table "groups"."group_messages" to "service_role";

grant update on table "groups"."group_messages" to "service_role";

grant delete on table "groups"."group_notes" to "service_role";

grant insert on table "groups"."group_notes" to "service_role";

grant references on table "groups"."group_notes" to "service_role";

grant select on table "groups"."group_notes" to "service_role";

grant trigger on table "groups"."group_notes" to "service_role";

grant truncate on table "groups"."group_notes" to "service_role";

grant update on table "groups"."group_notes" to "service_role";

grant delete on table "groups"."group_pings" to "service_role";

grant insert on table "groups"."group_pings" to "service_role";

grant references on table "groups"."group_pings" to "service_role";

grant select on table "groups"."group_pings" to "service_role";

grant trigger on table "groups"."group_pings" to "service_role";

grant truncate on table "groups"."group_pings" to "service_role";

grant update on table "groups"."group_pings" to "service_role";

grant delete on table "groups"."group_subscriptions" to "service_role";

grant insert on table "groups"."group_subscriptions" to "service_role";

grant references on table "groups"."group_subscriptions" to "service_role";

grant select on table "groups"."group_subscriptions" to "service_role";

grant trigger on table "groups"."group_subscriptions" to "service_role";

grant truncate on table "groups"."group_subscriptions" to "service_role";

grant update on table "groups"."group_subscriptions" to "service_role";

grant delete on table "groups"."groups" to "service_role";

grant insert on table "groups"."groups" to "service_role";

grant references on table "groups"."groups" to "service_role";

grant select on table "groups"."groups" to "service_role";

grant trigger on table "groups"."groups" to "service_role";

grant truncate on table "groups"."groups" to "service_role";

grant update on table "groups"."groups" to "service_role";

create policy "Enable all access"
on "groups"."group_messages"
as permissive
for select
to public
using (true);



CREATE TRIGGER revenue_cat_send_amplitude_id AFTER INSERT ON public.users FOR EACH ROW EXECUTE FUNCTION supabase_functions.http_request('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/revenue_cat_set_amplitude_id', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '10000');


