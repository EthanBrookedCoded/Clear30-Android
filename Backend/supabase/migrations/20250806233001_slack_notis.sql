create table "comms"."slack_channels" (
    "channel_type" text not null,
    "webhook_url" text not null,
    "channel_name" text not null,
    "description" text,
    "is_active" boolean default true,
    "created_at" timestamp with time zone default now()
);


alter table "comms"."slack_channels" enable row level security;

create table "comms"."slack_notifications" (
    "id" uuid not null default gen_random_uuid(),
    "channel_type" text not null,
    "title" text not null,
    "message" text not null,
    "metadata" jsonb default '{}'::jsonb,
    "status" text not null default 'pending'::text,
    "created_at" timestamp with time zone not null default now(),
    "sent_at" timestamp with time zone,
    "error_message" text,
    "retry_count" integer default 0,
    "priority" text default 'normal'::text
);


alter table "comms"."slack_notifications" enable row level security;

CREATE INDEX idx_slack_notifications_channel_type ON comms.slack_notifications USING btree (channel_type);

CREATE INDEX idx_slack_notifications_created_at ON comms.slack_notifications USING btree (created_at);

CREATE INDEX idx_slack_notifications_status ON comms.slack_notifications USING btree (status);

CREATE UNIQUE INDEX slack_channels_pkey ON comms.slack_channels USING btree (channel_type);

CREATE UNIQUE INDEX slack_notifications_pkey ON comms.slack_notifications USING btree (id);

alter table "comms"."slack_channels" add constraint "slack_channels_pkey" PRIMARY KEY using index "slack_channels_pkey";

alter table "comms"."slack_notifications" add constraint "slack_notifications_pkey" PRIMARY KEY using index "slack_notifications_pkey";

grant delete on table "comms"."slack_channels" to "anon";

grant insert on table "comms"."slack_channels" to "anon";

grant references on table "comms"."slack_channels" to "anon";

grant select on table "comms"."slack_channels" to "anon";

grant trigger on table "comms"."slack_channels" to "anon";

grant truncate on table "comms"."slack_channels" to "anon";

grant update on table "comms"."slack_channels" to "anon";

grant delete on table "comms"."slack_channels" to "authenticated";

grant insert on table "comms"."slack_channels" to "authenticated";

grant references on table "comms"."slack_channels" to "authenticated";

grant select on table "comms"."slack_channels" to "authenticated";

grant trigger on table "comms"."slack_channels" to "authenticated";

grant truncate on table "comms"."slack_channels" to "authenticated";

grant update on table "comms"."slack_channels" to "authenticated";

grant delete on table "comms"."slack_channels" to "service_role";

grant insert on table "comms"."slack_channels" to "service_role";

grant references on table "comms"."slack_channels" to "service_role";

grant select on table "comms"."slack_channels" to "service_role";

grant trigger on table "comms"."slack_channels" to "service_role";

grant truncate on table "comms"."slack_channels" to "service_role";

grant update on table "comms"."slack_channels" to "service_role";

grant delete on table "comms"."slack_notifications" to "anon";

grant insert on table "comms"."slack_notifications" to "anon";

grant references on table "comms"."slack_notifications" to "anon";

grant select on table "comms"."slack_notifications" to "anon";

grant trigger on table "comms"."slack_notifications" to "anon";

grant truncate on table "comms"."slack_notifications" to "anon";

grant update on table "comms"."slack_notifications" to "anon";

grant delete on table "comms"."slack_notifications" to "authenticated";

grant insert on table "comms"."slack_notifications" to "authenticated";

grant references on table "comms"."slack_notifications" to "authenticated";

grant select on table "comms"."slack_notifications" to "authenticated";

grant trigger on table "comms"."slack_notifications" to "authenticated";

grant truncate on table "comms"."slack_notifications" to "authenticated";

grant update on table "comms"."slack_notifications" to "authenticated";

grant delete on table "comms"."slack_notifications" to "service_role";

grant insert on table "comms"."slack_notifications" to "service_role";

grant references on table "comms"."slack_notifications" to "service_role";

grant select on table "comms"."slack_notifications" to "service_role";

grant trigger on table "comms"."slack_notifications" to "service_role";

grant truncate on table "comms"."slack_notifications" to "service_role";

grant update on table "comms"."slack_notifications" to "service_role";

create policy "Disable all access"
on "comms"."slack_channels"
as permissive
for all
to public
using (false);


create policy "Disable all access"
on "comms"."slack_notifications"
as permissive
for all
to public
using (false);



