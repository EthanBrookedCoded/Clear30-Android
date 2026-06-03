drop policy "Disable public access" on "public"."users";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.create_user(user_data json)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    _auth_id uuid;
    _phone_number text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Get phone number from auth.users table
    SELECT phone
    INTO _phone_number
    FROM auth.users
    WHERE id = _auth_id;

    -- Check if user exists
    IF EXISTS (SELECT 1 FROM public.users WHERE auth_id = _auth_id) THEN
        -- Update existing user with non-null values
        UPDATE public.users
        SET
            name = COALESCE(user_data->>'name', name),
            emoji = COALESCE(user_data->>'emoji', emoji),
            day_info = COALESCE((user_data->'day_info')::jsonb, day_info),
            fcm_token = CASE 
                WHEN user_data ? 'fcm_token' THEN user_data->>'fcm_token'
                ELSE fcm_token
            END
        WHERE auth_id = _auth_id;
    ELSE
        -- Insert new user
        INSERT INTO public.users (
            id,
            auth_id,
            name,
            emoji,
            day_info,
            fcm_token,
            phone_number
        )
        VALUES (
            _auth_id::text,
            _auth_id,
            user_data->>'name',
            user_data->>'emoji',
            (user_data->'day_info')::jsonb,
            user_data->>'fcm_token',
            _phone_number
        );
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;
$function$
;

create policy "Enable SELECT for authed users"
on "public"."users"
as permissive
for select
to public
using ((auth_id = auth.uid()));


create policy "Enable UPDATE for authed users"
on "public"."users"
as permissive
for update
to public
using ((auth_id = auth.uid()))
with check (((auth_id = auth.uid()) AND (id = id)));



