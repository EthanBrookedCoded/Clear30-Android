drop policy "Disable access for all users" on "comms"."notifications";

alter table "comms"."feedback" drop constraint "feedback_user_id_fkey";

alter table "comms"."feedback" alter column "user_id" set not null;

create policy "Disable access for all"
on "comms"."notifications"
as permissive
for all
to public
using (false);



drop policy "Enable read access for all users" on "library"."cache";

drop policy "Enable read access for all users" on "library"."claire_prompts";

drop policy "Enable read access for all users" on "library"."journal_prompts";

drop policy "Enable read access for all users" on "library"."meditations";

drop policy "Enable read access for all users" on "library"."resources";

create table "library"."one_offs" (
    "key" text not null,
    "value" text not null
);


alter table "library"."one_offs" enable row level security;

create table "library"."sms_check_in" (
    "day" bigint not null,
    "message" text not null
);


alter table "library"."sms_check_in" enable row level security;

create table "library"."sms_get_back" (
    "day" bigint not null,
    "message" text not null
);


alter table "library"."sms_get_back" enable row level security;

create table "library"."sms_slip_up" (
    "day" bigint not null,
    "message" text not null
);


alter table "library"."sms_slip_up" enable row level security;

CREATE UNIQUE INDEX check_in_messages_pkey ON library.sms_check_in USING btree (day, message);

CREATE UNIQUE INDEX get_back_messages_pkey ON library.sms_get_back USING btree (day, message);

CREATE UNIQUE INDEX one_offs_pkey ON library.one_offs USING btree (key);

CREATE UNIQUE INDEX sms_slip_up_pkey ON library.sms_slip_up USING btree (day, message);

alter table "library"."one_offs" add constraint "one_offs_pkey" PRIMARY KEY using index "one_offs_pkey";

alter table "library"."sms_check_in" add constraint "check_in_messages_pkey" PRIMARY KEY using index "check_in_messages_pkey";

alter table "library"."sms_get_back" add constraint "get_back_messages_pkey" PRIMARY KEY using index "get_back_messages_pkey";

alter table "library"."sms_slip_up" add constraint "sms_slip_up_pkey" PRIMARY KEY using index "sms_slip_up_pkey";

grant delete on table "library"."one_offs" to "anon";

grant insert on table "library"."one_offs" to "anon";

grant references on table "library"."one_offs" to "anon";

grant select on table "library"."one_offs" to "anon";

grant trigger on table "library"."one_offs" to "anon";

grant truncate on table "library"."one_offs" to "anon";

grant update on table "library"."one_offs" to "anon";

grant delete on table "library"."one_offs" to "authenticated";

grant insert on table "library"."one_offs" to "authenticated";

grant references on table "library"."one_offs" to "authenticated";

grant select on table "library"."one_offs" to "authenticated";

grant trigger on table "library"."one_offs" to "authenticated";

grant truncate on table "library"."one_offs" to "authenticated";

grant update on table "library"."one_offs" to "authenticated";

grant delete on table "library"."sms_check_in" to "anon";

grant insert on table "library"."sms_check_in" to "anon";

grant references on table "library"."sms_check_in" to "anon";

grant select on table "library"."sms_check_in" to "anon";

grant trigger on table "library"."sms_check_in" to "anon";

grant truncate on table "library"."sms_check_in" to "anon";

grant update on table "library"."sms_check_in" to "anon";

grant delete on table "library"."sms_check_in" to "authenticated";

grant insert on table "library"."sms_check_in" to "authenticated";

grant references on table "library"."sms_check_in" to "authenticated";

grant select on table "library"."sms_check_in" to "authenticated";

grant trigger on table "library"."sms_check_in" to "authenticated";

grant truncate on table "library"."sms_check_in" to "authenticated";

grant update on table "library"."sms_check_in" to "authenticated";

grant delete on table "library"."sms_get_back" to "anon";

grant insert on table "library"."sms_get_back" to "anon";

grant references on table "library"."sms_get_back" to "anon";

grant select on table "library"."sms_get_back" to "anon";

grant trigger on table "library"."sms_get_back" to "anon";

grant truncate on table "library"."sms_get_back" to "anon";

grant update on table "library"."sms_get_back" to "anon";

grant delete on table "library"."sms_get_back" to "authenticated";

grant insert on table "library"."sms_get_back" to "authenticated";

grant references on table "library"."sms_get_back" to "authenticated";

grant select on table "library"."sms_get_back" to "authenticated";

grant trigger on table "library"."sms_get_back" to "authenticated";

grant truncate on table "library"."sms_get_back" to "authenticated";

grant update on table "library"."sms_get_back" to "authenticated";

grant delete on table "library"."sms_slip_up" to "anon";

grant insert on table "library"."sms_slip_up" to "anon";

grant references on table "library"."sms_slip_up" to "anon";

grant select on table "library"."sms_slip_up" to "anon";

grant trigger on table "library"."sms_slip_up" to "anon";

grant truncate on table "library"."sms_slip_up" to "anon";

grant update on table "library"."sms_slip_up" to "anon";

grant delete on table "library"."sms_slip_up" to "authenticated";

grant insert on table "library"."sms_slip_up" to "authenticated";

grant references on table "library"."sms_slip_up" to "authenticated";

grant select on table "library"."sms_slip_up" to "authenticated";

grant trigger on table "library"."sms_slip_up" to "authenticated";

grant truncate on table "library"."sms_slip_up" to "authenticated";

grant update on table "library"."sms_slip_up" to "authenticated";

create policy "Disable access for all"
on "library"."cache"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."claire_prompts"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."journal_prompts"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."meditations"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."one_offs"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."resources"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."sms_check_in"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."sms_get_back"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "library"."sms_slip_up"
as permissive
for all
to public
using (false);



create schema if not exists "programs";

create table "programs"."programs" (
    "id" text not null,
    "descriptions" text not null,
    "created_at" timestamp with time zone not null default now()
);


CREATE UNIQUE INDEX programs_pkey ON programs.programs USING btree (id);

alter table "programs"."programs" add constraint "programs_pkey" PRIMARY KEY using index "programs_pkey";


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.add_ping_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    -- Declare variables to hold the data we'll fetch
    to_user_fcm_token TEXT;
    from_user_name TEXT;
    from_user_emoji TEXT;
BEGIN
    -- Check if the 'to_member_id' has an fcm_token
    SELECT fcm_token INTO to_user_fcm_token
    FROM users
    WHERE id = NEW.to_user_id;

    -- If there is no fcm_token, just return and do nothing
    IF to_user_fcm_token IS NULL THEN
        RETURN NULL;
    END IF;

    -- Fetch the 'name' and 'emoji' of the 'from_member_id'
    SELECT name, emoji INTO from_user_name, from_user_emoji
    FROM users
    WHERE id = NEW.from_user_id;

    -- Insert a notification into the notifications table
    INSERT INTO comms.notifications (user_id, title, body, timestamp)
    VALUES (
        NEW.to_user_id, 
        from_user_emoji || ' ' || from_user_name || ' check in!',  -- Format the title
        from_user_name || ' wants you to check in on Clear30!',  -- The body is the note's message content
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_check_in_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'check_in_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if the cache already has a value for "check_in_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSONB result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_check_in  -- Updated table name
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_claire_prompt()
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    claire_prompt_value TEXT;
BEGIN
    -- Query to get the value of the row where the key is 'claire_prompt'
    SELECT value INTO claire_prompt_value
    FROM library.one_offs
    WHERE key = 'claire_prompt';

    -- Return the retrieved value
    RETURN claire_prompt_value;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_generic_demo()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$ 
DECLARE 
    result JSONB; 
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    SELECT 
    (
        get_school_data(umich_school_id) || 
        '{"school_id": "generic_demo", "short_name": "Group", "long_name": "Your Group", "reddit_flair": null, "sf_symbol_icon": "person.3.fill"}'::jsonb
    ) INTO result; 
    
    RETURN result;
END; 
$function$
;

CREATE OR REPLACE FUNCTION public.get_get_back_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'get_back_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "get_back_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_get_back  -- Updated schema reference
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_payment_settings()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    payment_settings TEXT;
BEGIN
    -- Retrieve the string value for the key "payment_settings" from the library.one_offs table
    SELECT value
    INTO payment_settings
    FROM library.one_offs
    WHERE key = 'payment_settings';

    -- Return the value as JSONB
    RETURN payment_settings::JSONB;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_school_data(school_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    result JSONB;
    cached_result JSONB;
BEGIN
    -- Step 1: Check if there's a cached result for this school_id
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = school_id;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    WITH aggregated_resources AS (
        SELECT r.school_id,
               r.section_title,
               jsonb_agg(
                   jsonb_build_object(
                       'title', r.title,
                       'subtitle', r.subtitle,
                       'description', r.description,
                       'phone_number', r.phone_number,
                       'location', r.location,
                       'link', r.link,
                       'badge', r.badge
                   )
               ) AS resources_array
        FROM schools.school_resources r
        WHERE r.school_id = $1
        GROUP BY r.school_id, r.section_title
    ),
    final_resources AS (
        SELECT ar.school_id,
               jsonb_object_agg(ar.section_title, ar.resources_array) AS resources
        FROM aggregated_resources ar
        GROUP BY ar.school_id
    ),
    aggregated_messages AS (
        SELECT jsonb_agg(
                   jsonb_build_object(
                       'day', m.day,
                       'title', m.title,
                       'subtitle', m.subtitle,
                       'message', m.message,
                        'meditation_name', (
                           SELECT med.title
                           FROM schools.school_message_meditations smm
                           JOIN library.meditations med ON med.id = smm.meditation
                           WHERE smm.school_message = m.id
                           LIMIT 1
                       ),
                       'meditation_link', (
                           SELECT med.url
                           FROM schools.school_message_meditations smm
                           JOIN library.meditations med ON med.id = smm.meditation
                           WHERE smm.school_message = m.id
                           LIMIT 1
                       ),
                       'prompts', (
                            SELECT jsonb_object_agg(cp.title, scp.prompt)
                            FROM schools.school_message_claire_prompts scp
                            JOIN library.claire_prompts cp ON cp.id = scp.prompt
                            WHERE scp.school_message = m.id
                       ),
                       'journal_prompts', (
                           SELECT jsonb_agg(jp.prompt)
                           FROM schools.school_message_journal_prompts sjp
                           JOIN library.journal_prompts jp ON jp.id = sjp.prompt
                           WHERE sjp.school_message = m.id
                       ),
                       'resources', (
                           SELECT jsonb_object_agg(r.title, r.url)
                           FROM schools.school_message_resources smr
                           JOIN library.resources r ON r.id = smr.resource
                           WHERE smr.school_message = m.id
                       )
                   )
               ) AS messages_array
        FROM schools.school_messages m
        WHERE m.school_id = $1
    )
    
    -- Step 4: Build the JSON result
    SELECT jsonb_build_object(
        'school_id', s.school_id,
        'short_name', s.short_name,
        'long_name', s.long_name,
        'reddit_flair', COALESCE(s.reddit_flair, NULL),
        'color1', s.color1,
        'color2', s.color2,
        'activities', COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'title', a.title,
                    'org_name', a.org_name,
                    'location', a.location,
                    'subtitle', a.subtitle,
                    'description', a.description,
                    'date_time', a.date_time,
                    'repeats', a.repeats,
                    'link', a.link,
                    'facilitated_by', a.facilitated_by,
                    'phone_number', a.phone_number,
                    'email', a.email,
                    'sub_links', a.sub_links
                )
            ) FILTER (WHERE a.school_id IS NOT NULL), '[]'::jsonb
        ),
        'resources', COALESCE(fr.resources, '{}'::jsonb),
        'messages', COALESCE(am.messages_array, '[]'::jsonb)
    )
    INTO result
    FROM schools.schools s
    LEFT JOIN schools.school_activities a ON s.school_id = a.school_id
    LEFT JOIN final_resources fr ON s.school_id = fr.school_id
    LEFT JOIN aggregated_messages am ON TRUE  -- Ensure that all messages for this school are selected
    WHERE s.school_id = $1
    GROUP BY s.school_id, fr.resources, am.messages_array;

    -- Step 5: Insert the computed result into the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES ($1, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();
    
    -- Step 6: Return the computed result
    RETURN result;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_school_demo()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$ 
DECLARE 
    result JSONB; 
    cached_result JSONB;
    umich_school_id CONSTANT TEXT := 'umich';
BEGIN
    SELECT 
    (
        get_school_data(umich_school_id) || 
        '{"school_id": "school_demo", "short_name": "Your School", "long_name": "Your School", "reddit_flair": null}'::jsonb
    ) INTO result; 
    
    RETURN result;
END; 
$function$
;

CREATE OR REPLACE FUNCTION public.get_slip_up_messages()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'slip_up_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "slip_up_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    SELECT jsonb_object_agg(day, messages_array) INTO result
    FROM (
        SELECT day, jsonb_agg(message) AS messages_array
        FROM library.sms_slip_up  -- Updated schema reference
        GROUP BY day
        ORDER BY day
    ) subquery;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;
$function$
;


create schema if not exists "schools";

create table "schools"."school_activities" (
    "school_id" text not null,
    "org_name" text not null,
    "title" text not null,
    "date_time" timestamp with time zone not null,
    "link" text not null,
    "subtitle" text,
    "description" text,
    "facilitated_by" text,
    "location" text,
    "phone_number" text,
    "email" text,
    "repeats" school_activity_repeat_rate,
    "sub_links" json,
    "id" bigint generated by default as identity not null
);


alter table "schools"."school_activities" enable row level security;

create table "schools"."school_message_claire_prompts" (
    "id" bigint generated by default as identity not null,
    "school_message" bigint not null,
    "prompt" bigint not null
);


alter table "schools"."school_message_claire_prompts" enable row level security;

create table "schools"."school_message_journal_prompts" (
    "id" bigint generated by default as identity not null,
    "school_message" bigint not null,
    "prompt" bigint
);


alter table "schools"."school_message_journal_prompts" enable row level security;

create table "schools"."school_message_meditations" (
    "id" bigint generated by default as identity not null,
    "school_message" bigint not null,
    "meditation" bigint not null
);


alter table "schools"."school_message_meditations" enable row level security;

create table "schools"."school_message_resources" (
    "id" bigint generated by default as identity not null,
    "school_message" bigint not null,
    "resource" bigint
);


alter table "schools"."school_message_resources" enable row level security;

create table "schools"."school_messages" (
    "school_id" text not null,
    "day" bigint not null,
    "title" text not null,
    "subtitle" text not null,
    "message" text not null,
    "id" bigint generated by default as identity not null
);


alter table "schools"."school_messages" enable row level security;

create table "schools"."school_resources" (
    "title" text not null,
    "subtitle" text not null,
    "link" text not null,
    "description" text,
    "badge" text,
    "phone_number" text,
    "location" text,
    "section_title" text not null,
    "school_id" text not null,
    "id" bigint generated by default as identity not null
);


alter table "schools"."school_resources" enable row level security;

create table "schools"."schools" (
    "school_id" text not null,
    "short_name" text not null,
    "long_name" text not null,
    "color1" text not null,
    "color2" text not null,
    "reddit_flair" text,
    "sf_symbol_icon" text
);


alter table "schools"."schools" enable row level security;

CREATE UNIQUE INDEX school_activities_pkey ON schools.school_activities USING btree (id);

CREATE UNIQUE INDEX school_message_claire_prompts_pkey ON schools.school_message_claire_prompts USING btree (id);

CREATE UNIQUE INDEX school_message_journal_prompts_pkey ON schools.school_message_journal_prompts USING btree (id);

CREATE UNIQUE INDEX school_message_meditations_pkey ON schools.school_message_meditations USING btree (id);

CREATE UNIQUE INDEX school_message_resources_pkey ON schools.school_message_resources USING btree (id);

CREATE UNIQUE INDEX school_messages_pkey ON schools.school_messages USING btree (id);

CREATE UNIQUE INDEX school_resources_pkey ON schools.school_resources USING btree (id);

CREATE UNIQUE INDEX schools_pkey ON schools.schools USING btree (school_id);

alter table "schools"."school_activities" add constraint "school_activities_pkey" PRIMARY KEY using index "school_activities_pkey";

alter table "schools"."school_message_claire_prompts" add constraint "school_message_claire_prompts_pkey" PRIMARY KEY using index "school_message_claire_prompts_pkey";

alter table "schools"."school_message_journal_prompts" add constraint "school_message_journal_prompts_pkey" PRIMARY KEY using index "school_message_journal_prompts_pkey";

alter table "schools"."school_message_meditations" add constraint "school_message_meditations_pkey" PRIMARY KEY using index "school_message_meditations_pkey";

alter table "schools"."school_message_resources" add constraint "school_message_resources_pkey" PRIMARY KEY using index "school_message_resources_pkey";

alter table "schools"."school_messages" add constraint "school_messages_pkey" PRIMARY KEY using index "school_messages_pkey";

alter table "schools"."school_resources" add constraint "school_resources_pkey" PRIMARY KEY using index "school_resources_pkey";

alter table "schools"."schools" add constraint "schools_pkey" PRIMARY KEY using index "schools_pkey";

alter table "schools"."school_activities" add constraint "school_activities_school_id_fkey" FOREIGN KEY (school_id) REFERENCES schools.schools(school_id) ON UPDATE CASCADE not valid;

alter table "schools"."school_activities" validate constraint "school_activities_school_id_fkey";

alter table "schools"."school_message_claire_prompts" add constraint "school_message_claire_prompts_prompt_fkey" FOREIGN KEY (prompt) REFERENCES library.claire_prompts(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_claire_prompts" validate constraint "school_message_claire_prompts_prompt_fkey";

alter table "schools"."school_message_claire_prompts" add constraint "school_message_claire_prompts_school_message_fkey" FOREIGN KEY (school_message) REFERENCES schools.school_messages(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_claire_prompts" validate constraint "school_message_claire_prompts_school_message_fkey";

alter table "schools"."school_message_journal_prompts" add constraint "school_message_journal_prompts_prompt_fkey" FOREIGN KEY (prompt) REFERENCES library.journal_prompts(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_journal_prompts" validate constraint "school_message_journal_prompts_prompt_fkey";

alter table "schools"."school_message_journal_prompts" add constraint "school_message_journal_prompts_school_message_fkey" FOREIGN KEY (school_message) REFERENCES schools.school_messages(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_journal_prompts" validate constraint "school_message_journal_prompts_school_message_fkey";

alter table "schools"."school_message_meditations" add constraint "school_message_meditations_meditation_fkey" FOREIGN KEY (meditation) REFERENCES library.meditations(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_meditations" validate constraint "school_message_meditations_meditation_fkey";

alter table "schools"."school_message_meditations" add constraint "school_message_meditations_school_message_fkey" FOREIGN KEY (school_message) REFERENCES schools.school_messages(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_meditations" validate constraint "school_message_meditations_school_message_fkey";

alter table "schools"."school_message_resources" add constraint "school_message_resources_resource_fkey" FOREIGN KEY (resource) REFERENCES library.resources(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_resources" validate constraint "school_message_resources_resource_fkey";

alter table "schools"."school_message_resources" add constraint "school_message_resources_school_message_fkey" FOREIGN KEY (school_message) REFERENCES schools.school_messages(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."school_message_resources" validate constraint "school_message_resources_school_message_fkey";

alter table "schools"."school_messages" add constraint "school_messages_school_id_fkey" FOREIGN KEY (school_id) REFERENCES schools.schools(school_id) ON UPDATE CASCADE not valid;

alter table "schools"."school_messages" validate constraint "school_messages_school_id_fkey";

alter table "schools"."school_resources" add constraint "school_resources_school_id_fkey" FOREIGN KEY (school_id) REFERENCES schools.schools(school_id) ON UPDATE CASCADE not valid;

alter table "schools"."school_resources" validate constraint "school_resources_school_id_fkey";

grant delete on table "schools"."school_activities" to "anon";

grant insert on table "schools"."school_activities" to "anon";

grant references on table "schools"."school_activities" to "anon";

grant select on table "schools"."school_activities" to "anon";

grant trigger on table "schools"."school_activities" to "anon";

grant truncate on table "schools"."school_activities" to "anon";

grant update on table "schools"."school_activities" to "anon";

grant delete on table "schools"."school_activities" to "authenticated";

grant insert on table "schools"."school_activities" to "authenticated";

grant references on table "schools"."school_activities" to "authenticated";

grant select on table "schools"."school_activities" to "authenticated";

grant trigger on table "schools"."school_activities" to "authenticated";

grant truncate on table "schools"."school_activities" to "authenticated";

grant update on table "schools"."school_activities" to "authenticated";

grant delete on table "schools"."school_message_claire_prompts" to "anon";

grant insert on table "schools"."school_message_claire_prompts" to "anon";

grant references on table "schools"."school_message_claire_prompts" to "anon";

grant select on table "schools"."school_message_claire_prompts" to "anon";

grant trigger on table "schools"."school_message_claire_prompts" to "anon";

grant truncate on table "schools"."school_message_claire_prompts" to "anon";

grant update on table "schools"."school_message_claire_prompts" to "anon";

grant delete on table "schools"."school_message_claire_prompts" to "authenticated";

grant insert on table "schools"."school_message_claire_prompts" to "authenticated";

grant references on table "schools"."school_message_claire_prompts" to "authenticated";

grant select on table "schools"."school_message_claire_prompts" to "authenticated";

grant trigger on table "schools"."school_message_claire_prompts" to "authenticated";

grant truncate on table "schools"."school_message_claire_prompts" to "authenticated";

grant update on table "schools"."school_message_claire_prompts" to "authenticated";

grant delete on table "schools"."school_message_journal_prompts" to "anon";

grant insert on table "schools"."school_message_journal_prompts" to "anon";

grant references on table "schools"."school_message_journal_prompts" to "anon";

grant select on table "schools"."school_message_journal_prompts" to "anon";

grant trigger on table "schools"."school_message_journal_prompts" to "anon";

grant truncate on table "schools"."school_message_journal_prompts" to "anon";

grant update on table "schools"."school_message_journal_prompts" to "anon";

grant delete on table "schools"."school_message_journal_prompts" to "authenticated";

grant insert on table "schools"."school_message_journal_prompts" to "authenticated";

grant references on table "schools"."school_message_journal_prompts" to "authenticated";

grant select on table "schools"."school_message_journal_prompts" to "authenticated";

grant trigger on table "schools"."school_message_journal_prompts" to "authenticated";

grant truncate on table "schools"."school_message_journal_prompts" to "authenticated";

grant update on table "schools"."school_message_journal_prompts" to "authenticated";

grant delete on table "schools"."school_message_meditations" to "anon";

grant insert on table "schools"."school_message_meditations" to "anon";

grant references on table "schools"."school_message_meditations" to "anon";

grant select on table "schools"."school_message_meditations" to "anon";

grant trigger on table "schools"."school_message_meditations" to "anon";

grant truncate on table "schools"."school_message_meditations" to "anon";

grant update on table "schools"."school_message_meditations" to "anon";

grant delete on table "schools"."school_message_meditations" to "authenticated";

grant insert on table "schools"."school_message_meditations" to "authenticated";

grant references on table "schools"."school_message_meditations" to "authenticated";

grant select on table "schools"."school_message_meditations" to "authenticated";

grant trigger on table "schools"."school_message_meditations" to "authenticated";

grant truncate on table "schools"."school_message_meditations" to "authenticated";

grant update on table "schools"."school_message_meditations" to "authenticated";

grant delete on table "schools"."school_message_resources" to "anon";

grant insert on table "schools"."school_message_resources" to "anon";

grant references on table "schools"."school_message_resources" to "anon";

grant select on table "schools"."school_message_resources" to "anon";

grant trigger on table "schools"."school_message_resources" to "anon";

grant truncate on table "schools"."school_message_resources" to "anon";

grant update on table "schools"."school_message_resources" to "anon";

grant delete on table "schools"."school_message_resources" to "authenticated";

grant insert on table "schools"."school_message_resources" to "authenticated";

grant references on table "schools"."school_message_resources" to "authenticated";

grant select on table "schools"."school_message_resources" to "authenticated";

grant trigger on table "schools"."school_message_resources" to "authenticated";

grant truncate on table "schools"."school_message_resources" to "authenticated";

grant update on table "schools"."school_message_resources" to "authenticated";

grant delete on table "schools"."school_messages" to "anon";

grant insert on table "schools"."school_messages" to "anon";

grant references on table "schools"."school_messages" to "anon";

grant select on table "schools"."school_messages" to "anon";

grant trigger on table "schools"."school_messages" to "anon";

grant truncate on table "schools"."school_messages" to "anon";

grant update on table "schools"."school_messages" to "anon";

grant delete on table "schools"."school_messages" to "authenticated";

grant insert on table "schools"."school_messages" to "authenticated";

grant references on table "schools"."school_messages" to "authenticated";

grant select on table "schools"."school_messages" to "authenticated";

grant trigger on table "schools"."school_messages" to "authenticated";

grant truncate on table "schools"."school_messages" to "authenticated";

grant update on table "schools"."school_messages" to "authenticated";

grant delete on table "schools"."school_resources" to "anon";

grant insert on table "schools"."school_resources" to "anon";

grant references on table "schools"."school_resources" to "anon";

grant select on table "schools"."school_resources" to "anon";

grant trigger on table "schools"."school_resources" to "anon";

grant truncate on table "schools"."school_resources" to "anon";

grant update on table "schools"."school_resources" to "anon";

grant delete on table "schools"."school_resources" to "authenticated";

grant insert on table "schools"."school_resources" to "authenticated";

grant references on table "schools"."school_resources" to "authenticated";

grant select on table "schools"."school_resources" to "authenticated";

grant trigger on table "schools"."school_resources" to "authenticated";

grant truncate on table "schools"."school_resources" to "authenticated";

grant update on table "schools"."school_resources" to "authenticated";

grant delete on table "schools"."schools" to "anon";

grant insert on table "schools"."schools" to "anon";

grant references on table "schools"."schools" to "anon";

grant select on table "schools"."schools" to "anon";

grant trigger on table "schools"."schools" to "anon";

grant truncate on table "schools"."schools" to "anon";

grant update on table "schools"."schools" to "anon";

grant delete on table "schools"."schools" to "authenticated";

grant insert on table "schools"."schools" to "authenticated";

grant references on table "schools"."schools" to "authenticated";

grant select on table "schools"."schools" to "authenticated";

grant trigger on table "schools"."schools" to "authenticated";

grant truncate on table "schools"."schools" to "authenticated";

grant update on table "schools"."schools" to "authenticated";

create policy "Disable access for all"
on "schools"."school_activities"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_message_claire_prompts"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_message_journal_prompts"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_message_meditations"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_message_resources"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_messages"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."school_resources"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "schools"."schools"
as permissive
for all
to public
using (false);



drop policy "Enable read access for all users" on "symptoms"."symptom_claire_prompts";

drop policy "Enable read access for all users" on "symptoms"."symptom_messages";

drop policy "Enable read access for all users" on "symptoms"."symptom_reddit_threads";

drop policy "Enable read access for all users" on "symptoms"."symptom_tip_sections";

drop policy "Enable read access for all users" on "symptoms"."symptom_tips";

drop policy "Enable read access for all users" on "symptoms"."symptoms";

create policy "Disable access for all"
on "symptoms"."symptom_claire_prompts"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "symptoms"."symptom_messages"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "symptoms"."symptom_reddit_threads"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "symptoms"."symptom_tip_sections"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "symptoms"."symptom_tips"
as permissive
for all
to public
using (false);


create policy "Disable access for all"
on "symptoms"."symptoms"
as permissive
for all
to public
using (false);



