drop policy "Disable all access" on "comms"."sms_notify_team_summaries";

revoke delete on table "comms"."sms_notify_team_summaries" from "anon";

revoke insert on table "comms"."sms_notify_team_summaries" from "anon";

revoke references on table "comms"."sms_notify_team_summaries" from "anon";

revoke select on table "comms"."sms_notify_team_summaries" from "anon";

revoke trigger on table "comms"."sms_notify_team_summaries" from "anon";

revoke truncate on table "comms"."sms_notify_team_summaries" from "anon";

revoke update on table "comms"."sms_notify_team_summaries" from "anon";

revoke delete on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke insert on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke references on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke select on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke trigger on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke truncate on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke update on table "comms"."sms_notify_team_summaries" from "authenticated";

revoke delete on table "comms"."sms_notify_team_summaries" from "service_role";

revoke insert on table "comms"."sms_notify_team_summaries" from "service_role";

revoke references on table "comms"."sms_notify_team_summaries" from "service_role";

revoke select on table "comms"."sms_notify_team_summaries" from "service_role";

revoke trigger on table "comms"."sms_notify_team_summaries" from "service_role";

revoke truncate on table "comms"."sms_notify_team_summaries" from "service_role";

revoke update on table "comms"."sms_notify_team_summaries" from "service_role";

alter table "comms"."sms_notify_team_summaries" drop constraint "sms_notify_team_summaries_pkey";

drop index if exists "comms"."idx_dr_fred_user_id";

drop index if exists "comms"."idx_sms_messages_conversation_lookup";

drop index if exists "comms"."idx_sms_messages_created_at";

drop index if exists "comms"."idx_sms_messages_nonuser_conversations";

drop index if exists "comms"."idx_sms_messages_scheduled_for";

drop index if exists "comms"."idx_sms_messages_user_conversations";

drop index if exists "comms"."idx_sms_notify_team_summaries_sent_at";

drop index if exists "comms"."idx_sms_notify_team_summaries_updated_at";

drop index if exists "comms"."sms_notify_team_summaries_pkey";

drop table "comms"."sms_notify_team_summaries";

drop sequence if exists "comms"."sms_notify_team_summaries_id_seq";

CREATE INDEX idx_sms_pending_send ON comms.sms_messages USING btree (scheduled_for) WHERE ((outbound = true) AND (sent_at IS NULL) AND (canceled = false));


drop policy "All can access" on "library"."general_copy";

revoke delete on table "library"."general_copy" from "anon";

revoke insert on table "library"."general_copy" from "anon";

revoke references on table "library"."general_copy" from "anon";

revoke select on table "library"."general_copy" from "anon";

revoke trigger on table "library"."general_copy" from "anon";

revoke truncate on table "library"."general_copy" from "anon";

revoke update on table "library"."general_copy" from "anon";

revoke delete on table "library"."general_copy" from "authenticated";

revoke insert on table "library"."general_copy" from "authenticated";

revoke references on table "library"."general_copy" from "authenticated";

revoke select on table "library"."general_copy" from "authenticated";

revoke trigger on table "library"."general_copy" from "authenticated";

revoke truncate on table "library"."general_copy" from "authenticated";

revoke update on table "library"."general_copy" from "authenticated";

revoke delete on table "library"."general_copy" from "service_role";

revoke insert on table "library"."general_copy" from "service_role";

revoke references on table "library"."general_copy" from "service_role";

revoke select on table "library"."general_copy" from "service_role";

revoke trigger on table "library"."general_copy" from "service_role";

revoke truncate on table "library"."general_copy" from "service_role";

revoke update on table "library"."general_copy" from "service_role";

alter table "library"."general_copy" drop constraint "general_copy_pkey";

drop index if exists "library"."general_copy_pkey";

drop table "library"."general_copy";

drop policy "Authed can access" on "payment"."trial_notifications";

revoke delete on table "payment"."trial_notifications" from "anon";

revoke insert on table "payment"."trial_notifications" from "anon";

revoke references on table "payment"."trial_notifications" from "anon";

revoke select on table "payment"."trial_notifications" from "anon";

revoke trigger on table "payment"."trial_notifications" from "anon";

revoke truncate on table "payment"."trial_notifications" from "anon";

revoke update on table "payment"."trial_notifications" from "anon";

revoke delete on table "payment"."trial_notifications" from "authenticated";

revoke insert on table "payment"."trial_notifications" from "authenticated";

revoke references on table "payment"."trial_notifications" from "authenticated";

revoke select on table "payment"."trial_notifications" from "authenticated";

revoke trigger on table "payment"."trial_notifications" from "authenticated";

revoke truncate on table "payment"."trial_notifications" from "authenticated";

revoke update on table "payment"."trial_notifications" from "authenticated";

revoke delete on table "payment"."trial_notifications" from "service_role";

revoke insert on table "payment"."trial_notifications" from "service_role";

revoke references on table "payment"."trial_notifications" from "service_role";

revoke select on table "payment"."trial_notifications" from "service_role";

revoke trigger on table "payment"."trial_notifications" from "service_role";

revoke truncate on table "payment"."trial_notifications" from "service_role";

revoke update on table "payment"."trial_notifications" from "service_role";

drop function if exists "payment"."check_referral_code"(input_code text);

alter table "payment"."trial_notifications" drop constraint "trial_notifications_pkey";

drop index if exists "payment"."trial_notifications_pkey";

drop table "payment"."trial_notifications";


drop function if exists "programs"."get_matching_assessment_count"(criteria jsonb);

drop function if exists "programs"."migrate_clear30_messages"();


drop function if exists "public"."add_group_mem"(user_id text, group_id uuid);

drop function if exists "public"."add_group_note"(group_id uuid, to_member_id text, from_member_id text, message text);

drop function if exists "public"."add_group_ping"(group_id uuid, from_user_id text, to_user_id text);

drop function if exists "public"."delete_group"(group_id uuid);

drop function if exists "public"."dr_fred_get_messages"();

drop function if exists "public"."get_group"(group_id uuid, user_id text);

drop function if exists "public"."get_obfuscated_events"(excluded_users text[]);

drop function if exists "public"."get_obfuscated_events_count"(excluded_users text[]);

drop function if exists "public"."group_add_mem"(group_id uuid);

drop function if exists "public"."group_add_note"(group_id uuid, to_member_id text, message text);

drop function if exists "public"."group_add_ping"(group_id uuid, to_user_id text);

drop function if exists "public"."group_delete"(group_id uuid);

drop function if exists "public"."group_get"(group_id uuid);

drop function if exists "public"."group_rem_mem"(user_id text, group_id uuid);

drop function if exists "public"."group_update_activity"(group_id uuid, activity_data jsonb);

drop function if exists "public"."group_update_subscriptions"(group_id uuid, subscriptions jsonb);

drop function if exists "public"."group_upsert"(group_data jsonb);

drop function if exists "public"."payment_check_email"();

drop function if exists "public"."program_submit_assessment_response"(assessment_id text, responses jsonb);

drop function if exists "public"."rem_group_mem"(user_id text, group_id uuid);

drop function if exists "public"."update_group_activity"(group_id uuid, user_id text, activity_data jsonb);

drop function if exists "public"."update_group_subscriptions"(group_id uuid, user_id text, subscriptions jsonb);

drop function if exists "public"."upsert_group"(group_data jsonb, user_id text);

drop function if exists "public"."upsert_user"(user_data jsonb);


drop view if exists "views"."assessment_responses_clear30";

drop view if exists "views"."assessment_responses_life";

drop view if exists "views"."sms_non_user_conversations";

drop view if exists "views"."sms_user_conversations";

drop view if exists "views"."thatchers_fuck_up";


