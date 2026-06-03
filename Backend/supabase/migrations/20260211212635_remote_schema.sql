drop trigger if exists "tr_creators_updated_at" on "public"."creators";

drop trigger if exists "tr_outreach_accounts_updated_at" on "public"."outreach_accounts";

drop trigger if exists "tr_profiles_updated_at" on "public"."profiles";

drop policy "Authenticated users can view notes" on "public"."creator_notes";

drop policy "View status changes" on "public"."creator_status_changes";

drop policy "View own submissions" on "public"."creator_submissions";

drop policy "View all creators" on "public"."creators";

drop policy "View active outreach accounts" on "public"."outreach_accounts";

drop policy "Update own profile" on "public"."profiles";

drop policy "View own profile" on "public"."profiles";

revoke delete on table "public"."creator_notes" from "anon";

revoke insert on table "public"."creator_notes" from "anon";

revoke references on table "public"."creator_notes" from "anon";

revoke select on table "public"."creator_notes" from "anon";

revoke trigger on table "public"."creator_notes" from "anon";

revoke truncate on table "public"."creator_notes" from "anon";

revoke update on table "public"."creator_notes" from "anon";

revoke delete on table "public"."creator_notes" from "authenticated";

revoke insert on table "public"."creator_notes" from "authenticated";

revoke references on table "public"."creator_notes" from "authenticated";

revoke select on table "public"."creator_notes" from "authenticated";

revoke trigger on table "public"."creator_notes" from "authenticated";

revoke truncate on table "public"."creator_notes" from "authenticated";

revoke update on table "public"."creator_notes" from "authenticated";

revoke delete on table "public"."creator_notes" from "service_role";

revoke insert on table "public"."creator_notes" from "service_role";

revoke references on table "public"."creator_notes" from "service_role";

revoke select on table "public"."creator_notes" from "service_role";

revoke trigger on table "public"."creator_notes" from "service_role";

revoke truncate on table "public"."creator_notes" from "service_role";

revoke update on table "public"."creator_notes" from "service_role";

revoke delete on table "public"."creator_status_changes" from "anon";

revoke insert on table "public"."creator_status_changes" from "anon";

revoke references on table "public"."creator_status_changes" from "anon";

revoke select on table "public"."creator_status_changes" from "anon";

revoke trigger on table "public"."creator_status_changes" from "anon";

revoke truncate on table "public"."creator_status_changes" from "anon";

revoke update on table "public"."creator_status_changes" from "anon";

revoke delete on table "public"."creator_status_changes" from "authenticated";

revoke insert on table "public"."creator_status_changes" from "authenticated";

revoke references on table "public"."creator_status_changes" from "authenticated";

revoke select on table "public"."creator_status_changes" from "authenticated";

revoke trigger on table "public"."creator_status_changes" from "authenticated";

revoke truncate on table "public"."creator_status_changes" from "authenticated";

revoke update on table "public"."creator_status_changes" from "authenticated";

revoke delete on table "public"."creator_status_changes" from "service_role";

revoke insert on table "public"."creator_status_changes" from "service_role";

revoke references on table "public"."creator_status_changes" from "service_role";

revoke select on table "public"."creator_status_changes" from "service_role";

revoke trigger on table "public"."creator_status_changes" from "service_role";

revoke truncate on table "public"."creator_status_changes" from "service_role";

revoke update on table "public"."creator_status_changes" from "service_role";

revoke delete on table "public"."creator_submissions" from "anon";

revoke insert on table "public"."creator_submissions" from "anon";

revoke references on table "public"."creator_submissions" from "anon";

revoke select on table "public"."creator_submissions" from "anon";

revoke trigger on table "public"."creator_submissions" from "anon";

revoke truncate on table "public"."creator_submissions" from "anon";

revoke update on table "public"."creator_submissions" from "anon";

revoke delete on table "public"."creator_submissions" from "authenticated";

revoke insert on table "public"."creator_submissions" from "authenticated";

revoke references on table "public"."creator_submissions" from "authenticated";

revoke select on table "public"."creator_submissions" from "authenticated";

revoke trigger on table "public"."creator_submissions" from "authenticated";

revoke truncate on table "public"."creator_submissions" from "authenticated";

revoke update on table "public"."creator_submissions" from "authenticated";

revoke delete on table "public"."creator_submissions" from "service_role";

revoke insert on table "public"."creator_submissions" from "service_role";

revoke references on table "public"."creator_submissions" from "service_role";

revoke select on table "public"."creator_submissions" from "service_role";

revoke trigger on table "public"."creator_submissions" from "service_role";

revoke truncate on table "public"."creator_submissions" from "service_role";

revoke update on table "public"."creator_submissions" from "service_role";

revoke delete on table "public"."creators" from "anon";

revoke insert on table "public"."creators" from "anon";

revoke references on table "public"."creators" from "anon";

revoke select on table "public"."creators" from "anon";

revoke trigger on table "public"."creators" from "anon";

revoke truncate on table "public"."creators" from "anon";

revoke update on table "public"."creators" from "anon";

revoke delete on table "public"."creators" from "authenticated";

revoke insert on table "public"."creators" from "authenticated";

revoke references on table "public"."creators" from "authenticated";

revoke select on table "public"."creators" from "authenticated";

revoke trigger on table "public"."creators" from "authenticated";

revoke truncate on table "public"."creators" from "authenticated";

revoke update on table "public"."creators" from "authenticated";

revoke delete on table "public"."creators" from "service_role";

revoke insert on table "public"."creators" from "service_role";

revoke references on table "public"."creators" from "service_role";

revoke select on table "public"."creators" from "service_role";

revoke trigger on table "public"."creators" from "service_role";

revoke truncate on table "public"."creators" from "service_role";

revoke update on table "public"."creators" from "service_role";

revoke delete on table "public"."outreach_accounts" from "anon";

revoke insert on table "public"."outreach_accounts" from "anon";

revoke references on table "public"."outreach_accounts" from "anon";

revoke select on table "public"."outreach_accounts" from "anon";

revoke trigger on table "public"."outreach_accounts" from "anon";

revoke truncate on table "public"."outreach_accounts" from "anon";

revoke update on table "public"."outreach_accounts" from "anon";

revoke delete on table "public"."outreach_accounts" from "authenticated";

revoke insert on table "public"."outreach_accounts" from "authenticated";

revoke references on table "public"."outreach_accounts" from "authenticated";

revoke select on table "public"."outreach_accounts" from "authenticated";

revoke trigger on table "public"."outreach_accounts" from "authenticated";

revoke truncate on table "public"."outreach_accounts" from "authenticated";

revoke update on table "public"."outreach_accounts" from "authenticated";

revoke delete on table "public"."outreach_accounts" from "service_role";

revoke insert on table "public"."outreach_accounts" from "service_role";

revoke references on table "public"."outreach_accounts" from "service_role";

revoke select on table "public"."outreach_accounts" from "service_role";

revoke trigger on table "public"."outreach_accounts" from "service_role";

revoke truncate on table "public"."outreach_accounts" from "service_role";

revoke update on table "public"."outreach_accounts" from "service_role";

revoke delete on table "public"."profiles" from "anon";

revoke insert on table "public"."profiles" from "anon";

revoke references on table "public"."profiles" from "anon";

revoke select on table "public"."profiles" from "anon";

revoke trigger on table "public"."profiles" from "anon";

revoke truncate on table "public"."profiles" from "anon";

revoke update on table "public"."profiles" from "anon";

revoke delete on table "public"."profiles" from "authenticated";

revoke insert on table "public"."profiles" from "authenticated";

revoke references on table "public"."profiles" from "authenticated";

revoke select on table "public"."profiles" from "authenticated";

revoke trigger on table "public"."profiles" from "authenticated";

revoke truncate on table "public"."profiles" from "authenticated";

revoke update on table "public"."profiles" from "authenticated";

revoke delete on table "public"."profiles" from "service_role";

revoke insert on table "public"."profiles" from "service_role";

revoke references on table "public"."profiles" from "service_role";

revoke select on table "public"."profiles" from "service_role";

revoke trigger on table "public"."profiles" from "service_role";

revoke truncate on table "public"."profiles" from "service_role";

revoke update on table "public"."profiles" from "service_role";

alter table "public"."creator_notes" drop constraint "creator_notes_created_by_profile_id_fkey";

alter table "public"."creator_notes" drop constraint "creator_notes_creator_id_fkey";

alter table "public"."creator_status_changes" drop constraint "creator_status_changes_changed_by_profile_id_fkey";

alter table "public"."creator_status_changes" drop constraint "creator_status_changes_creator_id_fkey";

alter table "public"."creator_submissions" drop constraint "creator_submissions_matched_creator_id_fkey";

alter table "public"."creator_submissions" drop constraint "creator_submissions_outreach_account_id_fkey";

alter table "public"."creator_submissions" drop constraint "creator_submissions_result_check";

alter table "public"."creator_submissions" drop constraint "creator_submissions_submitted_by_va_id_fkey";

alter table "public"."creators" drop constraint "creators_added_by_profile_id_fkey";

alter table "public"."creators" drop constraint "creators_platform_check";

alter table "public"."creators" drop constraint "creators_sourced_via_outreach_account_id_fkey";

alter table "public"."creators" drop constraint "creators_status_check";

alter table "public"."creators" drop constraint "unique_creator_key";

alter table "public"."outreach_accounts" drop constraint "outreach_accounts_platform_check";

alter table "public"."outreach_accounts" drop constraint "unique_outreach_account_key";

alter table "public"."profiles" drop constraint "profiles_auth_user_id_fkey";

alter table "public"."profiles" drop constraint "profiles_auth_user_id_key";

alter table "public"."profiles" drop constraint "profiles_instagram_outreach_account_id_fkey";

alter table "public"."profiles" drop constraint "profiles_tiktok_outreach_account_id_fkey";

drop function if exists "public"."update_updated_at"();

alter table "public"."creator_notes" drop constraint "creator_notes_pkey";

alter table "public"."creator_status_changes" drop constraint "creator_status_changes_pkey";

alter table "public"."creator_submissions" drop constraint "creator_submissions_pkey";

alter table "public"."creators" drop constraint "creators_pkey";

alter table "public"."outreach_accounts" drop constraint "outreach_accounts_pkey";

alter table "public"."profiles" drop constraint "profiles_pkey";

drop index if exists "public"."creator_notes_pkey";

drop index if exists "public"."creator_status_changes_pkey";

drop index if exists "public"."creator_submissions_pkey";

drop index if exists "public"."creators_pkey";

drop index if exists "public"."idx_creator_notes_creator_id";

drop index if exists "public"."idx_creators_creator_key";

drop index if exists "public"."idx_creators_deleted_at";

drop index if exists "public"."idx_creators_platform_handle";

drop index if exists "public"."idx_outreach_accounts_deleted_at";

drop index if exists "public"."idx_profiles_auth_user_id";

drop index if exists "public"."idx_status_changes_creator_id";

drop index if exists "public"."idx_submissions_result";

drop index if exists "public"."idx_submissions_va_id";

drop index if exists "public"."outreach_accounts_pkey";

drop index if exists "public"."profiles_auth_user_id_key";

drop index if exists "public"."profiles_pkey";

drop index if exists "public"."unique_creator_key";

drop index if exists "public"."unique_outreach_account_key";

drop table "public"."creator_notes";

drop table "public"."creator_status_changes";

drop table "public"."creator_submissions";

drop table "public"."creators";

drop table "public"."outreach_accounts";

drop table "public"."profiles";

drop type "public"."user_role";


