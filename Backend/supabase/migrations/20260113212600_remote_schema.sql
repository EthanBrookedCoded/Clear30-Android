drop policy "Select for all" on "webapps"."sup_supplements";

drop policy "Select for all" on "webapps"."sup_tags";

revoke delete on table "webapps"."sup_supplements" from "anon";

revoke insert on table "webapps"."sup_supplements" from "anon";

revoke references on table "webapps"."sup_supplements" from "anon";

revoke select on table "webapps"."sup_supplements" from "anon";

revoke trigger on table "webapps"."sup_supplements" from "anon";

revoke truncate on table "webapps"."sup_supplements" from "anon";

revoke update on table "webapps"."sup_supplements" from "anon";

revoke delete on table "webapps"."sup_supplements" from "authenticated";

revoke insert on table "webapps"."sup_supplements" from "authenticated";

revoke references on table "webapps"."sup_supplements" from "authenticated";

revoke select on table "webapps"."sup_supplements" from "authenticated";

revoke trigger on table "webapps"."sup_supplements" from "authenticated";

revoke truncate on table "webapps"."sup_supplements" from "authenticated";

revoke update on table "webapps"."sup_supplements" from "authenticated";

revoke delete on table "webapps"."sup_supplements" from "service_role";

revoke insert on table "webapps"."sup_supplements" from "service_role";

revoke references on table "webapps"."sup_supplements" from "service_role";

revoke select on table "webapps"."sup_supplements" from "service_role";

revoke trigger on table "webapps"."sup_supplements" from "service_role";

revoke truncate on table "webapps"."sup_supplements" from "service_role";

revoke update on table "webapps"."sup_supplements" from "service_role";

revoke delete on table "webapps"."sup_tags" from "anon";

revoke insert on table "webapps"."sup_tags" from "anon";

revoke references on table "webapps"."sup_tags" from "anon";

revoke select on table "webapps"."sup_tags" from "anon";

revoke trigger on table "webapps"."sup_tags" from "anon";

revoke truncate on table "webapps"."sup_tags" from "anon";

revoke update on table "webapps"."sup_tags" from "anon";

revoke delete on table "webapps"."sup_tags" from "authenticated";

revoke insert on table "webapps"."sup_tags" from "authenticated";

revoke references on table "webapps"."sup_tags" from "authenticated";

revoke select on table "webapps"."sup_tags" from "authenticated";

revoke trigger on table "webapps"."sup_tags" from "authenticated";

revoke truncate on table "webapps"."sup_tags" from "authenticated";

revoke update on table "webapps"."sup_tags" from "authenticated";

revoke delete on table "webapps"."sup_tags" from "service_role";

revoke insert on table "webapps"."sup_tags" from "service_role";

revoke references on table "webapps"."sup_tags" from "service_role";

revoke select on table "webapps"."sup_tags" from "service_role";

revoke trigger on table "webapps"."sup_tags" from "service_role";

revoke truncate on table "webapps"."sup_tags" from "service_role";

revoke update on table "webapps"."sup_tags" from "service_role";

alter table "webapps"."sup_tags" drop constraint "sup_tags_name_key";

drop view if exists "views"."dr_fred_conversations";

alter table "webapps"."sup_supplements" drop constraint "sup_supplements_pkey";

alter table "webapps"."sup_tags" drop constraint "sup_tags_pkey";

drop index if exists "webapps"."idx_sup_supplements_instructions";

drop index if exists "webapps"."idx_sup_supplements_proof";

drop index if exists "webapps"."idx_sup_supplements_tag_names";

drop index if exists "webapps"."sup_supplements_pkey";

drop index if exists "webapps"."sup_tags_name_key";

drop index if exists "webapps"."sup_tags_pkey";

drop table "webapps"."sup_supplements";

drop table "webapps"."sup_tags";

drop sequence if exists "webapps"."sup_supplements_id_seq";

drop sequence if exists "webapps"."sup_tags_id_seq";

create or replace view "views"."dr_fred_conversations" as  SELECT last_message.id,
    last_message.user_id,
    u.name,
    last_message.text,
    last_message.created_at AS "timestamp",
    last_message.outbound
   FROM (( SELECT DISTINCT ON (dr_fred.user_id) dr_fred.user_id,
            dr_fred.id,
            dr_fred.text,
            dr_fred.created_at,
            dr_fred.outbound
           FROM comms.dr_fred
          ORDER BY dr_fred.user_id, dr_fred.created_at DESC) last_message
     LEFT JOIN public.users u ON ((last_message.user_id = u.id)))
  ORDER BY last_message.outbound, last_message.created_at DESC;


grant delete on table "payment"."subscription_cache" to "anon";

grant insert on table "payment"."subscription_cache" to "anon";

grant references on table "payment"."subscription_cache" to "anon";

grant select on table "payment"."subscription_cache" to "anon";

grant trigger on table "payment"."subscription_cache" to "anon";

grant truncate on table "payment"."subscription_cache" to "anon";

grant update on table "payment"."subscription_cache" to "anon";

grant delete on table "payment"."subscription_cache" to "authenticated";

grant insert on table "payment"."subscription_cache" to "authenticated";

grant references on table "payment"."subscription_cache" to "authenticated";

grant select on table "payment"."subscription_cache" to "authenticated";

grant trigger on table "payment"."subscription_cache" to "authenticated";

grant truncate on table "payment"."subscription_cache" to "authenticated";

grant update on table "payment"."subscription_cache" to "authenticated";

grant delete on table "payment"."subscription_cache" to "service_role";

grant insert on table "payment"."subscription_cache" to "service_role";

grant references on table "payment"."subscription_cache" to "service_role";

grant select on table "payment"."subscription_cache" to "service_role";

grant trigger on table "payment"."subscription_cache" to "service_role";

grant truncate on table "payment"."subscription_cache" to "service_role";

grant update on table "payment"."subscription_cache" to "service_role";

drop schema if exists "webapps";


