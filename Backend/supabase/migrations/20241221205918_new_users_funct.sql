set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.create_user(user_data json)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    _auth_id uuid;
    _phone_number text;
    _user_exists boolean;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Get phone number from AUTH users table
    SELECT phone
    INTO _phone_number
    FROM auth.users
    WHERE id = _auth_id;

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
            END
        WHERE auth_id = _auth_id;

        PERFORM comms.sms_clear();
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
                    phone_number = _phone_number
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
                    phone_number
                )
                VALUES (
                    COALESCE(user_data->>'id', _auth_id::text),
                    _auth_id,
                    user_data->>'name',
                    user_data->>'emoji',
                    (user_data->'day_info')::jsonb,
                    user_data->>'fcm_token',
                    user_data->>'adjust_token',
                    _phone_number
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
                phone_number
            )
            VALUES (
                _auth_id::text,
                _auth_id,
                user_data->>'name',
                user_data->>'emoji',
                (user_data->'day_info')::jsonb,
                user_data->>'fcm_token',
                user_data->>'adjust_token',
                _phone_number
            );
        END IF;
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$function$
;


