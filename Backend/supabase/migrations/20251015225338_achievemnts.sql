create schema if not exists "achievements";

create sequence "achievements"."definitions_id_seq";

create sequence "achievements"."rarities_id_seq";

create sequence "achievements"."user_achievements_id_seq";

create table "achievements"."definitions" (
    "id" integer not null default nextval('achievements.definitions_id_seq'::regclass),
    "key" character varying(100) not null,
    "name" character varying(200) not null,
    "description" text not null,
    "category" character varying(50) not null,
    "subcategory" character varying(50) not null,
    "icon_url" text not null,
    "rarity_id" integer not null,
    "check_type" character varying(50) not null,
    "check_params" jsonb not null default '{}'::jsonb,
    "is_active" boolean not null default true,
    "display_order" integer not null default 0,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "achievements"."definitions" enable row level security;

create table "achievements"."rarities" (
    "id" integer not null default nextval('achievements.rarities_id_seq'::regclass),
    "name" character varying(50) not null,
    "gradient_start" character varying(7) not null,
    "gradient_end" character varying(7) not null,
    "display_order" integer not null default 0,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "achievements"."rarities" enable row level security;

create table "achievements"."stats" (
    "achievement_key" character varying(100) not null,
    "total_earned" integer not null default 0,
    "percentage_earned" numeric(5,2) not null default 0.00,
    "last_calculated_at" timestamp with time zone default now(),
    "total_users_at_calculation" integer not null default 0
);


alter table "achievements"."stats" enable row level security;

create table "achievements"."user_achievements" (
    "id" integer not null default nextval('achievements.user_achievements_id_seq'::regclass),
    "user_id" text not null,
    "achievement_key" character varying(100) not null,
    "earned_at" timestamp with time zone default now(),
    "custom_data" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


alter table "achievements"."user_achievements" enable row level security;

alter sequence "achievements"."definitions_id_seq" owned by "achievements"."definitions"."id";

alter sequence "achievements"."rarities_id_seq" owned by "achievements"."rarities"."id";

alter sequence "achievements"."user_achievements_id_seq" owned by "achievements"."user_achievements"."id";

CREATE UNIQUE INDEX definitions_key_key ON achievements.definitions USING btree (key);

CREATE UNIQUE INDEX definitions_pkey ON achievements.definitions USING btree (id);

CREATE INDEX idx_achievements_definitions_category ON achievements.definitions USING btree (category);

CREATE INDEX idx_achievements_definitions_check_type ON achievements.definitions USING btree (check_type);

CREATE INDEX idx_achievements_definitions_display_order ON achievements.definitions USING btree (display_order);

CREATE INDEX idx_achievements_definitions_is_active ON achievements.definitions USING btree (is_active);

CREATE INDEX idx_achievements_definitions_rarity_id ON achievements.definitions USING btree (rarity_id);

CREATE INDEX idx_achievements_definitions_subcategory ON achievements.definitions USING btree (subcategory);

CREATE INDEX idx_achievements_rarities_display_order ON achievements.rarities USING btree (display_order);

CREATE INDEX idx_achievements_user_achievements_achievement_key ON achievements.user_achievements USING btree (achievement_key);

CREATE INDEX idx_achievements_user_achievements_earned_at ON achievements.user_achievements USING btree (earned_at);

CREATE INDEX idx_achievements_user_achievements_user_id ON achievements.user_achievements USING btree (user_id);

CREATE UNIQUE INDEX rarities_name_key ON achievements.rarities USING btree (name);

CREATE UNIQUE INDEX rarities_pkey ON achievements.rarities USING btree (id);

CREATE UNIQUE INDEX stats_pkey ON achievements.stats USING btree (achievement_key);

CREATE UNIQUE INDEX user_achievements_pkey ON achievements.user_achievements USING btree (id);

CREATE UNIQUE INDEX user_achievements_user_id_achievement_key_key ON achievements.user_achievements USING btree (user_id, achievement_key);

alter table "achievements"."definitions" add constraint "definitions_pkey" PRIMARY KEY using index "definitions_pkey";

alter table "achievements"."rarities" add constraint "rarities_pkey" PRIMARY KEY using index "rarities_pkey";

alter table "achievements"."stats" add constraint "stats_pkey" PRIMARY KEY using index "stats_pkey";

alter table "achievements"."user_achievements" add constraint "user_achievements_pkey" PRIMARY KEY using index "user_achievements_pkey";

alter table "achievements"."definitions" add constraint "definitions_category_check" CHECK (((category)::text = ANY ((ARRAY['without_weed'::character varying, 'in_app_activity'::character varying, 'seasonal'::character varying])::text[]))) not valid;

alter table "achievements"."definitions" validate constraint "definitions_category_check";

alter table "achievements"."definitions" add constraint "definitions_check_type_check" CHECK (((check_type)::text = ANY ((ARRAY['streak'::character varying, 'cumulative'::character varying, 'milestone'::character varying, 'reduction'::character varying, 'count'::character varying])::text[]))) not valid;

alter table "achievements"."definitions" validate constraint "definitions_check_type_check";

alter table "achievements"."definitions" add constraint "definitions_key_key" UNIQUE using index "definitions_key_key";

alter table "achievements"."definitions" add constraint "definitions_rarity_id_fkey" FOREIGN KEY (rarity_id) REFERENCES achievements.rarities(id) ON DELETE RESTRICT not valid;

alter table "achievements"."definitions" validate constraint "definitions_rarity_id_fkey";

alter table "achievements"."definitions" add constraint "definitions_subcategory_check" CHECK (((subcategory)::text = ANY ((ARRAY['time'::character varying, 'money'::character varying, 'firsts'::character varying, 'continuous'::character varying])::text[]))) not valid;

alter table "achievements"."definitions" validate constraint "definitions_subcategory_check";

alter table "achievements"."rarities" add constraint "rarities_name_key" UNIQUE using index "rarities_name_key";

alter table "achievements"."stats" add constraint "stats_achievement_key_fkey" FOREIGN KEY (achievement_key) REFERENCES achievements.definitions(key) ON DELETE CASCADE not valid;

alter table "achievements"."stats" validate constraint "stats_achievement_key_fkey";

alter table "achievements"."user_achievements" add constraint "user_achievements_achievement_key_fkey" FOREIGN KEY (achievement_key) REFERENCES achievements.definitions(key) ON DELETE RESTRICT not valid;

alter table "achievements"."user_achievements" validate constraint "user_achievements_achievement_key_fkey";

alter table "achievements"."user_achievements" add constraint "user_achievements_user_id_achievement_key_key" UNIQUE using index "user_achievements_user_id_achievement_key_key";

alter table "achievements"."user_achievements" add constraint "user_achievements_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "achievements"."user_achievements" validate constraint "user_achievements_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION achievements.calculate_achievement_stats()
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
    total_users INTEGER;
    achievement_record RECORD;
    earned_count INTEGER;
    percentage DECIMAL(5,2);
BEGIN
    -- Get total user count
    SELECT COUNT(*) INTO total_users FROM public.users;
    
    -- Loop through all achievement definitions
    FOR achievement_record IN 
        SELECT key FROM achievements.definitions WHERE is_active = true
    LOOP
        -- Count how many users have earned this achievement
        SELECT COUNT(*) INTO earned_count 
        FROM achievements.user_achievements 
        WHERE achievement_key = achievement_record.key;
        
        -- Calculate percentage
        IF total_users > 0 THEN
            percentage := (earned_count::DECIMAL / total_users::DECIMAL) * 100;
        ELSE
            percentage := 0;
        END IF;
        
        -- Insert or update stats
        INSERT INTO achievements.stats (achievement_key, total_earned, percentage_earned, last_calculated_at, total_users_at_calculation)
        VALUES (achievement_record.key, earned_count, percentage, NOW(), total_users)
        ON CONFLICT (achievement_key) 
        DO UPDATE SET 
            total_earned = earned_count,
            percentage_earned = percentage,
            last_calculated_at = NOW(),
            total_users_at_calculation = total_users;
    END LOOP;
END;
$function$
;

CREATE OR REPLACE FUNCTION achievements.trigger_stats_calculation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    -- Recalculate stats for the specific achievement
    PERFORM achievements.calculate_achievement_stats();
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION achievements.update_updated_at_column()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$function$
;

grant select on table "achievements"."definitions" to "authenticated";

grant delete on table "achievements"."definitions" to "service_role";

grant insert on table "achievements"."definitions" to "service_role";

grant references on table "achievements"."definitions" to "service_role";

grant select on table "achievements"."definitions" to "service_role";

grant trigger on table "achievements"."definitions" to "service_role";

grant truncate on table "achievements"."definitions" to "service_role";

grant update on table "achievements"."definitions" to "service_role";

grant select on table "achievements"."rarities" to "authenticated";

grant delete on table "achievements"."rarities" to "service_role";

grant insert on table "achievements"."rarities" to "service_role";

grant references on table "achievements"."rarities" to "service_role";

grant select on table "achievements"."rarities" to "service_role";

grant trigger on table "achievements"."rarities" to "service_role";

grant truncate on table "achievements"."rarities" to "service_role";

grant update on table "achievements"."rarities" to "service_role";

grant select on table "achievements"."stats" to "authenticated";

grant delete on table "achievements"."stats" to "service_role";

grant insert on table "achievements"."stats" to "service_role";

grant references on table "achievements"."stats" to "service_role";

grant select on table "achievements"."stats" to "service_role";

grant trigger on table "achievements"."stats" to "service_role";

grant truncate on table "achievements"."stats" to "service_role";

grant update on table "achievements"."stats" to "service_role";

grant insert on table "achievements"."user_achievements" to "authenticated";

grant select on table "achievements"."user_achievements" to "authenticated";

grant update on table "achievements"."user_achievements" to "authenticated";

grant delete on table "achievements"."user_achievements" to "service_role";

grant insert on table "achievements"."user_achievements" to "service_role";

grant references on table "achievements"."user_achievements" to "service_role";

grant select on table "achievements"."user_achievements" to "service_role";

grant trigger on table "achievements"."user_achievements" to "service_role";

grant truncate on table "achievements"."user_achievements" to "service_role";

grant update on table "achievements"."user_achievements" to "service_role";

create policy "Definitions are viewable by authenticated users"
on "achievements"."definitions"
as permissive
for select
to public
using ((auth.uid() IS NOT NULL));


create policy "Rarities are viewable by authenticated users"
on "achievements"."rarities"
as permissive
for select
to public
using ((auth.uid() IS NOT NULL));


create policy "Stats are viewable by authenticated users"
on "achievements"."stats"
as permissive
for select
to public
using ((auth.uid() IS NOT NULL));


create policy "Users can insert their own achievements"
on "achievements"."user_achievements"
as permissive
for insert
to public
with check ((get_user_id() = user_id));


create policy "Users can update their own achievements"
on "achievements"."user_achievements"
as permissive
for update
to public
using ((get_user_id() = user_id));


create policy "Users can view their own achievements"
on "achievements"."user_achievements"
as permissive
for select
to public
using ((get_user_id() = user_id));


CREATE TRIGGER update_achievements_definitions_updated_at BEFORE UPDATE ON achievements.definitions FOR EACH ROW EXECUTE FUNCTION achievements.update_updated_at_column();

CREATE TRIGGER update_achievements_rarities_updated_at BEFORE UPDATE ON achievements.rarities FOR EACH ROW EXECUTE FUNCTION achievements.update_updated_at_column();

CREATE TRIGGER trigger_achievement_stats_update AFTER INSERT ON achievements.user_achievements FOR EACH ROW EXECUTE FUNCTION achievements.trigger_stats_calculation();

CREATE TRIGGER update_achievements_user_achievements_updated_at BEFORE UPDATE ON achievements.user_achievements FOR EACH ROW EXECUTE FUNCTION achievements.update_updated_at_column();




grant usage on schema achievements to "anon";
grant usage on schema achievements to "authenticated";
grant usage on schema achievements to "service_role";