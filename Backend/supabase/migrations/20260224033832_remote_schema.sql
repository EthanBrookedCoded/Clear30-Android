drop policy "portal_admins_delete_members" on "schools"."portal_users";

drop policy "portal_admins_insert_members" on "schools"."portal_users";

drop policy "portal_users_select_own_school_members" on "schools"."portal_users";

drop policy "portal_users_update_members" on "schools"."portal_users";

drop policy "portal_users_select" on "schools"."portal_users";

drop policy "portal_users_update" on "schools"."portal_users";

grant delete on table "schools"."support_tickets" to "anon";

grant insert on table "schools"."support_tickets" to "anon";

grant references on table "schools"."support_tickets" to "anon";

grant select on table "schools"."support_tickets" to "anon";

grant trigger on table "schools"."support_tickets" to "anon";

grant truncate on table "schools"."support_tickets" to "anon";

grant update on table "schools"."support_tickets" to "anon";

grant delete on table "schools"."support_tickets" to "authenticated";

grant insert on table "schools"."support_tickets" to "authenticated";

grant references on table "schools"."support_tickets" to "authenticated";

grant select on table "schools"."support_tickets" to "authenticated";

grant trigger on table "schools"."support_tickets" to "authenticated";

grant truncate on table "schools"."support_tickets" to "authenticated";

grant update on table "schools"."support_tickets" to "authenticated";

grant delete on table "schools"."support_tickets" to "service_role";

grant insert on table "schools"."support_tickets" to "service_role";

grant references on table "schools"."support_tickets" to "service_role";

grant select on table "schools"."support_tickets" to "service_role";

grant trigger on table "schools"."support_tickets" to "service_role";

grant truncate on table "schools"."support_tickets" to "service_role";

grant update on table "schools"."support_tickets" to "service_role";


  create policy "portal_users_delete"
  on "schools"."portal_users"
  as permissive
  for delete
  to public
using (((school_id = schools.get_portal_school_id()) OR schools.is_platform_admin()));



  create policy "portal_users_insert"
  on "schools"."portal_users"
  as permissive
  for insert
  to public
with check (((school_id = schools.get_portal_school_id()) OR schools.is_platform_admin()));



  create policy "portal_users_select"
  on "schools"."portal_users"
  as permissive
  for select
  to public
using (((auth_id = auth.uid()) OR (school_id = schools.get_portal_school_id()) OR ((auth_id IS NULL) AND (lower(email) = lower((auth.jwt() ->> 'email'::text)))) OR schools.is_platform_admin()));



  create policy "portal_users_update"
  on "schools"."portal_users"
  as permissive
  for update
  to public
using (((auth_id = auth.uid()) OR ((auth_id IS NULL) AND (lower(email) = lower((auth.jwt() ->> 'email'::text)))) OR schools.is_platform_admin()));



