create schema if not exists "claire";

grant usage on schema claire to "anon";
grant usage on schema claire to "authenticated";
grant usage on schema claire to "service_role";

create table "claire"."messages" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" text not null,
    "thread_id" text not null,
    "role" text not null,
    "content" text not null,
    "message_type" text default 'chat'::text,
    "api_type" text default 'assistants'::text,
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "claire"."messages" enable row level security;

create table "claire"."prompts" (
    "id" uuid not null default gen_random_uuid(),
    "name" text not null,
    "prompt" text not null,
    "created_at" timestamp with time zone default now()
);


alter table "claire"."prompts" enable row level security;

create table "claire"."settings" (
    "id" uuid not null default gen_random_uuid(),
    "key" text not null,
    "value" jsonb not null,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "claire"."settings" enable row level security;

CREATE INDEX idx_messages_created_at ON claire.messages USING btree (created_at DESC);

CREATE INDEX idx_messages_thread_id ON claire.messages USING btree (thread_id);

CREATE INDEX idx_messages_user_id ON claire.messages USING btree (user_id);

CREATE INDEX idx_messages_user_thread ON claire.messages USING btree (user_id, thread_id);

CREATE INDEX idx_prompts_created_at ON claire.prompts USING btree (created_at DESC);

CREATE INDEX idx_prompts_name ON claire.prompts USING btree (name);

CREATE INDEX idx_settings_updated_at ON claire.settings USING btree (updated_at DESC);

CREATE UNIQUE INDEX messages_pkey ON claire.messages USING btree (id);

CREATE UNIQUE INDEX prompts_pkey ON claire.prompts USING btree (id);

CREATE UNIQUE INDEX settings_key_unique ON claire.settings USING btree (key);

CREATE UNIQUE INDEX settings_pkey ON claire.settings USING btree (id);

alter table "claire"."messages" add constraint "messages_pkey" PRIMARY KEY using index "messages_pkey";

alter table "claire"."prompts" add constraint "prompts_pkey" PRIMARY KEY using index "prompts_pkey";

alter table "claire"."settings" add constraint "settings_pkey" PRIMARY KEY using index "settings_pkey";

alter table "claire"."messages" add constraint "messages_api_type_check" CHECK ((api_type = ANY (ARRAY['assistants'::text, 'completion'::text]))) not valid;

alter table "claire"."messages" validate constraint "messages_api_type_check";

alter table "claire"."messages" add constraint "messages_message_type_check" CHECK ((message_type = ANY (ARRAY['chat'::text, 'system'::text, 'context'::text]))) not valid;

alter table "claire"."messages" validate constraint "messages_message_type_check";

alter table "claire"."messages" add constraint "messages_role_check" CHECK ((role = ANY (ARRAY['user'::text, 'assistant'::text, 'system'::text]))) not valid;

alter table "claire"."messages" validate constraint "messages_role_check";

alter table "claire"."messages" add constraint "messages_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "claire"."messages" validate constraint "messages_user_id_fkey";

alter table "claire"."settings" add constraint "settings_key_unique" UNIQUE using index "settings_key_unique";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION claire.get_latest_prompt()
 RETURNS TABLE(prompt text)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
begin
    return query
    select p.prompt
    from claire.prompts p
    order by p.created_at desc
    limit 1;
end;
$function$
;

grant delete on table "claire"."messages" to "anon";

grant insert on table "claire"."messages" to "anon";

grant references on table "claire"."messages" to "anon";

grant select on table "claire"."messages" to "anon";

grant trigger on table "claire"."messages" to "anon";

grant truncate on table "claire"."messages" to "anon";

grant update on table "claire"."messages" to "anon";

grant delete on table "claire"."messages" to "authenticated";

grant insert on table "claire"."messages" to "authenticated";

grant references on table "claire"."messages" to "authenticated";

grant select on table "claire"."messages" to "authenticated";

grant trigger on table "claire"."messages" to "authenticated";

grant truncate on table "claire"."messages" to "authenticated";

grant update on table "claire"."messages" to "authenticated";

grant delete on table "claire"."messages" to "service_role";

grant insert on table "claire"."messages" to "service_role";

grant references on table "claire"."messages" to "service_role";

grant select on table "claire"."messages" to "service_role";

grant trigger on table "claire"."messages" to "service_role";

grant truncate on table "claire"."messages" to "service_role";

grant update on table "claire"."messages" to "service_role";

grant delete on table "claire"."prompts" to "anon";

grant insert on table "claire"."prompts" to "anon";

grant references on table "claire"."prompts" to "anon";

grant select on table "claire"."prompts" to "anon";

grant trigger on table "claire"."prompts" to "anon";

grant truncate on table "claire"."prompts" to "anon";

grant update on table "claire"."prompts" to "anon";

grant delete on table "claire"."prompts" to "authenticated";

grant insert on table "claire"."prompts" to "authenticated";

grant references on table "claire"."prompts" to "authenticated";

grant select on table "claire"."prompts" to "authenticated";

grant trigger on table "claire"."prompts" to "authenticated";

grant truncate on table "claire"."prompts" to "authenticated";

grant update on table "claire"."prompts" to "authenticated";

grant delete on table "claire"."prompts" to "service_role";

grant insert on table "claire"."prompts" to "service_role";

grant references on table "claire"."prompts" to "service_role";

grant select on table "claire"."prompts" to "service_role";

grant trigger on table "claire"."prompts" to "service_role";

grant truncate on table "claire"."prompts" to "service_role";

grant update on table "claire"."prompts" to "service_role";

grant delete on table "claire"."settings" to "anon";

grant insert on table "claire"."settings" to "anon";

grant references on table "claire"."settings" to "anon";

grant select on table "claire"."settings" to "anon";

grant trigger on table "claire"."settings" to "anon";

grant truncate on table "claire"."settings" to "anon";

grant update on table "claire"."settings" to "anon";

grant delete on table "claire"."settings" to "authenticated";

grant insert on table "claire"."settings" to "authenticated";

grant references on table "claire"."settings" to "authenticated";

grant select on table "claire"."settings" to "authenticated";

grant trigger on table "claire"."settings" to "authenticated";

grant truncate on table "claire"."settings" to "authenticated";

grant update on table "claire"."settings" to "authenticated";

grant delete on table "claire"."settings" to "service_role";

grant insert on table "claire"."settings" to "service_role";

grant references on table "claire"."settings" to "service_role";

grant select on table "claire"."settings" to "service_role";

grant trigger on table "claire"."settings" to "service_role";

grant truncate on table "claire"."settings" to "service_role";

grant update on table "claire"."settings" to "service_role";

create policy "Disable public access"
on "claire"."messages"
as permissive
for all
to public
using (false);


create policy "Disable public access"
on "claire"."prompts"
as permissive
for all
to public
using (false);


create policy "Disable public access"
on "claire"."settings"
as permissive
for all
to public
using (false);



