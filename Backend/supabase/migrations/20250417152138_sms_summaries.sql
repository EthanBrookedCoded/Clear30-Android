create sequence "comms"."sms_notify_team_summaries_id_seq";

create table "comms"."sms_notify_team_summaries" (
    "id" integer not null default nextval('comms.sms_notify_team_summaries_id_seq'::regclass),
    "summary" text not null,
    "updated_at" timestamp with time zone not null default now(),
    "sent_at" timestamp with time zone,
    "created_at" timestamp with time zone not null default now()
);


alter table "comms"."sms_notify_team_summaries" enable row level security;

alter sequence "comms"."sms_notify_team_summaries_id_seq" owned by "comms"."sms_notify_team_summaries"."id";

CREATE INDEX idx_sms_notify_team_summaries_sent_at ON comms.sms_notify_team_summaries USING btree (sent_at);

CREATE INDEX idx_sms_notify_team_summaries_updated_at ON comms.sms_notify_team_summaries USING btree (updated_at);

CREATE UNIQUE INDEX sms_notify_team_summaries_pkey ON comms.sms_notify_team_summaries USING btree (id);

alter table "comms"."sms_notify_team_summaries" add constraint "sms_notify_team_summaries_pkey" PRIMARY KEY using index "sms_notify_team_summaries_pkey";

grant delete on table "comms"."sms_notify_team_summaries" to "service_role";

grant insert on table "comms"."sms_notify_team_summaries" to "service_role";

grant references on table "comms"."sms_notify_team_summaries" to "service_role";

grant select on table "comms"."sms_notify_team_summaries" to "service_role";

grant trigger on table "comms"."sms_notify_team_summaries" to "service_role";

grant truncate on table "comms"."sms_notify_team_summaries" to "service_role";

grant update on table "comms"."sms_notify_team_summaries" to "service_role";

create policy "Disable all access"
on "comms"."sms_notify_team_summaries"
as permissive
for all
to public
using (false);



