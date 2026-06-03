set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.calculate_sober_days(p_user_id text)
 RETURNS integer
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_sober_days INTEGER := 0;
    v_day_info JSONB;
    i INTEGER;
    v_obj JSONB;
    v_sober BOOLEAN;
BEGIN
    -- Get user day_info
    SELECT day_info INTO v_day_info
    FROM public.users WHERE id = p_user_id;

    -- day_info is array of pairs: [date, {sober: bool}, date, {sober: bool}, ...]
    -- Count TOTAL sober days (not consecutive)
    -- Check that day_info is actually an array before getting length
    IF v_day_info IS NOT NULL
       AND jsonb_typeof(v_day_info) = 'array'
       AND jsonb_array_length(v_day_info) > 0 THEN
        FOR i IN 1..(jsonb_array_length(v_day_info) - 1) BY 2 LOOP
            v_obj := v_day_info->i;
            v_sober := (v_obj->>'sober')::BOOLEAN;
            IF v_sober = TRUE THEN
                v_sober_days := v_sober_days + 1;
            END IF;
        END LOOP;
    END IF;

    RETURN v_sober_days;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.get_last_check_in_date(p_user_id text)
 RETURNS timestamp with time zone
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_day_info JSONB;
    v_last_date TEXT;
BEGIN
    -- Get user day_info
    SELECT day_info INTO v_day_info
    FROM public.users WHERE id = p_user_id;

    -- day_info is array of pairs: [date, {sober: bool}, date, {sober: bool}, ...]
    -- Last date is at index (length - 2), second to last element
    -- Check that day_info is actually an array before getting length
    IF v_day_info IS NOT NULL
       AND jsonb_typeof(v_day_info) = 'array'
       AND jsonb_array_length(v_day_info) >= 2 THEN
        v_last_date := v_day_info->>(jsonb_array_length(v_day_info) - 2);
        RETURN v_last_date::TIMESTAMPTZ;
    END IF;

    RETURN NULL;
END;
$function$
;

CREATE OR REPLACE FUNCTION comms.notify_slack_dr_fred()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
  v_user_name TEXT;
  v_timestamp TEXT;
BEGIN
  -- Only process inbound messages (outbound = false)
  IF NEW.outbound = true THEN
    RETURN NEW;
  END IF;

  -- Get user name, email and phone from public.users
  IF NEW.user_id IS NOT NULL THEN
    SELECT 
      COALESCE(pu.name, 'N/A') as name
    INTO v_user_name
    FROM public.users pu
    WHERE pu.id = NEW.user_id 
       OR pu.auth_id::text = NEW.user_id
    LIMIT 1;
  END IF;

  -- Default values if still null
  v_user_name := COALESCE(v_user_name, 'N/A');

  -- Format timestamp
  v_timestamp := to_char(NEW.created_at, 'MM/DD/YYYY, HH12:MI:SS AM');

  -- Insert into slack_notifications
  INSERT INTO comms.slack_notifications (
    channel_type,
    title,
    message,
    metadata,
    status,
    priority
  ) VALUES (
    'fred',
    'New Dr. Fred Message',
    NEW.text,
    jsonb_build_object(
      'message_id', NEW.id,
      'user_id', NEW.user_id,
      'user_name', v_user_name,
      'received_at', v_timestamp,
      'blocks', jsonb_build_array(
        jsonb_build_object(
          'type', 'header',
          'text', jsonb_build_object(
            'type', 'plain_text',
            'text', '👨‍⚕️ New Dr. Fred Message'
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'fields', jsonb_build_array(
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*User:* ' || COALESCE(NEW.user_id, 'N/A')
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Name:* ' || v_user_name
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Received at:* ' || v_timestamp
            ),
            jsonb_build_object(
              'type', 'mrkdwn',
              'text', '*Message ID:* ' || NEW.id
            )
          )
        ),
        jsonb_build_object(
          'type', 'section',
          'text', jsonb_build_object(
            'type', 'mrkdwn',
            'text', '*Message:*' || chr(10) || NEW.text
          )
        ),
        jsonb_build_object(
          'type', 'actions',
          'elements', jsonb_build_array(
            jsonb_build_object(
              'type', 'button',
              'text', jsonb_build_object(
                'type', 'plain_text',
                'text', 'Reply in Panel'
              ),
              'url', 'https://clear30.org/panel'
            )
          )
        )
      )
    ),
    'pending',
    'normal'
  );

  RETURN NEW;
END;$function$
;

grant delete on table "comms"."video_testimonial_candidates" to "anon";

grant insert on table "comms"."video_testimonial_candidates" to "anon";

grant references on table "comms"."video_testimonial_candidates" to "anon";

grant select on table "comms"."video_testimonial_candidates" to "anon";

grant trigger on table "comms"."video_testimonial_candidates" to "anon";

grant truncate on table "comms"."video_testimonial_candidates" to "anon";

grant update on table "comms"."video_testimonial_candidates" to "anon";

grant delete on table "comms"."video_testimonial_candidates" to "authenticated";

grant insert on table "comms"."video_testimonial_candidates" to "authenticated";

grant references on table "comms"."video_testimonial_candidates" to "authenticated";

grant select on table "comms"."video_testimonial_candidates" to "authenticated";

grant trigger on table "comms"."video_testimonial_candidates" to "authenticated";

grant truncate on table "comms"."video_testimonial_candidates" to "authenticated";

grant update on table "comms"."video_testimonial_candidates" to "authenticated";

grant delete on table "comms"."video_testimonial_candidates" to "service_role";

grant insert on table "comms"."video_testimonial_candidates" to "service_role";

grant references on table "comms"."video_testimonial_candidates" to "service_role";

grant select on table "comms"."video_testimonial_candidates" to "service_role";

grant trigger on table "comms"."video_testimonial_candidates" to "service_role";

grant truncate on table "comms"."video_testimonial_candidates" to "service_role";

grant update on table "comms"."video_testimonial_candidates" to "service_role";


