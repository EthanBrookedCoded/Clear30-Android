-- Fix: create_user function was not setting peer_support_migrated/peer_support_migrated_at
-- on INSERT, so new users got the column default (false) instead of true.

-- 1. Update create_user to include peer_support_migrated in both INSERT paths
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
            sms_settings = CASE
                WHEN user_data->'sms_settings' is not null THEN (user_data->'sms_settings')::jsonb
                ELSE sms_settings
            END,
            platform = _platform,
            -- AppStack fields: only update if provided and current value is null (preserve existing attribution)
            appstack_id = CASE
                WHEN user_data->>'appstack_id' is not null AND appstack_id IS NULL
                THEN user_data->>'appstack_id'
                ELSE appstack_id
            END,
            appstack_attribution = CASE
                WHEN user_data->'appstack_attribution' is not null AND appstack_attribution IS NULL
                THEN (user_data->'appstack_attribution')::jsonb
                ELSE appstack_attribution
            END,
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
                    sms_settings = CASE
                        WHEN user_data->'sms_settings' is not null THEN (user_data->'sms_settings')::jsonb
                        ELSE sms_settings
                    END,
                    platform = _platform,
                    phone_number = _phone_number,
                    email = _email,
                    -- AppStack fields: only update if provided and current value is null
                    appstack_id = CASE
                        WHEN user_data->>'appstack_id' is not null AND appstack_id IS NULL
                        THEN user_data->>'appstack_id'
                        ELSE appstack_id
                    END,
                    appstack_attribution = CASE
                        WHEN user_data->'appstack_attribution' is not null AND appstack_attribution IS NULL
                        THEN (user_data->'appstack_attribution')::jsonb
                        ELSE appstack_attribution
                    END,
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
                    sms_settings,
                    phone_number,
                    email,
                    platform,
                    logging_id,
                    appstack_id,
                    appstack_attribution,
                    peer_support_migrated,
                    peer_support_migrated_at
                )
                VALUES (
                    COALESCE(user_data->>'id', _auth_id::text),
                    _auth_id,
                    user_data->>'name',
                    user_data->>'emoji',
                    (user_data->'day_info')::jsonb,
                    user_data->>'fcm_token',
                    user_data->>'adjust_token',
                    (user_data->'sms_settings')::jsonb,
                    _phone_number,
                    _email,
                    _platform,
                    CASE
                        WHEN _new_logging_id IS NOT NULL THEN ARRAY[_new_logging_id]::text[]
                        ELSE NULL
                    END,
                    user_data->>'appstack_id',
                    (user_data->'appstack_attribution')::jsonb,
                    true,
                    now()
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
                sms_settings,
                phone_number,
                email,
                platform,
                logging_id,
                appstack_id,
                appstack_attribution,
                peer_support_migrated,
                peer_support_migrated_at
            )
            VALUES (
                _auth_id::text,
                _auth_id,
                user_data->>'name',
                user_data->>'emoji',
                (user_data->'day_info')::jsonb,
                user_data->>'fcm_token',
                user_data->>'adjust_token',
                (user_data->'sms_settings')::jsonb,
                _phone_number,
                _email,
                _platform,
                CASE
                    WHEN _new_logging_id IS NOT NULL THEN ARRAY[_new_logging_id]::text[]
                    ELSE NULL
                END,
                user_data->>'appstack_id',
                (user_data->'appstack_attribution')::jsonb,
                true,
                now()
            );
        END IF;
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$function$;

-- 2. Change column default to true so any other insert paths get the right value
ALTER TABLE public.users ALTER COLUMN peer_support_migrated SET DEFAULT true;
