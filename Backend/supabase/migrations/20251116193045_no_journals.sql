drop trigger if exists "update_journal_entries_updated_at" on "public"."journal_entries";

drop policy "Users can delete their own journal entries" on "public"."journal_entries";

drop policy "Users can insert their own journal entries" on "public"."journal_entries";

drop policy "Users can update their own journal entries" on "public"."journal_entries";

drop policy "Users can view their own journal entries" on "public"."journal_entries";

revoke delete on table "public"."journal_entries" from "anon";

revoke insert on table "public"."journal_entries" from "anon";

revoke references on table "public"."journal_entries" from "anon";

revoke select on table "public"."journal_entries" from "anon";

revoke trigger on table "public"."journal_entries" from "anon";

revoke truncate on table "public"."journal_entries" from "anon";

revoke update on table "public"."journal_entries" from "anon";

revoke delete on table "public"."journal_entries" from "authenticated";

revoke insert on table "public"."journal_entries" from "authenticated";

revoke references on table "public"."journal_entries" from "authenticated";

revoke select on table "public"."journal_entries" from "authenticated";

revoke trigger on table "public"."journal_entries" from "authenticated";

revoke truncate on table "public"."journal_entries" from "authenticated";

revoke update on table "public"."journal_entries" from "authenticated";

revoke delete on table "public"."journal_entries" from "service_role";

revoke insert on table "public"."journal_entries" from "service_role";

revoke references on table "public"."journal_entries" from "service_role";

revoke select on table "public"."journal_entries" from "service_role";

revoke trigger on table "public"."journal_entries" from "service_role";

revoke truncate on table "public"."journal_entries" from "service_role";

revoke update on table "public"."journal_entries" from "service_role";

alter table "public"."journal_entries" drop constraint "journal_entries_user_id_fkey";

alter table "public"."journal_entries" drop constraint "journal_entries_pkey";

drop index if exists "public"."idx_journal_entries_created_at";

drop index if exists "public"."idx_journal_entries_user_id";

drop index if exists "public"."journal_entries_pkey";

drop table "public"."journal_entries";

drop sequence if exists "public"."journal_entries_id_seq";


