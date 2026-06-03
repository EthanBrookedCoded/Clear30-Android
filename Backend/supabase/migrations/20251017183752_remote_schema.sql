set check_function_bodies = off;

CREATE OR REPLACE FUNCTION achievements.calculate_achievement_stats()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'achievements', 'pg_temp'
AS $function$
DECLARE
    total_users INTEGER;
    achievement_record RECORD;
    earned_count INTEGER;
    percentage DECIMAL(5,2);
BEGIN
    -- Get total unique user count from achievements.user_achievements table
    SELECT COUNT(DISTINCT user_id) INTO total_users FROM achievements.user_achievements;
    
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
 SECURITY DEFINER
AS $function$
BEGIN
    -- Recalculate stats for the specific achievement
    PERFORM achievements.calculate_achievement_stats();
    RETURN NEW;
END;
$function$
;

grant delete on table "achievements"."definitions" to "anon";

grant insert on table "achievements"."definitions" to "anon";

grant references on table "achievements"."definitions" to "anon";

grant select on table "achievements"."definitions" to "anon";

grant trigger on table "achievements"."definitions" to "anon";

grant truncate on table "achievements"."definitions" to "anon";

grant update on table "achievements"."definitions" to "anon";

grant delete on table "achievements"."definitions" to "authenticated";

grant insert on table "achievements"."definitions" to "authenticated";

grant references on table "achievements"."definitions" to "authenticated";

grant trigger on table "achievements"."definitions" to "authenticated";

grant truncate on table "achievements"."definitions" to "authenticated";

grant update on table "achievements"."definitions" to "authenticated";

grant delete on table "achievements"."rarities" to "anon";

grant insert on table "achievements"."rarities" to "anon";

grant references on table "achievements"."rarities" to "anon";

grant select on table "achievements"."rarities" to "anon";

grant trigger on table "achievements"."rarities" to "anon";

grant truncate on table "achievements"."rarities" to "anon";

grant update on table "achievements"."rarities" to "anon";

grant delete on table "achievements"."rarities" to "authenticated";

grant insert on table "achievements"."rarities" to "authenticated";

grant references on table "achievements"."rarities" to "authenticated";

grant trigger on table "achievements"."rarities" to "authenticated";

grant truncate on table "achievements"."rarities" to "authenticated";

grant update on table "achievements"."rarities" to "authenticated";

grant delete on table "achievements"."stats" to "anon";

grant insert on table "achievements"."stats" to "anon";

grant references on table "achievements"."stats" to "anon";

grant select on table "achievements"."stats" to "anon";

grant trigger on table "achievements"."stats" to "anon";

grant truncate on table "achievements"."stats" to "anon";

grant update on table "achievements"."stats" to "anon";

grant delete on table "achievements"."stats" to "authenticated";

grant insert on table "achievements"."stats" to "authenticated";

grant references on table "achievements"."stats" to "authenticated";

grant trigger on table "achievements"."stats" to "authenticated";

grant truncate on table "achievements"."stats" to "authenticated";

grant update on table "achievements"."stats" to "authenticated";

grant delete on table "achievements"."user_achievements" to "anon";

grant insert on table "achievements"."user_achievements" to "anon";

grant references on table "achievements"."user_achievements" to "anon";

grant select on table "achievements"."user_achievements" to "anon";

grant trigger on table "achievements"."user_achievements" to "anon";

grant truncate on table "achievements"."user_achievements" to "anon";

grant update on table "achievements"."user_achievements" to "anon";

grant delete on table "achievements"."user_achievements" to "authenticated";

grant references on table "achievements"."user_achievements" to "authenticated";

grant trigger on table "achievements"."user_achievements" to "authenticated";

grant truncate on table "achievements"."user_achievements" to "authenticated";


