revoke delete on table "public"."documents" from "anon";

revoke insert on table "public"."documents" from "anon";

revoke references on table "public"."documents" from "anon";

revoke select on table "public"."documents" from "anon";

revoke trigger on table "public"."documents" from "anon";

revoke truncate on table "public"."documents" from "anon";

revoke update on table "public"."documents" from "anon";

revoke delete on table "public"."documents" from "authenticated";

revoke insert on table "public"."documents" from "authenticated";

revoke references on table "public"."documents" from "authenticated";

revoke select on table "public"."documents" from "authenticated";

revoke trigger on table "public"."documents" from "authenticated";

revoke truncate on table "public"."documents" from "authenticated";

revoke update on table "public"."documents" from "authenticated";

revoke delete on table "public"."documents" from "service_role";

revoke insert on table "public"."documents" from "service_role";

revoke references on table "public"."documents" from "service_role";

revoke select on table "public"."documents" from "service_role";

revoke trigger on table "public"."documents" from "service_role";

revoke truncate on table "public"."documents" from "service_role";

revoke update on table "public"."documents" from "service_role";

drop function if exists "public"."match_documents"(query_embedding vector, match_count integer, filter jsonb);

drop function if exists "public"."update_fcm_token"(user_id text, fcm_token text);

alter table "public"."documents" drop constraint "documents_pkey";

drop index if exists "public"."documents_pkey";

drop table "public"."documents";

drop extension if exists "vector";


