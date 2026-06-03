alter table "public"."users" add column "platform" text not null default 'ios'::text;

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.create_user(user_data json)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    _auth_id uuid;
    _phone_number text;
    _email text;
    _user_exists boolean;
    _new_logging_id text;
    _platform text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Get phone number from AUTH users table
    SELECT phone, email::text
    INTO _phone_number, _email
    FROM auth.users
    WHERE id = _auth_id;
    
    -- Extract the new logging_id from user_data
    _new_logging_id := user_data->>'logging_id';

    -- Extract and validate platform
    _platform := user_data->>'platform';
    
    -- Validate platform - only allow 'ios' or 'android', default to 'ios'
    IF _platform IS NULL OR _platform NOT IN ('ios', 'android') THEN
        _platform := 'ios';
    END IF;

    -- First check: Look for existing user by auth_id
    SELECT EXISTS (
        SELECT 1 FROM public.users WHERE auth_id = _auth_id
    ) INTO _user_exists;

    IF _user_exists THEN
        -- Update existing user with non-null values (auth_id match case)
        UPDATE public.users
        SET
            name = COALESCE(user_data->>'name', name),
            emoji = COALESCE(user_data->>'emoji', emoji),
            day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
            fcm_token = CASE 
                WHEN user_data->>'fcm_token' is not null THEN user_data->>'fcm_token'
                ELSE fcm_token
            END,
            adjust_token = CASE 
                WHEN user_data->>'adjust_token' is not null THEN user_data->>'adjust_token'
                ELSE adjust_token
            END,
            platform = _platform,
            -- Add new logging_id to array if it's not already present
            logging_id = CASE
                WHEN _new_logging_id IS NOT NULL THEN
                    CASE
                        WHEN logging_id IS NULL THEN ARRAY[_new_logging_id]::text[]
                        WHEN NOT (_new_logging_id = ANY(logging_id)) THEN array_append(logging_id, _new_logging_id)
                        ELSE logging_id
                    END
                ELSE logging_id
            END
        WHERE auth_id = _auth_id;

        -- CLEAR SMS
        PERFORM public.sms_clear();
    ELSE
        -- Second check: If ID provided, look for existing user by ID with null auth_id
        IF user_data->>'id' IS NOT NULL THEN
            SELECT EXISTS (
                SELECT 1 
                FROM public.users 
                WHERE id = user_data->>'id' 
                AND auth_id IS NULL
            ) INTO _user_exists;
            
            IF _user_exists THEN
                -- Update existing user with non-null values and set auth_id (ID match case)
                UPDATE public.users
                SET
                    auth_id = _auth_id,
                    name = COALESCE(user_data->>'name', name),
                    emoji = COALESCE(user_data->>'emoji', emoji),
                    day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
                    fcm_token = CASE 
                        WHEN user_data->>'fcm_token' is not null THEN user_data->>'fcm_token'
                        ELSE fcm_token
                    END,
                    adjust_token = CASE 
                        WHEN user_data->>'adjust_token' is not null THEN user_data->>'adjust_token'
                        ELSE adjust_token
                    END,
                    platform = _platform,
                    phone_number = _phone_number,
                    email = _email,
                    -- Add new logging_id to array if it's not already present
                    logging_id = CASE
                        WHEN _new_logging_id IS NOT NULL THEN
                            CASE
                                WHEN logging_id IS NULL THEN ARRAY[_new_logging_id]::text[]
                                WHEN NOT (_new_logging_id = ANY(logging_id)) THEN array_append(logging_id, _new_logging_id)
                                ELSE logging_id
                            END
                        ELSE logging_id
                    END
                WHERE id = user_data->>'id';
            ELSE
                -- Insert new user
                INSERT INTO public.users (
                    id,
                    auth_id,
                    name,
                    emoji,
                    day_info,
                    fcm_token,
                    adjust_token,
                    phone_number,
                    email,
                    platform,
                    logging_id
                )
                VALUES (
                    COALESCE(user_data->>'id', _auth_id::text),
                    _auth_id,
                    user_data->>'name',
                    user_data->>'emoji',
                    (user_data->'day_info')::jsonb,
                    user_data->>'fcm_token',
                    user_data->>'adjust_token',
                    _phone_number,
                    _email,
                    _platform,
                    CASE 
                        WHEN _new_logging_id IS NOT NULL THEN ARRAY[_new_logging_id]::text[]
                        ELSE NULL
                    END
                );
            END IF;
        ELSE
            -- Insert new user (no ID provided case)
            INSERT INTO public.users (
                id,
                auth_id,
                name,
                emoji,
                day_info,
                fcm_token,
                adjust_token,
                phone_number,
                email,
                platform,
                logging_id
            )
            VALUES (
                _auth_id::text,
                _auth_id,
                user_data->>'name',
                user_data->>'emoji',
                (user_data->'day_info')::jsonb,
                user_data->>'fcm_token',
                user_data->>'adjust_token',
                _phone_number,
                _email,
                _platform,
                CASE 
                    WHEN _new_logging_id IS NOT NULL THEN ARRAY[_new_logging_id]::text[]
                    ELSE NULL
                END
            );
        END IF;
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$function$
;

CREATE OR REPLACE FUNCTION public.upsert_user(user_data jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    existing_user users%ROWTYPE;
    _platform text;
BEGIN
    -- Get existing user data if it exists
    SELECT * INTO existing_user FROM users WHERE id = user_data->>'id';

    -- Extract and validate platform
    _platform := user_data->>'platform';
    
    -- Validate platform - only allow 'ios' or 'android', default to 'ios'
    IF _platform IS NULL OR _platform NOT IN ('ios', 'android') THEN
        _platform := 'ios';
    END IF;

    -- Insert the record with default values, or do nothing if it exists
    INSERT INTO users (
        id,
        name,
        emoji,
        day_info,
        fcm_token,
        show_in_group_rank,
        start_date,
        days_sober,
        platform
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
        END,
        _platform
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
                        users.start_date,
                        (SELECT start_date FROM programs.convert_day_info_to_legacy(users.day_info))
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
        END,
        platform = _platform;
END;$function$
;


