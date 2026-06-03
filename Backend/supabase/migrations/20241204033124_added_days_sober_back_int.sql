set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.upsert_user(user_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    existing_user users%ROWTYPE;
BEGIN
    -- Get existing user data if it exists
    SELECT * INTO existing_user FROM users WHERE id = user_data->>'id';

    -- Insert the record with default values, or do nothing if it exists
    INSERT INTO users (
        id,
        name,
        emoji,
        day_info,
        fcm_token,
        show_in_group_rank,
        start_date,
        days_sober
    )
    VALUES (
        user_data->>'id',
        COALESCE(user_data->>'name', existing_user.name),
        COALESCE(user_data->>'emoji', existing_user.emoji),
        CASE
            WHEN user_data ? 'day_info' THEN
                (user_data->'day_info')
            WHEN user_data ? 'days_sober' THEN
                programs.convert_legacy_to_day_info(
                    COALESCE(
                        (user_data->>'start_date')::timestamptz,
                        existing_user.start_date,
                        (SELECT start_date FROM programs.convert_day_info_to_legacy(existing_user.day_info))
                    ),
                    ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
                )
            ELSE
                existing_user.day_info
        END,
        COALESCE(user_data->>'fcm_token', existing_user.fcm_token),
        COALESCE((user_data->>'show_in_group_rank')::boolean, existing_user.show_in_group_rank),
        CASE
            WHEN user_data ? 'start_date' THEN
                (user_data->>'start_date')::timestamptz
            ELSE
                existing_user.start_date
        END,
        CASE
            WHEN user_data ? 'days_sober' THEN
                ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
            ELSE
                existing_user.days_sober
        END
    )
    ON CONFLICT (id)
    DO UPDATE
    SET name = COALESCE(user_data->>'name', users.name),
        emoji = COALESCE(user_data->>'emoji', users.emoji),
        day_info = CASE 
            WHEN user_data ? 'day_info' THEN
                (user_data->'day_info')
            WHEN user_data ? 'days_sober' THEN
                programs.convert_legacy_to_day_info(
                    COALESCE(
                        (user_data->>'start_date')::timestamptz,
                        existing_user.start_date,
                        (SELECT start_date FROM programs.convert_day_info_to_legacy(existing_user.day_info))
                    ),
                    ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
                )
            ELSE 
                users.day_info
        END,
        fcm_token = COALESCE(user_data->>'fcm_token', users.fcm_token),
        show_in_group_rank = COALESCE((user_data->>'show_in_group_rank')::boolean, users.show_in_group_rank),
        start_date = CASE
            WHEN user_data ? 'start_date' THEN
                (user_data->>'start_date')::timestamptz
            ELSE
                users.start_date
        END,
        days_sober = CASE
            WHEN user_data ? 'days_sober' THEN
                ARRAY(SELECT jsonb_array_elements_text(user_data->'days_sober')::boolean)
            ELSE
                users.days_sober
        END;
END;$function$
;


