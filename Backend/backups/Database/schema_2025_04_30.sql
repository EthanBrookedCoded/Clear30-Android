

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "comms";


ALTER SCHEMA "comms" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "community";


ALTER SCHEMA "community" OWNER TO "postgres";


COMMENT ON SCHEMA "community" IS 'Schema for community-related features including posts, comments, reactions, and tags. Anonymous users can view content, while authenticated users can create and update their own content.';



CREATE EXTENSION IF NOT EXISTS "pg_cron" WITH SCHEMA "pg_catalog";






CREATE SCHEMA IF NOT EXISTS "events";


ALTER SCHEMA "events" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "groups";


ALTER SCHEMA "groups" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "library";


ALTER SCHEMA "library" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";






CREATE SCHEMA IF NOT EXISTS "payment";


ALTER SCHEMA "payment" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pgsodium";






CREATE SCHEMA IF NOT EXISTS "programs";


ALTER SCHEMA "programs" OWNER TO "postgres";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE SCHEMA IF NOT EXISTS "schools";


ALTER SCHEMA "schools" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "symptoms";


ALTER SCHEMA "symptoms" OWNER TO "postgres";


CREATE SCHEMA IF NOT EXISTS "views";


ALTER SCHEMA "views" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "http" WITH SCHEMA "public";






CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgjwt" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE TYPE "comms"."direction" AS ENUM (
    'inbound',
    'outbound'
);


ALTER TYPE "comms"."direction" OWNER TO "postgres";


CREATE TYPE "community"."activity_action" AS ENUM (
    'created',
    'commented',
    'replied',
    'deleted'
);


ALTER TYPE "community"."activity_action" OWNER TO "postgres";


CREATE TYPE "community"."activity_entity_type" AS ENUM (
    'post',
    'comment'
);


ALTER TYPE "community"."activity_entity_type" OWNER TO "postgres";


CREATE TYPE "community"."tag_type" AS ENUM (
    'program',
    'day',
    'event',
    'general',
    'lobby'
);


ALTER TYPE "community"."tag_type" OWNER TO "postgres";


CREATE TYPE "public"."group_activity_type" AS ENUM (
    'joined',
    'smoked',
    'sober',
    'message'
);


ALTER TYPE "public"."group_activity_type" OWNER TO "postgres";


COMMENT ON TYPE "public"."group_activity_type" IS 'Type of group activity';



CREATE TYPE "public"."school_activity_repeat_rate" AS ENUM (
    'weekly'
);


ALTER TYPE "public"."school_activity_repeat_rate" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_dr_fred_message_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$DECLARE
   to_user_fcm_token TEXT;
BEGIN
   -- Only trigger for outbound messages
   IF NOT NEW.outbound THEN
       RETURN NEW;
   END IF;

   -- Check if the user has an fcm_token
   SELECT fcm_token INTO to_user_fcm_token
   FROM users
   WHERE id = NEW.user_id;

   -- If there is no fcm_token, just return and do nothing
   IF to_user_fcm_token IS NULL THEN
       RETURN NEW;
   END IF;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
   VALUES (
       NEW.user_id,
       '👨‍⚕️ Dr. Fred sent you a message!',
       NEW.text,  -- The message content
       '{"type": "drFred"}'::jsonb,
       NOW()
   );

   RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."add_dr_fred_message_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_group_message_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    -- Declare variables to hold the data we'll fetch
    receiver_record RECORD;
    sender_name TEXT;
    sender_emoji TEXT;
    group_name TEXT;
    message_preview TEXT;
BEGIN
    -- Get all users in the group except the sender
    FOR receiver_record IN (
        SELECT 
            u.id,
            u.fcm_token,
            u.notification_settings
        FROM users u
        INNER JOIN groups.group_members gm ON gm.user_id = u.id
        WHERE gm.group_id = NEW.group_id
        AND u.id != NEW.user_id
    ) LOOP
        -- Skip users without FCM token
        IF receiver_record.fcm_token IS NULL THEN
            CONTINUE;
        END IF;

        -- Skip users who have disabled group notifications
        IF receiver_record.notification_settings IS NULL OR 
           receiver_record.notification_settings->'options' IS NULL OR
           NOT COALESCE((receiver_record.notification_settings->'options'->>'Group Notifications')::boolean, false) THEN
            CONTINUE;
        END IF;

        -- Get the sender's info
        SELECT name, emoji INTO sender_name, sender_emoji
        FROM users
        WHERE id = NEW.user_id;

        -- Get the group name
        SELECT name INTO group_name
        FROM groups.groups
        WHERE id = NEW.group_id;

        -- Create message preview (first 30 chars)
        message_preview := SUBSTRING(NEW.message, 1, 30);
        
        -- Insert a notification into the notifications table
        INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
        VALUES (
            receiver_record.id, 
            'Message from ' || sender_emoji || ' ' || sender_name || ' in ' || group_name,
            message_preview,
            '{"type": "groupChatMessage"}'::jsonb,
            NOW()
        );
    END LOOP;

    RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."add_group_message_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_group_note_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$DECLARE
    -- Declare variables to hold the data we'll fetch
    to_user_record RECORD;
    from_user_name TEXT;
    from_user_emoji TEXT;
BEGIN
    -- Get the user record with all necessary information in one query
    SELECT 
        fcm_token,
        notification_settings,
        id
    INTO to_user_record
    FROM users
    WHERE id = NEW.to_member_id;

    -- Check if the user exists
    IF to_user_record IS NULL THEN
        RETURN NULL;
    END IF;

    -- Check if FCM token exists
    IF to_user_record.fcm_token IS NULL THEN
        RETURN NULL;
    END IF;

    -- Check if group notifications are enabled
    -- Note: Accessing the nested "options" object first, then "Group Notifications"
    IF to_user_record.notification_settings IS NULL OR 
       to_user_record.notification_settings->'options' IS NULL OR
       NOT COALESCE((to_user_record.notification_settings->'options'->>'Group Notifications')::boolean, false) THEN
        RETURN NULL;
    END IF;

    -- If we got here, both conditions are met, so fetch the sender's info
    SELECT name, emoji INTO from_user_name, from_user_emoji
    FROM users
    WHERE id = NEW.from_member_id;

    -- Insert a notification into the notifications table
    INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
    VALUES (
        NEW.to_member_id, 
        from_user_emoji || ' ' || from_user_name || ' sent you a note!',  -- Format the title
        NEW.message,  -- The body is the note's message content
        '{"type": "groupNote"}'::jsonb,
        NOW()  -- Set the current timestamp
    );

    RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."add_group_note_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_group_ping_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'comms'
    AS $$DECLARE
   -- Declare variables to hold the data we'll fetch
   to_user_record RECORD;
   from_user_name TEXT;
   from_user_emoji TEXT;
BEGIN
   -- Get the user record with all necessary information in one query
   SELECT 
       fcm_token,
       notification_settings,
       id
   INTO to_user_record
   FROM users
   WHERE id = NEW.to_user_id;

   -- Check if the user exists
   IF to_user_record IS NULL THEN
       RETURN NULL;
   END IF;

   -- Check if FCM token exists
   IF to_user_record.fcm_token IS NULL THEN
       RETURN NULL;
   END IF;

   -- Check if check-in notifications are enabled
   -- Note: Accessing the nested "options" object first, then "Check In Notifications"
   IF to_user_record.notification_settings IS NULL OR 
      to_user_record.notification_settings->'options' IS NULL OR
      NOT COALESCE((to_user_record.notification_settings->'options'->>'Check In Notifications')::boolean, false) THEN
       RETURN NULL;
   END IF;

   -- If we got here, both conditions are met, so fetch the sender's info
   SELECT name, emoji INTO from_user_name, from_user_emoji
   FROM users
   WHERE id = NEW.from_user_id;

   -- Insert a notification into the notifications table
   INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp)
   VALUES (
       NEW.to_user_id, 
       from_user_emoji || ' ' || from_user_name || ' check in!',
       from_user_name || ' wants you to check in on Clear30!',
       '{"type": "groupChatMessage"}'::jsonb,
       NOW()  -- Set the current timestamp
   );

   RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."add_group_ping_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."add_silent_notification"() RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN
  -- Insert due silent notifications into the notifications table
  WITH due_silent_notifications AS (
    SELECT 
      id,
      user_id,
      metadata,
      scheduled_for
    FROM comms.silent_notifications
    WHERE 
      processed = false 
      AND scheduled_for <= NOW()
  ),
  inserted_notifications AS (
    INSERT INTO comms.notifications (
      user_id,
      title,
      body,
      metadata,
      silent
    )
    SELECT
      user_id,
      '',
      '',
      metadata,
      true
    FROM due_silent_notifications
    RETURNING id
  )
  -- Mark processed silent notifications
  UPDATE comms.silent_notifications
  SET processed = true
  WHERE id IN (SELECT id FROM due_silent_notifications);
END;$$;


ALTER FUNCTION "comms"."add_silent_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."cancel_sms_broadcast"("p_broadcast_id" bigint) RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_success BOOLEAN := FALSE;
BEGIN
    -- Update the broadcast status
    UPDATE comms.sms_broadcasts
    SET is_canceled = TRUE
    WHERE id = p_broadcast_id;
    
    IF FOUND THEN
        -- Cancel all unsent SMS messages related to this broadcast
        UPDATE comms.sms_messages
        SET canceled = TRUE
        WHERE broadcast_id = p_broadcast_id
        AND sent_at IS NULL;
        
        v_success := TRUE;
    END IF;
    
    RETURN v_success;
END;
$$;


ALTER FUNCTION "comms"."cancel_sms_broadcast"("p_broadcast_id" bigint) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."create_sms_broadcast"("p_message" "text", "p_user_ids" "text"[], "p_batch_size" integer DEFAULT 100, "p_batch_interval" integer DEFAULT 2, "p_rest_period" integer DEFAULT 300) RETURNS bigint
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_broadcast_id BIGINT;
    v_user_id TEXT;
    v_user_phone TEXT;
    v_next_schedule_time TIMESTAMPTZ;
    v_now TIMESTAMPTZ := NOW();
    v_message_count INTEGER := 0;
BEGIN
    -- Insert the broadcast
    INSERT INTO comms.sms_broadcasts (
        message,
        batch_size,
        batch_interval,
        rest_period
    ) VALUES (
        p_message,
        p_batch_size,
        p_batch_interval,
        p_rest_period
    ) RETURNING id INTO v_broadcast_id;
    
    -- Initialize scheduling time
    v_next_schedule_time := v_now;
    
    -- Process all recipients and create SMS messages immediately
    FOREACH v_user_id IN ARRAY p_user_ids
    LOOP
        -- Get user's phone number
        SELECT phone_number INTO v_user_phone
        FROM public.users
        WHERE id = v_user_id;
        
        -- Only process if user has a valid phone number
        IF v_user_phone IS NOT NULL AND v_user_phone != '' THEN
            -- Calculate the next scheduled time based on batch interval and rest periods
            -- Every batch_size messages, add a rest_period to the schedule time
            IF (v_message_count > 0) AND (v_message_count % p_batch_size) = 0 THEN
                v_next_schedule_time := v_next_schedule_time + 
                                       (p_rest_period * INTERVAL '1 second');
            ELSE
                v_next_schedule_time := v_next_schedule_time + 
                                       (p_batch_interval * INTERVAL '1 second');
            END IF;
            
            -- Insert the SMS message directly into the sms_messages table
            INSERT INTO comms.sms_messages (
                user_id,
                phone_number,
                text,
                outbound,
                scheduled_for,
                created_at,
                canceled,
                broadcast_id
            ) VALUES (
                v_user_id,
                v_user_phone,
                p_message,
                TRUE,
                v_next_schedule_time,
                v_now,
                FALSE,
                v_broadcast_id
            );
            
            v_message_count := v_message_count + 1;
        END IF;
    END LOOP;
    
    RETURN v_broadcast_id;
END;
$$;


ALTER FUNCTION "comms"."create_sms_broadcast"("p_message" "text", "p_user_ids" "text"[], "p_batch_size" integer, "p_batch_interval" integer, "p_rest_period" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "comms"."dr_fred_auto_respond"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$DECLARE
  first_message BOOLEAN;
BEGIN
  -- Check if this is the first message from this user (inbound message)
  -- We only want to auto-respond to the first message from each user
  SELECT COUNT(*) = 1 INTO first_message
  FROM comms.dr_fred
  WHERE user_id = NEW.user_id AND outbound = false;
  
  -- If this is the first message from this user and it's inbound (to Dr. Fred)
  IF first_message AND NEW.outbound = false THEN
    -- Insert an automatic response from Dr. Fred
    INSERT INTO comms.dr_fred (user_id, text, outbound)
    VALUES (NEW.user_id, 'Dr. Fred holds a Ph.D. and offers general feedback and support related to taking a break from cannabis. He is not a medical doctor, and this service is not a substitute for medical or mental health treatment. It does not constitute therapy, diagnosis, or medical advice.

Engaging with this service does not create a professional-client or therapist-patient relationship and assumes you are over 18 years old given the terms you accepted. 

This service is intended solely for general guidance and informational purposes. Response times may vary and may take up to 24 hours.

If you are experiencing a mental health crisis, suicidal thoughts, or a medical emergency, call 911 or 988, or seek help from a licensed medical or mental health professional immediately', true);
  END IF;
  
  RETURN NEW;
END;$$;


ALTER FUNCTION "comms"."dr_fred_auto_respond"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."create_comment_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    v_post_author_id text;
    v_parent_comment_author_id text;
BEGIN
    -- Don't create activity if the actor would be the recipient
    IF NEW.parent_comment_id IS NULL THEN
        -- Direct comment on post
        SELECT user_id INTO v_post_author_id
        FROM community.posts
        WHERE id = NEW.post_id;

        -- Only create activity if the commenter is not the post author
        IF NEW.user_id <> v_post_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_post_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'commented'::community.activity_action
            );
        END IF;
    ELSE
        -- Reply to comment
        SELECT user_id INTO v_parent_comment_author_id
        FROM community.comments
        WHERE id = NEW.parent_comment_id;

        -- Only create activity if the replier is not the parent comment author
        IF NEW.user_id <> v_parent_comment_author_id THEN
            INSERT INTO community.activities (
                actor_id,
                recipient_id,
                entity_type,
                entity_id,
                action
            ) VALUES (
                NEW.user_id,
                v_parent_comment_author_id,
                'comment'::community.activity_entity_type,
                NEW.id,
                'replied'::community.activity_action
            );
        END IF;
    END IF;

    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent comment creation
        RAISE WARNING 'Error creating activity for comment %: %', NEW.id, SQLERRM;
        RETURN NEW;
END;$$;


ALTER FUNCTION "community"."create_comment_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."create_notification_from_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_message text;
    v_title text;
    v_post_title text;
    v_actor_name text;
    v_actor_emoji text;
    v_comment_body text;
    v_notification_settings jsonb;
    v_community_notifications_enabled boolean;
    v_debug_info jsonb;
BEGIN
    -- Only proceed if:
    -- 1. There is a recipient (not a broadcast activity)
    -- 2. The recipient is not the same as the actor (don't notify yourself)
    -- 3. The action is either 'commented' or 'replied' (not for post creation)
    IF NEW.recipient_id IS NULL OR 
       NEW.recipient_id = NEW.actor_id OR 
       NEW.action NOT IN ('commented', 'replied') THEN
        RETURN NEW;
    END IF;
    
    -- Check user's notification settings
    SELECT notification_settings INTO v_notification_settings
    FROM public.users
    WHERE id = NEW.recipient_id;

    -- Create debug info
    v_debug_info := jsonb_build_object(
        'recipient_id', NEW.recipient_id,
        'raw_settings', v_notification_settings
    );
    
    -- Check if community notifications are enabled
    -- Structure: {"all": bool, "options": {"Community Notifications": bool, ...}}
    IF v_notification_settings IS NULL THEN
        -- Default to enabled if no settings are specified
        v_community_notifications_enabled := true;
        v_debug_info := v_debug_info || jsonb_build_object('reason', 'settings_null');
    ELSE
        -- FIRST check if the specific Community Notifications option exists and is set
        IF (v_notification_settings->'options'->>'Community Notifications') IS NOT NULL THEN
            -- Always respect the specific setting if it exists
            v_community_notifications_enabled := (v_notification_settings->'options'->>'Community Notifications')::boolean;
            v_debug_info := v_debug_info || jsonb_build_object('reason', 'using_specific_setting');
        ELSE
            -- If specific setting doesn't exist, fall back to the "all" setting
            v_community_notifications_enabled := COALESCE((v_notification_settings->'all')::boolean, true);
            v_debug_info := v_debug_info || jsonb_build_object('reason', 'using_all_setting');
        END IF;
    END IF;
    
    -- Add the final decision to debug info
    v_debug_info := v_debug_info || jsonb_build_object('notifications_enabled', v_community_notifications_enabled);
    
    -- Log the debug info for diagnosing issues
    RAISE LOG 'Notification preferences check: %', v_debug_info;
    
    -- Exit if community notifications are disabled for this user
    IF NOT v_community_notifications_enabled THEN
        RETURN NEW;
    END IF;
    
    -- Get the actor's info for the notification
    SELECT name, emoji 
    INTO v_actor_name, v_actor_emoji
    FROM public.users 
    WHERE id = NEW.actor_id;
    
    -- Set the notification title based on action type
    IF NEW.action = 'commented' THEN
        -- Get the post title
        SELECT p.title INTO v_post_title
        FROM community.posts p
        WHERE p.id = (
            SELECT c.post_id 
            FROM community.comments c 
            WHERE c.id = NEW.entity_id
        );
        
        -- Get the comment body
        SELECT body INTO v_comment_body
        FROM community.comments
        WHERE id = NEW.entity_id;
        
        v_title := v_actor_emoji || ' ' || v_actor_name || ' commented on your post';
        v_message := v_comment_body;
    ELSIF NEW.action = 'replied' THEN
        -- Get the comment body
        SELECT body INTO v_comment_body
        FROM community.comments
        WHERE id = NEW.entity_id;
        
        v_title := v_actor_emoji || ' ' || v_actor_name || ' replied to your comment';
        v_message := v_comment_body;
    ELSE
        -- Fallback (shouldn't reach here due to the conditions above)
        v_title := 'New activity in Clear30';
        v_message := 'Check your activity feed for details';
    END IF;
    
    -- Insert into comms.notifications table
    INSERT INTO comms.notifications (
        user_id, 
        title, 
        body,
        metadata,
        timestamp
    ) VALUES (
        NEW.recipient_id,
        v_title,
        v_message,
        jsonb_build_object(
            'type', 'community',
            'activity_id', NEW.id,
            'entity_type', NEW.entity_type,
            'entity_id', NEW.entity_id,
            'action', NEW.action,
            'debug', v_debug_info
        ),
        NOW()
    );
    
    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't prevent activity creation
        RAISE WARNING 'Error creating notification for activity %: %', NEW.id, SQLERRM;
        RETURN NEW;
END;
$$;


ALTER FUNCTION "community"."create_notification_from_activity"() OWNER TO "postgres";


COMMENT ON FUNCTION "community"."create_notification_from_activity"() IS 'Creates a notification in comms.notifications when a new activity is created, respecting the user''s notification preferences.
Always checks specific notification categories first before falling back to the "all" setting.';



CREATE OR REPLACE FUNCTION "community"."create_post_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO community.activities (
        actor_id,
        recipient_id,      -- Adding recipient_id: user sees their own post creation
        entity_type,
        entity_id,
        action
    ) VALUES (
        NEW.user_id,
        NEW.user_id,      -- Same user is both actor and recipient
        'post'::community.activity_entity_type,
        NEW.id,
        'created'::community.activity_action
    );
    RETURN NEW;
END;
$$;


ALTER FUNCTION "community"."create_post_activity"() OWNER TO "postgres";


COMMENT ON FUNCTION "community"."create_post_activity"() IS 'Creates an activity record when a post is created, setting the creator as both actor and recipient';



CREATE OR REPLACE FUNCTION "community"."create_post_with_tags"("p_title" "text", "p_content_type" "text", "p_body" "text", "p_video_url" "text", "p_thumbnail_url" "text", "p_user_id" "text", "p_tags" "text"[]) RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
  v_post_id uuid;
  v_tag_id uuid;
  v_tag TEXT;
BEGIN
  -- Input validation
  IF p_title IS NULL OR p_content_type IS NULL THEN
    RAISE EXCEPTION 'Title and content_type are required fields';
  END IF;

  IF p_content_type NOT IN ('text', 'video') THEN
    RAISE EXCEPTION 'Invalid content_type. Must be either text or video';
  END IF;

  IF p_content_type = 'text' AND p_body IS NULL THEN
    RAISE EXCEPTION 'Body is required for text posts';
  END IF;

  IF p_content_type = 'video' AND p_video_url IS NULL THEN
    RAISE EXCEPTION 'Video URL is required for video posts';
  END IF;

  -- Log the start of the operation
  RAISE LOG 'Creating new post with title: %, content_type: %, user_id: %', p_title, p_content_type, p_user_id;

  -- First create the post
  INSERT INTO community.posts (
    title,
    content_type,
    body,
    video_url,
    thumbnail_url, -- New column added
    user_id,
    is_pinned,
    is_hidden,
    created_at,
    updated_at
  ) VALUES (
    p_title,
    p_content_type,
    p_body,
    p_video_url,
    p_thumbnail_url, -- New parameter included
    p_user_id,
    false,
    false,
    NOW(),
    NULL
  ) RETURNING id INTO v_post_id;

  -- Log successful post creation
  RAISE LOG 'Successfully created post with ID: %', v_post_id;

  -- Then handle each tag
  IF array_length(p_tags, 1) > 0 THEN
    FOREACH v_tag IN ARRAY p_tags
    LOOP
      -- Log tag processing
      RAISE LOG 'Processing tag: %', v_tag;

      -- Try to find existing tag or create new one
      INSERT INTO community.tags (name, created_at)
      VALUES (v_tag, NOW())
      ON CONFLICT (name) 
      DO UPDATE SET name = EXCLUDED.name
      RETURNING id INTO v_tag_id;

      -- Create post_tag connection
      INSERT INTO community.post_tags (post_id, tag_id, created_at)
      VALUES (v_post_id, v_tag_id, NOW());

      -- Log successful tag association
      RAISE LOG 'Associated tag % (ID: %) with post %', v_tag, v_tag_id, v_post_id;
    END LOOP;
  END IF;

  RETURN v_post_id;
EXCEPTION
  WHEN others THEN
    -- Log any errors that occur
    RAISE LOG 'Error in create_post_with_tags: %', SQLERRM;
    RAISE EXCEPTION 'An error occurred while creating the post: %', SQLERRM;
END;
$$;


ALTER FUNCTION "community"."create_post_with_tags"("p_title" "text", "p_content_type" "text", "p_body" "text", "p_video_url" "text", "p_thumbnail_url" "text", "p_user_id" "text", "p_tags" "text"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."create_profile"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
  -- Check if a profile with the user_id already exists in community.profiles
  IF NOT EXISTS (SELECT 1 FROM community.profiles WHERE id = NEW.user_id) THEN
    -- If no profile exists, insert a new one using data from public.users
    INSERT INTO community.profiles (id, name, emoji)
    SELECT id, name, emoji
    FROM public.users
    WHERE id = NEW.user_id;
  END IF;
  
  RETURN NEW;
END;$$;


ALTER FUNCTION "community"."create_profile"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."delete_post"("post_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'community', 'public'
    AS $$DECLARE
    v_auth_id uuid;
    v_user_id text;
    v_post_user_id text;
    v_is_admin boolean;
BEGIN
    -- Get the auth_id of the authenticated user
    v_auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF v_auth_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    -- First check if the user is an admin (regardless of being in public.users)
    SELECT EXISTS(SELECT 1 FROM public.admins a WHERE a.auth_id = v_auth_id) 
    INTO v_is_admin;

    -- If user is an admin, they can proceed without further user checks
    IF NOT v_is_admin THEN
        -- Non-admin users must be in the public.users table
        SELECT u.id
        INTO v_user_id
        FROM public.users u
        WHERE u.auth_id = v_auth_id;
        
        -- If not in public.users, raise an exception
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'User profile not found. Auth ID: %', v_auth_id;
        END IF;
    END IF;

    -- Get the post's user_id
    SELECT user_id INTO v_post_user_id
    FROM community.posts
    WHERE id = post_id;

    -- Check if post exists
    IF v_post_user_id IS NULL THEN
        RAISE EXCEPTION 'Post not found';
    END IF;

    -- Admin users can delete any post
    -- Non-admin users can only delete their own posts
    IF NOT v_is_admin AND v_post_user_id != v_user_id THEN
        RAISE EXCEPTION 'Only the post owner or admin can delete the post';
    END IF;

    -- Delete all reactions for the post
    DELETE FROM community.reactions r
    WHERE r.post_id = delete_post.post_id;

    -- Delete all post tags
    DELETE FROM community.post_tags pt
    WHERE pt.post_id = delete_post.post_id;

    -- Delete all reported posts entries
    DELETE FROM community.reported_posts rp
    WHERE rp.post_id = delete_post.post_id;

    -- Delete all comments and their replies using recursive CTE
    WITH RECURSIVE comment_tree AS (
        -- Base case: get all direct comments for the post
        SELECT c.id
        FROM community.comments c
        WHERE c.post_id = delete_post.post_id
        
        UNION ALL
        
        -- Recursive case: get all replies to comments
        SELECT c.id
        FROM community.comments c
        INNER JOIN comment_tree ct ON c.parent_comment_id = ct.id
    )
    DELETE FROM community.comments c
    WHERE c.id IN (SELECT id FROM comment_tree)
    OR c.post_id = delete_post.post_id;

    -- Log additional information about who deleted the post
    INSERT INTO community.deleted_posts_log (
        id, 
        user_id, 
        title, 
        metadata,
        deleted_by_user_id,
        deleted_by_admin
    )
    SELECT 
        p.id,
        p.user_id,
        p.title,
        jsonb_build_object(
            'content_type', p.content_type,
            'view_count', p.view_count,
            'is_pinned', p.is_pinned,
            'is_hidden', p.is_hidden,
            'is_flagged_by_llm', p.is_flagged_by_llm,
            'deleted_by', v_user_id,
            'was_admin_deletion', v_is_admin
        ),
        v_user_id,
        v_is_admin
    FROM community.posts p
    WHERE p.id = delete_post.post_id;

    -- Delete activies about post
    DELETE FROM community.activities a
    WHERE a.entity_type = 'post' AND a.entity_id::text = delete_post.post_id::text;


    -- Finally delete the post itself
    DELETE FROM community.posts p
    WHERE p.id = delete_post.post_id;

    RETURN true;
END;$$;


ALTER FUNCTION "community"."delete_post"("post_id" "uuid") OWNER TO "postgres";


COMMENT ON FUNCTION "community"."delete_post"("post_id" "uuid") IS 'Deletes a post and all its related data (comments, reactions, tags, etc.). Only the post owner or admin can delete posts.';



CREATE OR REPLACE FUNCTION "community"."get_activity_feed"("start_range" integer, "end_range" integer) RETURNS TABLE("id" "uuid", "created_at" timestamp with time zone, "is_read" boolean, "message" "text", "entity_type" "community"."activity_entity_type", "entity_id" "uuid", "post_id" "uuid", "action" "community"."activity_action")
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'community', 'public'
    AS $$DECLARE
    v_user_id text;
BEGIN
    -- Get the authenticated user's ID
    SELECT u.id INTO v_user_id
    FROM public.users u
    WHERE u.auth_id = auth.uid();

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    RETURN QUERY
    WITH post_titles AS (
        -- Get titles for existing
        SELECT 
            p.id AS post_id,
            p.title AS title
        FROM community.posts p
    )
    SELECT 
        a.id,
        a.created_at,
        a.is_read,
        CASE 
            -- Created something
            WHEN a.action = 'created' THEN 
                CASE 
                    -- You created post
                    WHEN a.actor_id = a.recipient_id THEN 
                        'You created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                    -- Someone else created a post
                    ELSE
                        actor.name || ' created a new post "' || 
                        COALESCE(
                            (SELECT pt.title FROM post_titles pt WHERE pt.post_id = a.entity_id),
                            'Untitled'
                        ) || '"'
                END
            WHEN a.action = 'commented' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You commented on your post saying "' || c.body || '"'
                    ELSE actor.name || ' commented on your post saying "' || c.body || '"'
                END
            WHEN a.action = 'replied' THEN
                CASE 
                    WHEN a.actor_id = a.recipient_id THEN 'You replied to your comment saying "' || c.body || '"'
                    ELSE actor.name || ' replied to your comment saying "' || c.body || '"'
                END
        END,
        a.entity_type,
        a.entity_id,
        COALESCE(c.post_id, p.id) as post_id,
        a.action
    FROM community.activities a
    JOIN public.users actor ON actor.id = a.actor_id
    LEFT JOIN community.posts p ON 
        (a.entity_type = 'post' AND a.entity_id = p.id)
    LEFT JOIN community.comments c ON 
        (a.entity_type = 'comment' AND a.entity_id = c.id)
    WHERE a.recipient_id = v_user_id
    ORDER BY a.created_at DESC
    OFFSET start_range LIMIT (end_range - start_range + 1);
END;$$;


ALTER FUNCTION "community"."get_activity_feed"("start_range" integer, "end_range" integer) OWNER TO "postgres";


COMMENT ON FUNCTION "community"."get_activity_feed"("start_range" integer, "end_range" integer) IS 'Returns paginated activity feed for the authenticated user with formatted messages';



CREATE OR REPLACE FUNCTION "community"."get_filtered_posts"("start_range" integer, "end_range" integer, "tag_ids" "uuid"[] DEFAULT NULL::"uuid"[], "only_my_posts" boolean DEFAULT false, "include_pinned" boolean DEFAULT true) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'community', 'public'
    AS $$DECLARE
    v_user_id text;
    v_total_count bigint;
    v_result jsonb;
BEGIN
    -- Get authenticated user's ID if only_my_posts is true
    IF only_my_posts THEN
        SELECT u.id INTO v_user_id
        FROM users u
        WHERE u.auth_id = auth.uid();

        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'User not found';
        END IF;
    END IF;

    -- Get total count first
    WITH base_query AS (
        SELECT DISTINCT p.id
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        WHERE NOT p.is_hidden
        AND (v_user_id IS NULL OR p.user_id = v_user_id)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
    )
    SELECT COUNT(*) INTO v_total_count FROM base_query;

    -- Get posts data and return as single JSON object
    WITH filtered_posts AS (
        SELECT DISTINCT p.*
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        WHERE NOT p.is_hidden
        AND (CASE WHEN only_my_posts THEN p.user_id = v_user_id ELSE true END)
        AND (include_pinned OR NOT p.is_pinned)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        ORDER BY p.is_pinned DESC, p.created_at DESC
        OFFSET start_range LIMIT (end_range - start_range + 1)
    ),
    post_data AS (
        SELECT 
            p.id,
            p.user_id,
            p.title,
            p.content_type,
            p.body,
            p.video_url,
            p.thumbnail_url,
            p.is_pinned,
            p.is_hidden,
            p.is_flagged_by_llm,
            p.view_count,
            p.created_at,
            p.updated_at,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'tag', jsonb_build_object(
                            'id', t.id,
                            'name', t.name,
                            'type', t.type,
                            'color', t.color
                        )
                    )
                )
                FROM community.post_tags pt
                JOIN community.tags t ON t.id = pt.tag_id
                WHERE pt.post_id = p.id
            ) as post_tags,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'emoji', r.emoji,
                        'user_id', r.user_id,
                        'post_id', r.post_id
                    )
                )
                FROM community.reactions r
                WHERE r.post_id = p.id
            ) as reactions,
            (
                SELECT COUNT(*)
                FROM community.comments c
                WHERE c.post_id = p.id
            ) as comments_count
        FROM filtered_posts p
    )
    SELECT jsonb_build_object(
        'data', COALESCE(jsonb_agg(to_jsonb(post_data.*)), '[]'::jsonb),
        'total_count', v_total_count
    ) INTO v_result
    FROM post_data;

    RETURN v_result;
END;$$;


ALTER FUNCTION "community"."get_filtered_posts"("start_range" integer, "end_range" integer, "tag_ids" "uuid"[], "only_my_posts" boolean, "include_pinned" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."get_filtered_posts"("start_range" integer, "end_range" integer, "tag_ids" "uuid"[] DEFAULT NULL::"uuid"[], "only_my_posts" boolean DEFAULT false, "exclude_pinned" boolean DEFAULT false, "min_comments" integer DEFAULT 0, "min_date" timestamp without time zone DEFAULT NULL::timestamp without time zone, "sort_by" "text" DEFAULT 'recent'::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'community', 'public'
    AS $$DECLARE
    v_user_id text;
    v_total_count bigint;
    v_result jsonb;
BEGIN
    -- Validate sort_by parameter
    IF sort_by NOT IN ('recent', 'views', 'engagement') THEN
        RAISE EXCEPTION 'Invalid sort_by parameter. Must be one of: recent, views, engagement';
    END IF;

    -- Get authenticated user's ID for filtering
    -- We need this regardless of only_my_posts to check post ownership
    SELECT u.id INTO v_user_id
    FROM users u
    WHERE u.auth_id = auth.uid();

    -- Get total count with comment count filtering and hidden posts logic
    WITH comment_counts AS (
        SELECT 
            post_id,
            COUNT(*) as count
        FROM community.comments
        GROUP BY post_id
    ),
    base_query AS (
        SELECT DISTINCT p.id
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND (NOT exclude_pinned OR NOT p.is_pinned) -- Skip pinned posts if exclude_pinned is true
        AND (NOT only_my_posts OR p.user_id = v_user_id) -- Filter by author if only_my_posts is true
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        AND COALESCE(cc.count, 0) >= min_comments -- Apply minimum comment threshold
        AND (min_date IS NULL OR p.created_at >= min_date) -- Filter by minimum date
    )
    SELECT COUNT(*) INTO v_total_count FROM base_query;

    -- Get posts data with engagement metrics
    WITH comment_counts AS (
        SELECT 
            post_id,
            COUNT(*) as count
        FROM community.comments
        GROUP BY post_id
    ),
    reaction_counts AS (
        SELECT
            post_id,
            COUNT(*) as count
        FROM community.reactions
        GROUP BY post_id
    ),
    filtered_posts AS (
        SELECT DISTINCT 
            p.*,
            COALESCE(cc.count, 0) as comments_count,
            COALESCE(rc.count, 0) as reactions_count,
            -- Calculate engagement score: views + (comments*5) + (reactions*3)
            (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3)) as engagement_score,
            -- Include the sorting expressions in the SELECT list
            CASE WHEN sort_by = 'recent' AND NOT exclude_pinned THEN p.is_pinned END as sort_pinned,
            CASE WHEN sort_by = 'views' THEN p.view_count
                 WHEN sort_by = 'engagement' THEN (p.view_count + (COALESCE(cc.count, 0) * 5) + (COALESCE(rc.count, 0) * 3))
                 ELSE NULL 
            END as sort_metric,
            CASE WHEN sort_by = 'recent' THEN p.created_at END as sort_date
        FROM community.posts p
        LEFT JOIN community.post_tags pt ON pt.post_id = p.id
        LEFT JOIN comment_counts cc ON cc.post_id = p.id
        LEFT JOIN reaction_counts rc ON rc.post_id = p.id
        WHERE (
            -- Only show hidden posts to their author
            NOT p.is_hidden OR (p.is_hidden AND p.user_id = v_user_id)
        )
        AND (NOT exclude_pinned OR NOT p.is_pinned)
        AND (NOT only_my_posts OR p.user_id = v_user_id)
        AND (
            tag_ids IS NULL 
            OR array_length(tag_ids, 1) IS NULL 
            OR pt.tag_id = ANY(tag_ids)
        )
        AND COALESCE(cc.count, 0) >= min_comments
        AND (min_date IS NULL OR p.created_at >= min_date) -- Filter by minimum date
        ORDER BY 
            sort_pinned DESC,
            sort_metric DESC NULLS LAST,
            sort_date DESC NULLS LAST,
            -- Always include created_at as secondary sort for consistency
            p.created_at DESC
        OFFSET start_range LIMIT (end_range - start_range + 1)
    ),
    post_data AS (
        SELECT 
            p.id,
            p.user_id,
            p.title,
            p.content_type,
            p.body,
            p.video_url,
            p.thumbnail_url,
            p.is_pinned,
            p.is_hidden,
            p.is_flagged_by_llm,
            p.view_count,
            p.created_at,
            p.updated_at,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'tag', jsonb_build_object(
                            'id', t.id,
                            'name', t.name,
                            'type', t.type,
                            'color', t.color
                        )
                    )
                )
                FROM community.post_tags pt
                JOIN community.tags t ON t.id = pt.tag_id
                WHERE pt.post_id = p.id
            ) as post_tags,
            (
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'emoji', r.emoji,
                        'user_id', r.user_id,
                        'post_id', r.post_id
                    )
                )
                FROM community.reactions r
                WHERE r.post_id = p.id
            ) as reactions,
            p.comments_count,
            p.engagement_score
        FROM filtered_posts p
    )
    SELECT jsonb_build_object(
        'data', COALESCE(jsonb_agg(to_jsonb(post_data.*)), '[]'::jsonb),
        'total_count', v_total_count
    ) INTO v_result
    FROM post_data;

    RETURN v_result;
END;$$;


ALTER FUNCTION "community"."get_filtered_posts"("start_range" integer, "end_range" integer, "tag_ids" "uuid"[], "only_my_posts" boolean, "exclude_pinned" boolean, "min_comments" integer, "min_date" timestamp without time zone, "sort_by" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."increment_view_count"("post_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $_$
DECLARE
    multiplier INTEGER := 1;
BEGIN
    -- Check if view-multiplier setting exists
    SELECT 
        CASE 
            WHEN value ~ '^[0-9]+$' THEN value::INTEGER 
            ELSE 1 
        END INTO multiplier
    FROM community.settings
    WHERE id = 'view-multiplier'
    LIMIT 1;
    
    -- If no valid multiplier found, default to 1
    IF multiplier IS NULL OR multiplier < 1 THEN
        multiplier := 1;
    END IF;
    
    -- Update the post view count using the multiplier
    UPDATE community.posts 
    SET view_count = view_count + multiplier 
    WHERE id = post_id;
END;
$_$;


ALTER FUNCTION "community"."increment_view_count"("post_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "community"."set_post_visibility"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
    -- Count reported entries for the same post_id
    IF (SELECT COUNT(*) FROM reported_posts WHERE post_id = NEW.post_id) > 3 THEN
        -- Update the post to set is_hidden to TRUE
        UPDATE posts SET is_hidden = TRUE WHERE post_id = NEW.post_id;
    END IF;
    RETURN NEW;
END;$$;


ALTER FUNCTION "community"."set_post_visibility"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "events"."check_event_sale"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    current_sale_id BIGINT;
BEGIN
    -- Only run this function on INSERT operations
    IF TG_OP != 'INSERT' THEN
        RETURN NEW;
    END IF;
    
    -- Check if there is a running sale in the payment.sale table
    -- A running sale is one where current timestamp is between starts_on and ends_on
    SELECT id INTO current_sale_id
    FROM payment.sale
    WHERE NOW() BETWEEN starts_on AND ends_on
    LIMIT 1;
    
    -- If a current sale exists, add the user to the sale_users table
    IF current_sale_id IS NOT NULL THEN
        -- Add an entry to payment.sale_users
        INSERT INTO payment.sale_users (user_id, sale_id)
        VALUES (NEW.user_id, current_sale_id)
        ON CONFLICT (user_id, sale_id) DO NOTHING;
        
        -- Increment the uses counter in the sale table
        UPDATE payment.sale
        SET uses = uses + 1
        WHERE id = current_sale_id;
    END IF;
    
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Log any errors that occur but don't fail the transaction
    RAISE NOTICE 'Error in events.check_event_sale function: %', SQLERRM;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "events"."check_event_sale"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "events"."get_active_event_pop_ups"() RETURNS TABLE("id" bigint, "event" "text", "start_date" "date", "end_date" "date", "text" "text")
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'events', 'public'
    AS $$
BEGIN
  RETURN QUERY
    SELECT 
      ep.id,
      ep.event,
      ep.start_date,
      ep.end_date,
      ep.text
    FROM 
      events.event_pop_ups ep
    WHERE 
      CURRENT_DATE BETWEEN ep.start_date AND ep.end_date
      AND get_user_id()::text = ANY(ep.user_ids);
END;
$$;


ALTER FUNCTION "events"."get_active_event_pop_ups"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "events"."handle_event_users"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    v_logging_id TEXT;
    current_event_id TEXT;
    matching_user_id TEXT;
BEGIN
    -- Extract the logging_id from the user_id field
    v_logging_id := NEW.user_id;
    
    IF v_logging_id IS NULL THEN
        RETURN NEW;
    END IF;
    
    -- Check if the event is 'event_entered'
    IF NEW.event = 'event_entered' THEN
        -- Get the current running event based on onboarding dates
        -- Include a 1-day grace period on both start and end dates
        SELECT id INTO current_event_id 
        FROM events.events 
        WHERE CURRENT_DATE BETWEEN (onboarding_start_date - INTERVAL '1 day') AND (onboarding_end_date + INTERVAL '1 day')
        LIMIT 1;
        
        IF current_event_id IS NULL THEN
            RETURN NEW; -- No active event found
        END IF;
        
        -- First try: Get the user_id for the user matching that logging_id in the array
        SELECT id INTO matching_user_id 
        FROM public.users 
        WHERE logging_id @> ARRAY[v_logging_id]
        LIMIT 1;
        
        -- Second try: If no user found with logging_id array match, try finding a user with id = logging_id
        IF matching_user_id IS NULL THEN
            SELECT id INTO matching_user_id 
            FROM public.users 
            WHERE id = v_logging_id
            LIMIT 1;
            
            IF matching_user_id IS NULL THEN
                RETURN NEW; -- No matching user found with either method
            END IF;
        END IF;
        
        -- Add a row to the events.event_users table
        INSERT INTO events.event_users (user_id, event_id, entered_at)
        VALUES (matching_user_id, current_event_id, NOW())
        ON CONFLICT (user_id, event_id) DO NOTHING;
        
    -- Check if the event is 'event_left'
    ELSIF NEW.event = 'event_left' THEN
        -- First try: Get the user_id for the user matching that logging_id in the array
        SELECT id INTO matching_user_id 
        FROM public.users 
        WHERE logging_id @> ARRAY[v_logging_id]
        LIMIT 1;
        
        -- Second try: If no user found with logging_id array match, try finding a user with id = logging_id
        IF matching_user_id IS NULL THEN
            SELECT id INTO matching_user_id 
            FROM public.users 
            WHERE id = v_logging_id
            LIMIT 1;
            
            IF matching_user_id IS NULL THEN
                RETURN NEW; -- No matching user found with either method
            END IF;
        END IF;
        
        -- Remove the row from the events.event_users table
        DELETE FROM events.event_users
        WHERE user_id = matching_user_id;
    END IF;
    
    RETURN NEW;
END;$$;


ALTER FUNCTION "events"."handle_event_users"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "events"."increment_waiting_count"("event_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE events.events
    SET num_waiting = num_waiting + 1
    WHERE id = event_id;
END;
$$;


ALTER FUNCTION "events"."increment_waiting_count"("event_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."add_checkin_activity"("activity_type" "text" DEFAULT NULL::"text", "timezone" "text" DEFAULT 'UTC'::"text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$
DECLARE
    current_user_id text;
    current_group_id uuid;
    activity_timestamp timestamptz := NOW();
    mapped_activity_type group_activity_type;
    activity_date date;
    timezone_valid boolean;
BEGIN
    -- Validate the timezone
    SELECT EXISTS (
        SELECT 1 FROM pg_timezone_names WHERE name = timezone
    ) INTO timezone_valid;
    
    IF NOT timezone_valid THEN
        RAISE EXCEPTION 'Invalid timezone: %', timezone;
    END IF;

    -- Get the current user's ID
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'You must be logged in to record an activity';
    END IF;

    -- Get the user's group
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;
    
    -- Calculate the calendar date in the provided timezone
    activity_date := (activity_timestamp AT TIME ZONE timezone)::date;
    
    -- If activity_type is NULL, just remove all 'smoked' and 'sober' activities for the day
    IF activity_type IS NULL THEN
        DELETE FROM groups.group_activity
        WHERE group_id = current_group_id
          AND user_id = current_user_id
          AND activity IN ('smoked', 'sober')
          AND ((timestamp AT TIME ZONE timezone)::date = activity_date);
          
        -- No new activity to insert, so return
        RETURN;
    END IF;
    
    -- Otherwise, map the activity string to the appropriate activity type
    CASE activity_type
        WHEN 'smoked' THEN mapped_activity_type := 'smoked'::group_activity_type;
        WHEN 'sober' THEN mapped_activity_type := 'sober'::group_activity_type;
        ELSE
            RAISE EXCEPTION 'Invalid activity type: %. Only "smoked", "sober", or NULL are allowed.', activity_type;
    END CASE;
    
    -- For 'smoked' or 'sober' activities, remove conflicting activities on the same calendar day
    DELETE FROM groups.group_activity
    WHERE group_id = current_group_id
      AND user_id = current_user_id
      AND activity IN ('smoked', 'sober')
      AND ((timestamp AT TIME ZONE timezone)::date = activity_date);
    
    -- Insert the new activity
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    VALUES (current_group_id, current_user_id, mapped_activity_type, activity_timestamp);
    
END;
$$;


ALTER FUNCTION "groups"."add_checkin_activity"("activity_type" "text", "timezone" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."add_member"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$
DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id, joined_at)
    VALUES (current_user_id, add_member.group_id, NOW())
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (add_member.group_id, current_user_id, 'joined');
END;
$$;


ALTER FUNCTION "groups"."add_member"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."check_user_group"("user_id" "text", "group_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$
BEGIN
    -- Check if the user is in the specified group
    RETURN EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.user_id = check_user_group.user_id
        AND gm.group_id = check_user_group.group_id
    );
END;
$$;


ALTER FUNCTION "groups"."check_user_group"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."delete"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public', 'groups'
    AS $$
BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE group_notes.group_id = delete.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE group_activity.group_id = delete.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE group_members.group_id = delete.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.id = delete.group_id;
END;
$$;


ALTER FUNCTION "groups"."delete"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."get"() RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$DECLARE
    current_user_id text;
    current_group_id uuid;
BEGIN
    -- Get the current user's ID using public.get_user_id()
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Get the user's group using the groups.get_user_group() function
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;

    -- Return group details for the user's group
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'showInRank', u.show_in_group_rank,
                        'joinDate', gm.joined_at,
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        )
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'notes', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'fromMemberID', gn.from_member_id,
                        'message', gn.message,
                        'timestamp', gn.timestamp
                    )
                )
                FROM groups.group_notes gn
                WHERE gn.to_member_id = current_user_id
                AND gn.group_id = g.id
            ), '[]'::json),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'id', ga.id,
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', TO_CHAR(ga.timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"+0000"')
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json),
            'subscribed', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'subscribedTo', gs.subscribed_to
                    )
                ), '[]'::json)
                FROM groups.group_subscriptions gs
                WHERE gs.group_id = current_group_id
                AND gs.user_id = current_user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = current_group_id
    );
END;$$;


ALTER FUNCTION "groups"."get"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."get_user_group"() RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$
DECLARE
  v_user_id text;
  v_group_id uuid;
BEGIN
  -- Get the actual user ID using the public.get_user_id() function
  SELECT public.get_user_id() INTO v_user_id;
  
  -- If user not found, return null
  IF v_user_id IS NULL THEN
    RETURN NULL;
  END IF;
  
  -- Get any group the user is a member of - just getting the first one
  -- according to the natural order (likely insertion order)
  SELECT group_id INTO v_group_id
  FROM groups.group_members
  WHERE user_id = v_user_id
  LIMIT 1;
  
  RETURN v_group_id;
END;
$$;


ALTER FUNCTION "groups"."get_user_group"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "groups"."remove_member"("user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'groups'
    AS $$
DECLARE
    current_user_id text;
    current_group_id uuid;
BEGIN
    -- Get the current user's ID using public.get_user_id()
    SELECT public.get_user_id() INTO current_user_id;
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'You must be logged in to remove members';
    END IF;

    -- Get the user's group using the groups.get_user_group() function
    SELECT groups.get_user_group() INTO current_group_id;

    -- If no group found, raise an exception
    IF current_group_id IS NULL THEN
        RAISE EXCEPTION 'You are not a member of any group';
    END IF;

    -- Step 1: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = current_group_id
      AND (gn.to_member_id = remove_member.user_id OR gn.from_member_id = remove_member.user_id);

    -- Step 2: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = remove_member.user_id
      AND gs.group_id = current_group_id;

    -- Step 3: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = remove_member.user_id
      AND ga.group_id = current_group_id;

    -- Step 4: Remove the user from the group_members table (do this last)
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = remove_member.user_id
      AND gm.group_id = current_group_id;

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = current_group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM groups.delete(current_group_id);
    END IF;
END;
$$;


ALTER FUNCTION "groups"."remove_member"("user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."convert_day_info_to_legacy"("program_info" "jsonb") RETURNS TABLE("start_date" timestamp with time zone, "days_sober" boolean[])
    LANGUAGE "sql"
    SET "search_path" TO 'programs'
    AS $$WITH RECURSIVE 
parsed_json AS (
    SELECT 
        (program_info->>(i*2))::timestamp with time zone as date_key,
        program_info->(i*2 + 1)->>'sober' as sober_status
    FROM generate_series(0, jsonb_array_length(program_info)/2 - 1) as i
    WHERE i*2 < jsonb_array_length(program_info)
),
dates AS (
    SELECT 
        date_key,
        sober_status
    FROM parsed_json
    ORDER BY date_key
),
date_series AS (
    SELECT 
        generate_series(
            (min(date_key)::date + interval '1 day')::date,  -- Add 1 day to minimum date
            max(date_key)::date,
            '1 day'::interval
        )::date as series_date
    FROM dates
),
final_array AS (
    SELECT 
        array_agg(
            CASE 
                -- If no entry exists for this date (left join returned null), use null
                WHEN d.date_key IS NULL THEN NULL
                -- If entry exists but no sober status, use null
                WHEN d.sober_status IS NULL THEN NULL
                WHEN d.sober_status = 'true' THEN TRUE
                WHEN d.sober_status = 'false' THEN FALSE
                ELSE NULL
            END
            ORDER BY ds.series_date
        ) as days_sober,
        (SELECT min(date_key) FROM dates) as start_date  -- Changed this line to get actual first logged date
    FROM date_series ds
    LEFT JOIN dates d ON ds.series_date = d.date_key::date
)
SELECT 
    start_date::timestamp with time zone,
    days_sober
FROM final_array;$$;


ALTER FUNCTION "programs"."convert_day_info_to_legacy"("program_info" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."convert_legacy_to_day_info"("start_date" timestamp with time zone, "days_sober" boolean[]) RETURNS "jsonb"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'programs'
    AS $$BEGIN
    -- Return null if start_date is null
    IF start_date IS NULL THEN
        RETURN NULL;
    END IF;

    RETURN (
        WITH RECURSIVE
        date_array AS (
            -- Generate array of dates starting from start_date
            SELECT 
                i,
                (start_date + (i * interval '1 day'))::timestamp with time zone as date_value,
                -- Shift the days_sober array by 1 to start from the day after start_date
                CASE 
                    WHEN i = 0 THEN NULL  -- Start date has no sober information
                    ELSE days_sober[i]    -- Use i instead of i+1 to shift array access
                END as is_sober
            FROM generate_series(0, COALESCE(array_length(days_sober, 1), 0)) as i
        ),
        array_elements AS (
            SELECT 
                jsonb_build_array(
                    to_char(date_value::date, 'YYYY-MM-DD'),  -- Convert to date and format
                    CASE 
                        WHEN i = 0 OR is_sober IS NULL THEN
                            jsonb_build_object(
                                'customCheckIn', '[]'::jsonb,
                                'loggedSymptoms', '[]'::jsonb
                            )
                        ELSE
                            jsonb_build_object(
                                'sober', is_sober,
                                'customCheckIn', '[]'::jsonb,
                                'loggedSymptoms', '[]'::jsonb
                            )
                    END
                ) as element_pair
            FROM date_array
            WHERE i = 0 OR is_sober IS NOT NULL
        )
        SELECT 
            COALESCE(
                jsonb_agg(value),
                jsonb_build_array(
                    -- Fallback if somehow we get no results
                    to_char(start_date::date, 'YYYY-MM-DD'),  -- Convert to date and format
                    jsonb_build_object(
                        'customCheckIn', '[]'::jsonb,
                        'loggedSymptoms', '[]'::jsonb
                    )
                )
            )
        FROM (
            SELECT jsonb_array_elements(element_pair) as value
            FROM array_elements
        ) sub
    );
END;$$;


ALTER FUNCTION "programs"."convert_legacy_to_day_info"("start_date" timestamp with time zone, "days_sober" boolean[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "programs"."migrate_clear30_messages"() RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
DECLARE
    next_id bigint;
BEGIN
    -- Get the next available ID from the production table
    SELECT COALESCE(MAX(id), 0) + 1 INTO next_id 
    FROM programs.program_messages;

    -- Delete existing clear30 messages from production
    DELETE FROM programs.program_messages 
    WHERE program = 'clear30';

    -- Insert messages from QA to production with new IDs
    INSERT INTO programs.program_messages (
        id,  -- Now explicitly setting id
        day,
        title,
        subtitle,
        body,
        program,
        question_id,
        question_response,
        resources,
        claire_prompts,
        journal_prompts,
        meditation,
        page_info,
        stage,
        guide_id
    )
    SELECT 
        next_id + row_number() OVER (ORDER BY id) - 1,  -- Generate new sequential IDs
        day,
        title,
        subtitle,
        body,
        program,
        question_id,
        question_response,
        resources,
        claire_prompts,
        journal_prompts,
        meditation,
        page_info,
        stage,
        guide_id
    FROM programs.program_messages_qa
    WHERE program = 'clear30';

    -- Reset the sequence to the next available ID
    PERFORM setval(
        'programs.program_messages_id_seq', 
        (SELECT MAX(id) FROM programs.program_messages), 
        true
    );

EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Error migrating clear30 messages: %', SQLERRM;
END;
$$;


ALTER FUNCTION "programs"."migrate_clear30_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id)
    VALUES (user_id, group_id)
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (group_id, user_id, 'joined');
END;$$;


ALTER FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Check if both to_member_id and from_member_id are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.to_member_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_note.group_id
          AND gm.user_id = add_group_note.from_member_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_notes (group_id, to_member_id, from_member_id, message, timestamp)
        VALUES (add_group_note.group_id, add_group_note.to_member_id, add_group_note.from_member_id, add_group_note.message, NOW());

        -- Insert a new row into the group_activity table with the "note" activity
        INSERT INTO groups.group_activity (group_id, user_id, activity)
        VALUES (add_group_note.group_id, add_group_note.from_member_id, 'message');
    END IF;
END;$$;


ALTER FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.to_user_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = add_group_ping.group_id
          AND gm.user_id = add_group_ping.from_user_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_pings (group_id, from_user_id, to_user_id)
        VALUES (add_group_ping.group_id, add_group_ping.from_user_id, add_group_ping.to_user_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."admin_check"() RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  uid uuid;
  is_admin boolean;
begin
  -- Get the current user ID from the JWT using auth.uid()
  select auth.uid() into uid;
  
  if uid is null then
    return false;
  end if;

  select exists(select 1 from public.admins where auth_id = uid) into is_admin;
  return is_admin;
end;
$$;


ALTER FUNCTION "public"."admin_check"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."community_update_profile"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
  -- Check if a profile with the user id exists in community.profiles
  IF EXISTS (SELECT 1 FROM community.profiles WHERE id = NEW.id) THEN
    -- If profile exists, update the name and emoji
    UPDATE community.profiles
    SET 
      name = NEW.name,
      emoji = NEW.emoji
    WHERE id = NEW.id;
  END IF;
  
  RETURN NEW;
END;$$;


ALTER FUNCTION "public"."community_update_profile"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."create_user"("user_data" "json") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    _auth_id uuid;
    _phone_number text;
    _email text;
    _user_exists boolean;
    _new_logging_id text;
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

        -- UNBLOCK phone number if soft block
        IF _phone_number IS NOT NULL THEN
            DELETE FROM comms.sms_blocked
            WHERE phone_number = _phone_number AND hard_stop = false;
        END IF;

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
                    -- Add new logging_id to array if it's not already present
                    logging_id = CASE
                        WHEN _new_logging_id IS NOT NULL AND NOT (_new_logging_id = ANY(logging_id)) 
                        THEN array_append(COALESCE(logging_id, ARRAY[]::text[]), _new_logging_id)
                        ELSE logging_id
                    END,
                    phone_number = _phone_number,
                    email = _email
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
                    logging_id,
                    phone_number,
                    email
                )
                VALUES (
                    COALESCE(user_data->>'id', _auth_id::text),
                    _auth_id,
                    user_data->>'name',
                    user_data->>'emoji',
                    (user_data->'day_info')::jsonb,
                    user_data->>'fcm_token',
                    user_data->>'adjust_token',
                    CASE 
                        WHEN _new_logging_id IS NOT NULL 
                        THEN ARRAY[_new_logging_id]::text[] 
                        ELSE NULL 
                    END,
                    _phone_number,
                    _email
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
                logging_id,
                phone_number,
                email
            )
            VALUES (
                _auth_id::text,
                _auth_id,
                user_data->>'name',
                user_data->>'emoji',
                (user_data->'day_info')::jsonb,
                user_data->>'fcm_token',
                user_data->>'adjust_token',
                CASE 
                    WHEN _new_logging_id IS NOT NULL 
                    THEN ARRAY[_new_logging_id]::text[] 
                    ELSE NULL 
                END,
                _phone_number,
                _email
            );
        END IF;
    END IF;
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error creating/updating user: %', SQLERRM;
END;$$;


ALTER FUNCTION "public"."create_user"("user_data" "json") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."delete_group"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE groups.group_notes.group_id = delete_group.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE groups.group_activity.group_id = delete_group.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE groups.group_members.group_id = delete_group.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.groups.id = delete_group.group_id;

END;$$;


ALTER FUNCTION "public"."delete_group"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."dr_fred_get_messages"() RETURNS "text"[]
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_messages text[];
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Get messages and store them in array
    SELECT array_agg(text ORDER BY created_at)
    INTO v_messages
    FROM comms.dr_fred
    WHERE user_id = v_user_id
    AND outbound = true;  -- Only get messages from Dr. Fred (outbound = true)

    -- Return empty array if no messages found
    RETURN COALESCE(v_messages, ARRAY[]::text[]);
END;$$;


ALTER FUNCTION "public"."dr_fred_get_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."dr_fred_send_message"("message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
BEGIN
    -- Get the user_id from the users table based on the authenticated user's auth.uid()
    SELECT id INTO v_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- If no user found, raise an exception
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Insert the message
    INSERT INTO comms.dr_fred (
        user_id,
        text,
        outbound
    ) VALUES (
        v_user_id,
        message,
        false
    );
END;$$;


ALTER FUNCTION "public"."dr_fred_send_message"("message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_check_in_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_check_in_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_claire_prompt"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_claire_prompt"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_generic_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$ 
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
$$;


ALTER FUNCTION "public"."get_generic_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_get_back_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_get_back_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Step 1: Check if the user is part of the group
    IF NOT EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.group_id = get_group.group_id
          AND gm.user_id = get_group.user_id
    ) THEN
        -- Raise a custom exception with a custom SQLSTATE
        RAISE EXCEPTION 'User % is not part of the group %', get_group.user_id, get_group.group_id
            USING ERRCODE = 'P0001';
    END IF;

    -- Step 2: Return group details if the user is part of the group
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'daysSober', COALESCE(
                            (SELECT days_sober FROM programs.convert_day_info_to_legacy(u.day_info)),
                            ARRAY[]::boolean[]
                        ),
                        'initialFrequency', 0.5,
                        'showInRank', u.show_in_group_rank,
                        'startDate', (SELECT start_date FROM programs.convert_day_info_to_legacy(u.day_info)),
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        ),
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', date_trunc('second', gn.timestamp)
                                )
                            )
                            FROM groups.group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', date_trunc('second', ga.timestamp)  -- Truncate to seconds
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json),
            'subscribed', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'subscribedTo', gs.subscribed_to
                    )
                ), '[]'::json)
                FROM groups.group_subscriptions gs
                WHERE gs.group_id = get_group.group_id
                AND gs.user_id = get_group.user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = get_group.group_id
    );
END;$$;


ALTER FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[] DEFAULT ARRAY[]::"text"[]) RETURNS TABLE("anonymized_user_id" integer, "event_timestamp" timestamp with time zone, "event_name" "text", "event_extra_data" "json")
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$
BEGIN
    RETURN QUERY
    WITH user_mapping AS (
        SELECT
            user_id,
            ROW_NUMBER() OVER (ORDER BY user_id) AS unique_id
        FROM (
            SELECT DISTINCT user_id
            FROM public.events
            WHERE CASE 
                WHEN array_length(excluded_users, 1) > 0 THEN user_id NOT IN (SELECT unnest(excluded_users))
                ELSE true
            END
        ) AS distinct_users
    )
    SELECT
        um.unique_id::INTEGER AS anonymized_user_id,
        e.timestamp AS event_timestamp,
        e.event AS event_name,
        e.extra_data AS event_extra_data
    FROM public.events e
    JOIN user_mapping um ON e.user_id = um.user_id
    ORDER BY e.timestamp DESC;
END;
$$;


ALTER FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[] DEFAULT ARRAY[]::"text"[]) RETURNS integer
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$
DECLARE
    total_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total_count
    FROM public.events
    WHERE CASE 
        WHEN array_length(excluded_users, 1) > 0 THEN user_id NOT IN (SELECT unnest(excluded_users))
        ELSE true
    END;
    
    RETURN total_count;
END;
$$;


ALTER FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_data"("school_id" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $_$DECLARE
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
                       SELECT key
                       FROM jsonb_object_keys(m.meditation) key
                       LIMIT 1
                   ),
                   'meditation_link', (
                       SELECT m.meditation->key
                       FROM jsonb_object_keys(m.meditation) key
                       LIMIT 1
                   ),
                   'prompts', COALESCE(m.claire_prompts, '{}'::jsonb),
                   'journal_prompts', COALESCE(m.journal_prompts, '[]'::jsonb),
                   'resources', COALESCE(m.resources, '{}'::jsonb)
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
END;$_$;


ALTER FUNCTION "public"."get_school_data"("school_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_school_demo"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$ 
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
$$;


ALTER FUNCTION "public"."get_school_demo"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_slip_up_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
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
$$;


ALTER FUNCTION "public"."get_slip_up_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_symptom_infos"() RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    result TEXT := '{';  -- Initialize result as a JSON string starting with an opening brace
    first_entry BOOLEAN := TRUE;  -- To track whether it's the first entry to avoid trailing commas
    record RECORD;
    tip_record RECORD;  -- Declare tip_record as a RECORD
    section_record RECORD;  -- Declare section_record as a RECORD
    reddit_record RECORD;  -- Declare reddit_record as a RECORD
    prompt_record RECORD;  -- Declare prompt_record as a RECORD
    tips_array TEXT;
    sections_array TEXT;
    reddits TEXT;
    prompts TEXT;
    first_tip BOOLEAN;
    first_section BOOLEAN;
    first_reddit BOOLEAN;
    first_prompt BOOLEAN;
    cached_result JSONB;  -- Holds the cached JSON from the cache table
    cache_key CONSTANT TEXT := 'symptom_infos';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if the cache already has a value for "symptom_infos"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If the cached JSON is not NULL, return the cached result
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result or it's NULL, compute the new JSON result
    -- Loop through each symptom and gather the colors, tips, reddit threads, and prompts for that symptom
    FOR record IN 
        SELECT 
            s.emoji || ' ' || s.symptom AS symptom_key, 
            s.symptom, 
            s.color1, 
            s.color2,
            COALESCE(s.resources, '{}'::jsonb) as resources,
            COALESCE(s.claire_prompts, '{}'::jsonb) as claire_prompts
        FROM symptoms.symptoms s
        LEFT JOIN symptoms.symptom_tips st ON s.symptom = st.symptom
        GROUP BY s.emoji, s.symptom, st.color1, st.color2, s.resources, s.claire_prompts
        ORDER BY s.symptom
    LOOP
        -- Initialize tips array for the current symptom
        tips_array := '[';
        first_tip := TRUE;

        -- Get all tips for the current symptom by matching the symptom name
        FOR tip_record IN
            SELECT st.id, st.title, st.color1, st.color2
            FROM symptoms.symptom_tips st
            WHERE st.symptom = record.symptom
        LOOP
            -- Initialize sections array for the current tip
            sections_array := '[';
            first_section := TRUE;

            -- Get all sections for the current tip
            FOR section_record IN
                SELECT sts.heading, sts.content, sts.example
                FROM symptoms.symptom_tip_sections sts
                WHERE sts.symptom_tip = tip_record.id
            LOOP
                -- Handle commas between sections
                IF NOT first_section THEN
                    sections_array := sections_array || ', ';
                END IF;

                -- Add each section with heading, content, and possibly example, using to_json to escape text
                sections_array := sections_array || '{"heading": ' || to_json(section_record.heading) || ', "content": ' || to_json(section_record.content);

                -- Add example if it's not NULL
                IF section_record.example IS NOT NULL THEN
                    sections_array := sections_array || ', "example": ' || to_json(section_record.example);
                END IF;

                -- Close the section object
                sections_array := sections_array || '}';

                -- Mark that this is no longer the first section
                first_section := FALSE;
            END LOOP;

            -- Close the sections array
            sections_array := sections_array || ']';

            -- Handle commas between tips
            IF NOT first_tip THEN
                tips_array := tips_array || ', ';
            END IF;

            -- Add each tip to the tips array with title, colors, and sections, using to_json to escape text
            tips_array := tips_array || '{"title": ' || to_json(tip_record.title) || ', "color1": ' || to_json(tip_record.color1) || ', "color2": ' || to_json(tip_record.color2) || ', "sections": ' || sections_array || '}';

            -- Mark that this is no longer the first tip
            first_tip := FALSE;
        END LOOP;

        -- Close the tips array
        tips_array := tips_array || ']';

        -- Handle commas between symptom entries
        IF NOT first_entry THEN
            result := result || ', ';
        END IF;

        -- Add the symptom's colors, tips, reddits, and prompts to the result
        -- Note: Using the new columns directly as they're already in JSON format
        result := result || '"' || record.symptom_key || '": {"color1": ' || 
            to_json(record.color1) || ', "color2": ' || 
            to_json(record.color2) || ', "tips": ' || 
            tips_array || ', "reddits": ' || 
            record.resources::text || ', "prompts": ' || 
            record.claire_prompts::text || '}';

        -- Mark that this is no longer the first entry
        first_entry := FALSE;
    END LOOP;

    -- Close the JSON object with a closing brace
    result := result || '}';

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result::JSON, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSON
    RETURN result::JSON;
END;$$;


ALTER FUNCTION "public"."get_symptom_infos"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_symptom_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$DECLARE
    result JSONB;
    cached_result JSONB;
    cache_key CONSTANT TEXT := 'symptom_messages';  -- Key for this cache entry
BEGIN
    -- Step 1: Check if there's a cached result for "symptom_messages"
    SELECT json INTO cached_result
    FROM library.cache
    WHERE type = cache_key;

    -- Step 2: If cached result exists and is not NULL, return it
    IF cached_result IS NOT NULL THEN
        RETURN cached_result;
    END IF;

    -- Step 3: If no cached result, compute the result
    WITH aggregated_symptoms AS (
        SELECT s.emoji || ' ' || s.symptom AS symptom_key,
               jsonb_agg(sm.message) AS messages_array
        FROM symptoms.symptom_messages sm
        JOIN symptoms.symptoms s ON sm.symptom = s.symptom
        GROUP BY s.emoji, s.symptom
    )
    SELECT jsonb_object_agg(symptom_key, jsonb_build_object('messages', messages_array))
    INTO result
    FROM aggregated_symptoms;

    -- Step 4: Store the computed result in the cache table
    INSERT INTO library.cache (type, json, last_updated)
    VALUES (cache_key, result, NOW())
    ON CONFLICT (type) DO UPDATE
    SET json = EXCLUDED.json, last_updated = NOW();

    -- Step 5: Return the newly computed JSONB
    RETURN result;
END;$$;


ALTER FUNCTION "public"."get_symptom_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_user_id"() RETURNS "text"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  RETURN (SELECT id FROM users WHERE auth_id = auth.uid());
END;
$$;


ALTER FUNCTION "public"."get_user_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_mem"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Insert a new row into the group_members table
    INSERT INTO groups.group_members (user_id, group_id)
    VALUES (current_user_id, group_add_mem.group_id)
    ON CONFLICT DO NOTHING;  -- Avoid inserting duplicate records

    -- Insert a new row into the group_activity table with the "joined" activity
    INSERT INTO groups.group_activity (group_id, user_id, activity)
    VALUES (group_add_mem.group_id, current_user_id, 'joined');
END;$$;


ALTER FUNCTION "public"."group_add_mem"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Check if both to_member_id and from_member_id (current user) are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_note.group_id
          AND gm.user_id = to_member_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_note.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Insert the note into the group_notes table
        INSERT INTO groups.group_notes (
            group_id, 
            to_member_id, 
            from_member_id, 
            message, 
            timestamp
        )
        VALUES (
            group_id, 
            to_member_id, 
            current_user_id, 
            message, 
            NOW()
        );

        -- Insert a new row into the group_activity table with the "note" activity
        INSERT INTO groups.group_activity (
            group_id, 
            user_id, 
            activity
        )
        VALUES (
            group_id, 
            current_user_id, 
            'message'
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Check if both to_user_id and current user are in the group
    IF EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_ping.group_id
          AND gm.user_id = to_user_id
    )
    AND EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_add_ping.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Insert the ping into the group_pings table
        INSERT INTO groups.group_pings (
            group_id, 
            from_user_id, 
            to_user_id
        )
        VALUES (
            group_id, 
            current_user_id, 
            to_user_id
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_delete"("group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Delete all records from the group_notes table for the specified group
    DELETE FROM groups.group_notes
    WHERE groups.group_notes.group_id = group_delete.group_id;

    -- Delete all records from the group_activity table for the specified group
    DELETE FROM groups.group_activity
    WHERE groups.group_activity.group_id = group_delete.group_id;

    -- Delete all records from the group_members table for the specified group
    DELETE FROM groups.group_members
    WHERE groups.group_members.group_id = group_delete.group_id;

    -- Finally, delete the group itself from the groups table
    DELETE FROM groups.groups
    WHERE groups.groups.id = group_delete.group_id;

END;$$;


ALTER FUNCTION "public"."group_delete"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_get"("group_id" "uuid") RETURNS "json"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- Step 1: Check if the user is part of the group
    IF NOT EXISTS (
        SELECT 1
        FROM groups.group_members gm
        WHERE gm.group_id = group_get.group_id
          AND gm.user_id = current_user_id
    ) THEN
        -- Raise a custom exception with a custom SQLSTATE
        RAISE EXCEPTION 'User % is not part of the group %', current_user_id, group_get.group_id
            USING ERRCODE = 'P0001';
    END IF;

    -- Step 2: Return group details if the user is part of the group
    RETURN (
        SELECT json_build_object(
            'id', g.id,
            'name', g.name,
            'hue', g.hue,
            'members', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'memberID', u.id,
                        'name', u.name,
                        'emoji', u.emoji,
                        'daysSober', COALESCE(
                            (SELECT days_sober FROM programs.convert_day_info_to_legacy(u.day_info)),
                            ARRAY[]::boolean[]
                        ),
                        'initialFrequency', 0.5,
                        'showInRank', u.show_in_group_rank,
                        'startDate', TO_CHAR((SELECT start_date FROM programs.convert_day_info_to_legacy(u.day_info)) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"+0000"'),
                        '_dayInfo', (
                            SELECT jsonb_object_agg(
                                key,
                                value
                            )
                            FROM (
                                SELECT 
                                    (day_info->>(i*2)) as key,
                                    day_info->(i*2 + 1) as value
                                FROM generate_series(0, jsonb_array_length(u.day_info)/2 - 1) as i
                                WHERE i*2 < jsonb_array_length(u.day_info)
                            ) as pairs
                        ),
                        'notes', COALESCE((
                            SELECT json_agg(
                                json_build_object(
                                    'fromMemberID', gn.from_member_id,
                                    'message', gn.message,
                                    'timestamp', TO_CHAR(gn.timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"+0000"')
                                )
                            )
                            FROM groups.group_notes gn
                            WHERE gn.to_member_id = u.id
                            AND gn.group_id = g.id
                        ), '[]'::json)
                    )
                ), '[]'::json)
                FROM groups.group_members gm
                JOIN users u ON gm.user_id = u.id
                WHERE gm.group_id = g.id
            ),
            'activity', COALESCE((
                SELECT json_agg(
                    json_build_object(
                        'memberID', ga.user_id,
                        'type', ga.activity,
                        'timestamp', TO_CHAR(ga.timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"+0000"')
                    )
                )
                FROM groups.group_activity ga
                WHERE ga.group_id = g.id
            ), '[]'::json),
            'subscribed', (
                SELECT COALESCE(json_agg(
                    json_build_object(
                        'subscribedTo', gs.subscribed_to
                    )
                ), '[]'::json)
                FROM groups.group_subscriptions gs
                WHERE gs.group_id = group_get.group_id
                AND gs.user_id = current_user_id
            )
        )
        FROM groups.groups g
        WHERE g.id = group_get.group_id
    );
END;$$;


ALTER FUNCTION "public"."group_get"("group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the current user's ID from their auth ID
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();

    -- Check if the current user is in the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_rem_mem.group_id
        AND gm.user_id = current_user_id
    ) THEN
        RAISE EXCEPTION 'You must be a member of the group to remove members';
    END IF;

    -- Step 1: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = group_rem_mem.user_id
      AND ga.group_id = group_rem_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = group_rem_mem.user_id
      AND gm.group_id = group_rem_mem.group_id;

    -- Step 3: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = group_rem_mem.user_id
      AND gs.group_id = group_rem_mem.group_id;

    -- Step 4: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = group_rem_mem.group_id
      AND (gn.to_member_id = group_rem_mem.user_id OR gn.from_member_id = group_rem_mem.user_id);

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = group_rem_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM group_delete(group_rem_mem.group_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    WITH json_activities AS (
        -- Step 1: Unnest the JSONB array into a table format for easy comparison
        SELECT 
            -- Associate all activities with the current user
            current_user_id AS user_id,
            -- Map string values to the corresponding enum type
            CASE
                WHEN activity->>'type' = 'smoked' THEN 'smoked'::group_activity_type
                WHEN activity->>'type' = 'sober' THEN 'sober'::group_activity_type
                WHEN activity->>'type' = 'joined' THEN 'joined'::group_activity_type
                WHEN activity->>'type' = 'message' THEN 'message'::group_activity_type
                ELSE NULL
            END AS activity_type,
            (activity->>'timestamp')::timestamptz AS activity_timestamp
        FROM jsonb_array_elements(activity_data) AS activity
    ),
    -- Step 2: Delete activities for the current user that are in the table but not in the provided JSON
    delete_activities AS (
        DELETE FROM groups.group_activity ga
        WHERE ga.group_id = group_update_activity.group_id
          AND ga.user_id = current_user_id
          AND NOT EXISTS (
              SELECT 1 FROM json_activities ja
              WHERE ja.user_id = ga.user_id
                AND ja.activity_type = ga.activity
                AND ja.activity_timestamp = ga.timestamp
          )
        RETURNING *
    )
    -- Step 3: Insert activities from JSON if they don't already exist in the table
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    SELECT group_update_activity.group_id, ja.user_id, ja.activity_type, ja.activity_timestamp
    FROM json_activities ja
    WHERE ja.activity_type IS NOT NULL  -- Only insert valid activity types
      AND NOT EXISTS (
        SELECT 1 FROM groups.group_activity ga
        WHERE ga.group_id = group_update_activity.group_id
          AND ga.user_id = current_user_id
          AND ga.activity = ja.activity_type
          AND ga.timestamp = ja.activity_timestamp
    );
END;$$;


ALTER FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- 1. Check if the user is in the group
    IF NOT EXISTS (
        SELECT 1 
        FROM groups.group_members
        WHERE group_members.group_id = group_update_subscriptions.group_id
        AND group_members.user_id = current_user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of the group';
    END IF;

    -- 2. Remove all existing rows in group_subscriptions for the group_id and user_id
    DELETE FROM groups.group_subscriptions
    WHERE group_subscriptions.group_id = group_update_subscriptions.group_id
    AND group_subscriptions.user_id = current_user_id;

    -- 3. Insert new subscriptions into group_subscriptions
    INSERT INTO groups.group_subscriptions (group_id, user_id, subscribed_to)
    SELECT 
        group_update_subscriptions.group_id, 
        current_user_id, 
        value::TEXT
    FROM jsonb_array_elements_text(group_update_subscriptions.subscriptions) AS value;

END;$$;


ALTER FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."group_upsert"("group_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    current_user_id text;
BEGIN
    -- Get the user_id from the users table using the auth_id
    SELECT id INTO current_user_id
    FROM public.users
    WHERE auth_id = auth.uid();
    
    -- If no user_id found, raise an exception
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'No user found for the current auth_id';
    END IF;

    -- If an 'id' is provided, check if the group exists and the user is part of the group
    IF group_data ? 'id' THEN
        -- Check if the group exists
        IF EXISTS (SELECT 1 FROM groups.groups WHERE id = (group_data->>'id')::uuid) THEN
            -- Check if the user is part of the group
            IF NOT EXISTS (
                SELECT 1
                FROM groups.group_members gm
                WHERE gm.group_id = (group_data->>'id')::uuid
                  AND gm.user_id = current_user_id
            ) THEN
                -- Raise an exception if the user is not part of the group
                RAISE EXCEPTION 'User % is not a member of the group %', current_user_id, group_data->>'id';
            END IF;
        END IF;

        -- Insert or update the group with the provided 'id'
        INSERT INTO groups.groups (
            id, 
            name, 
            hue
        )
        VALUES (
            (group_data->>'id')::uuid,    -- Use the provided 'id'
            group_data->>'name',          -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        )
        ON CONFLICT (id)
        DO UPDATE
        SET name = COALESCE(group_data->>'name', groups.name),
            hue = COALESCE((group_data->>'hue')::numeric, groups.hue);

    ELSE
        -- Insert without 'id', letting Supabase generate the UUID
        INSERT INTO groups.groups (
            name, 
            hue
        )
        VALUES (
            group_data->>'name',          -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."group_upsert"("group_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_code"("input_code" "text") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    is_valid boolean;
BEGIN
    -- Check if code exists (case insensitive) and increment uses if it does
    UPDATE payment.promo_codes
    SET uses = uses + 1
    WHERE LOWER(code) = LOWER(input_code)
    RETURNING true INTO is_valid;
    
    -- Return the result (null becomes false)
    RETURN COALESCE(is_valid, false);
END;
$$;


ALTER FUNCTION "public"."payment_check_code"("input_code" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_email"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
DECLARE
    user_email text;
    email_domain text;
    org_name text;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name if it does
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org INTO org_name;
    
    -- Return the org name (will be null if domain not found)
    RETURN org_name;
END;
$$;


ALTER FUNCTION "public"."payment_check_email"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_email_json"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    user_email text;
    email_domain text;
    org_name text;
    org_school_id text;
    result jsonb;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name and school_id
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org, school_id INTO org_name, org_school_id;
    
    -- Raise exception if no matching domain was found
    IF org_name IS NULL THEN
        RAISE EXCEPTION 'Domain % not found in allowlist', email_domain;
    END IF;
    
    -- Construct the JSON return object
    result := jsonb_build_object(
        'org', org_name,
        'school_id', org_school_id
    );
    
    RETURN result;
END;$$;


ALTER FUNCTION "public"."payment_check_email_json"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."payment_check_sale"() RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  active_sale_id bigint;
  sale_exists boolean;
  v_user_id text;
  has_participated boolean;
BEGIN
  -- Get the user's ID using the public.get_user_id function
  v_user_id := public.get_user_id();
  
  -- Throw an error if no user found
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Check if there's an active sale (current date between starts_on and ends_on)
  SELECT id INTO active_sale_id
  FROM payment.sale
  WHERE NOW() BETWEEN starts_on AND ends_on
  LIMIT 1;
  
  -- Determine if a sale exists
  sale_exists := active_sale_id IS NOT NULL;
  
  -- If a sale exists
  IF sale_exists THEN
    -- Increment the uses count
    UPDATE payment.sale
    SET uses = uses + 1
    WHERE id = active_sale_id;
    
    -- Add a record to sale_users table (if it doesn't exist already)
    INSERT INTO payment.sale_users (user_id, sale_id)
    VALUES (v_user_id, active_sale_id)
    ON CONFLICT (user_id, sale_id) DO NOTHING;
    
    RETURN TRUE;
  ELSE
    -- No active sale, check if user has participated in any sale before
    SELECT EXISTS (
      SELECT 1 
      FROM payment.sale_users 
      WHERE user_id = v_user_id
    ) INTO has_participated;
    
    -- Return true if user has participated in a sale before
    RETURN has_participated;
  END IF;
END;$$;


ALTER FUNCTION "public"."payment_check_sale"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."popin_request_clear"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_user_id text;
BEGIN
  -- Get the user ID from the authenticated user
  SELECT id INTO v_user_id
  FROM public.users
  WHERE auth_id = auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Update all unprocessed pop-in notifications for this user
  UPDATE comms.silent_notifications
  SET processed = true
  WHERE 
    user_id = v_user_id
    AND processed = false
    AND metadata->>'type' = 'popInRequest';
  RETURN;
END;$$;


ALTER FUNCTION "public"."popin_request_clear"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_user_id text;
BEGIN
  -- Get the user ID from the authenticated user
  SELECT id INTO v_user_id
  FROM public.users
  WHERE auth_id = auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Insert the notification
  INSERT INTO comms.silent_notifications
    (
      user_id, 
      scheduled_for, 
      metadata, 
      processed
    )
  VALUES
    (
      v_user_id, 
      popin_request_schedule.scheduled_for, 
      '{"type": "popInRequest"}'::jsonb, 
      false
    );
    
  RETURN;
END;$$;


ALTER FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_feedback"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    recent_assessment RECORD;
    feedback JSONB := '[]';
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Step 2: Fetch feedback for the associated program, applying filters and ordering
    SELECT jsonb_agg(
        jsonb_build_object(
            'order', af.order,
            'title', af.title,
            'body', REPLACE(af.body, '_CLIENTNAME_', _user_name),
            'links', COALESCE(
                (
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'title', key,
                            'url', value
                        )
                    )
                    FROM jsonb_each_text(af.links)
                ),
                '[]'::jsonb
            )
        ) ORDER BY af.order
    )
    INTO feedback
    FROM programs.program_feedback af
    WHERE af.program = recent_assessment.program
    AND (
        af.question_id IS NULL
        OR (
            af.question_id IS NOT NULL
            AND (
                -- String response match
                jsonb_typeof(recent_assessment.responses -> af.question_id) = 'string'
                AND af.question_response = recent_assessment.responses ->> af.question_id
            )
            OR (
                -- Array response match
                jsonb_typeof(recent_assessment.responses -> af.question_id) = 'array'
                AND af.question_response = ANY (
                    SELECT jsonb_array_elements_text(recent_assessment.responses -> af.question_id)
                )
            )
        )
    );

    RETURN COALESCE(feedback, '[]'::JSONB);
END;$$;


ALTER FUNCTION "public"."program_get_feedback"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_latest_update"() RETURNS timestamp with time zone
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$begin
  -- Check if user is authenticated
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  -- Return the most recent updated_at date
  return (
    select max(updated_at)
    from programs.programs
  );
end;$$;


ALTER FUNCTION "public"."program_get_latest_update"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_messages"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
    _start_soon_program_id text;
    start_soon_messages JSONB;
    start_soon_stage_ids TEXT[];
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Check if there's a start_soon program associated with this program
    SELECT start_soon INTO _start_soon_program_id
    FROM programs.programs
    WHERE id = recent_assessment.program;

    -- Get messages with assessment message limitation
    WITH base_message_data AS (
        SELECT 
            pm.id,
            pm.day,
            pm.question_id,
            pm.question_response,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_title), '\n', '', 'g') as notification_title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_body), '\n', '', 'g') as notification_body,
            pm.thumbnail_url,
            pm.video_url,
            CASE 
                WHEN pm.resources IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'url', value
                    )) FROM jsonb_each_text(pm.resources))
                ELSE NULL
            END as resources,
            CASE 
                WHEN pm.meditation IS NOT NULL THEN
                    jsonb_build_object(
                        'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                        'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                    )
                ELSE NULL
            END as meditation,
            CASE 
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            pm.journal_prompts,
            CASE 
                WHEN pg.id IS NOT NULL THEN
                    (
                        SELECT jsonb_agg(jsonb_build_object('title', title, 'body', content))
                        FROM (
                            SELECT section_1_title as title, section_1_content as content FROM (SELECT pg.*) s WHERE section_1_title IS NOT NULL AND section_1_content IS NOT NULL
                            UNION ALL
                            SELECT section_2_title, section_2_content FROM (SELECT pg.*) s WHERE section_2_title IS NOT NULL AND section_2_content IS NOT NULL
                            UNION ALL
                            SELECT section_3_title, section_3_content FROM (SELECT pg.*) s WHERE section_3_title IS NOT NULL AND section_3_content IS NOT NULL
                            UNION ALL
                            SELECT section_4_title, section_4_content FROM (SELECT pg.*) s WHERE section_4_title IS NOT NULL AND section_4_content IS NOT NULL
                            UNION ALL
                            SELECT section_5_title, section_5_content FROM (SELECT pg.*) s WHERE section_5_title IS NOT NULL AND section_5_content IS NOT NULL
                            UNION ALL
                            SELECT section_6_title, section_6_content FROM (SELECT pg.*) s WHERE section_6_title IS NOT NULL AND section_6_content IS NOT NULL
                            UNION ALL
                            SELECT section_7_title, section_7_content FROM (SELECT pg.*) s WHERE section_7_title IS NOT NULL AND section_7_content IS NOT NULL
                            UNION ALL
                            SELECT section_8_title, section_8_content FROM (SELECT pg.*) s WHERE section_8_title IS NOT NULL AND section_8_content IS NOT NULL
                            UNION ALL
                            SELECT section_9_title, section_9_content FROM (SELECT pg.*) s WHERE section_9_title IS NOT NULL AND section_9_content IS NOT NULL
                        ) sections
                    )
                ELSE NULL
            END as page_info
        FROM programs.program_messages pm
        LEFT JOIN programs.program_guides pg ON pm.guide_id = pg.id
        WHERE pm.program = recent_assessment.program
        AND (
            pm.question_id IS NULL
            OR (
                pm.question_id IS NOT NULL
                AND (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                    AND pm.question_response = recent_assessment.responses ->> pm.question_id
                )
                OR (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                    AND pm.question_response = ANY (
                        SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                    )
                )
            )
        )
    ),
    message_objects AS (
        -- Convert base data into JSON objects
        SELECT 
            id,
            day,
            stage,
            question_id IS NOT NULL as is_assessment,
            jsonb_build_object(
                'id', id,
                'question_id', question_id,
                'question_response', question_response,
                'day', day,
                'title', title,
                'subtitle', subtitle,
                'body', body,
                'stage', stage,
                'resources', resources,
                'meditation', meditation,
                'claire_prompts', claire_prompts,
                'journal_prompts', journal_prompts,
                'page_info', page_info,
                'notification_title', notification_title,
                'notification_body', notification_body,
                'thumbnail_url', thumbnail_url,
                'video_url', video_url
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
        -- Group messages by day and type, keeping track of position within assessment messages
        SELECT 
            day,
            is_assessment,
            message_object,
            CASE 
                WHEN is_assessment THEN
                    row_number() OVER (PARTITION BY day, is_assessment ORDER BY id) - 1
                ELSE 0
            END as msg_position,
            CASE 
                WHEN is_assessment THEN
                    count(*) OVER (PARTITION BY day, is_assessment)
                ELSE 1
            END as group_size,
            -- Global counter for assessment messages across all days
            CASE 
                WHEN is_assessment THEN
                    dense_rank() OVER (ORDER BY day) - 1
                ELSE 0
            END as day_counter
        FROM message_objects
    )
    SELECT 
        (
            SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
            FROM day_groups
            WHERE NOT is_assessment  -- Include all core messages
               OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- If there's a start_soon program, get its non-assessment messages
    IF _start_soon_program_id IS NOT NULL THEN
        WITH start_soon_base_data AS (
            SELECT 
                pm.id,
                pm.day,
                pm.question_id,
                pm.question_response,
                REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
                REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
                TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
                pm.stage,
                REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_title), '\n', '', 'g') as notification_title,
                REGEXP_REPLACE(TRIM(TRAILING FROM pm.notification_body), '\n', '', 'g') as notification_body,
                pm.thumbnail_url,
                pm.video_url,
                CASE 
                    WHEN pm.resources IS NOT NULL THEN
                        (SELECT jsonb_agg(jsonb_build_object(
                            'title', key,
                            'url', value
                        )) FROM jsonb_each_text(pm.resources))
                    ELSE NULL
                END as resources,
                CASE 
                    WHEN pm.meditation IS NOT NULL THEN
                        jsonb_build_object(
                            'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                            'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                        )
                    ELSE NULL
                END as meditation,
                CASE 
                    WHEN pm.claire_prompts IS NOT NULL THEN
                        (SELECT jsonb_agg(jsonb_build_object(
                            'title', key,
                            'prompt', value
                        )) FROM jsonb_each_text(pm.claire_prompts))
                    ELSE NULL
                END as claire_prompts,
                pm.journal_prompts,
                CASE 
                    WHEN pg.id IS NOT NULL THEN
                        (
                            SELECT jsonb_agg(jsonb_build_object('title', title, 'body', content))
                            FROM (
                                SELECT section_1_title as title, section_1_content as content FROM (SELECT pg.*) s WHERE section_1_title IS NOT NULL AND section_1_content IS NOT NULL
                                UNION ALL
                                SELECT section_2_title, section_2_content FROM (SELECT pg.*) s WHERE section_2_title IS NOT NULL AND section_2_content IS NOT NULL
                                UNION ALL
                                SELECT section_3_title, section_3_content FROM (SELECT pg.*) s WHERE section_3_title IS NOT NULL AND section_3_content IS NOT NULL
                                UNION ALL
                                SELECT section_4_title, section_4_content FROM (SELECT pg.*) s WHERE section_4_title IS NOT NULL AND section_4_content IS NOT NULL
                                UNION ALL
                                SELECT section_5_title, section_5_content FROM (SELECT pg.*) s WHERE section_5_title IS NOT NULL AND section_5_content IS NOT NULL
                                UNION ALL
                                SELECT section_6_title, section_6_content FROM (SELECT pg.*) s WHERE section_6_title IS NOT NULL AND section_6_content IS NOT NULL
                                UNION ALL
                                SELECT section_7_title, section_7_content FROM (SELECT pg.*) s WHERE section_7_title IS NOT NULL AND section_7_content IS NOT NULL
                                UNION ALL
                                SELECT section_8_title, section_8_content FROM (SELECT pg.*) s WHERE section_8_title IS NOT NULL AND section_8_content IS NOT NULL
                                UNION ALL
                                SELECT section_9_title, section_9_content FROM (SELECT pg.*) s WHERE section_9_title IS NOT NULL AND section_9_content IS NOT NULL
                            ) sections
                        )
                    ELSE NULL
                END as page_info
            FROM programs.program_messages pm
            LEFT JOIN programs.program_guides pg ON pm.guide_id = pg.id
            WHERE pm.program = _start_soon_program_id
            AND (
                pm.question_id IS NULL
                OR (
                    pm.question_id IS NOT NULL
                    AND (
                        jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                        AND pm.question_response = recent_assessment.responses ->> pm.question_id
                    )
                    OR (
                        jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                        AND pm.question_response = ANY (
                            SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                        )
                    )
                )
            )
        ),
        start_soon_message_objects AS (
            SELECT 
                id,
                day,
                stage,
                question_id IS NOT NULL as is_assessment,
                jsonb_build_object(
                    'id', id,
                    'question_id', question_id,
                    'question_response', question_response,
                    'day', day,
                    'title', title,
                    'subtitle', subtitle,
                    'body', body,
                    'stage', stage,
                    'resources', resources,
                    'meditation', meditation,
                    'claire_prompts', claire_prompts,
                    'journal_prompts', journal_prompts,
                    'page_info', page_info,
                    'notification_title', notification_title,
                    'notification_body', notification_body,
                    'thumbnail_url', thumbnail_url,
                    'video_url', video_url
                ) as message_object
            FROM start_soon_base_data
        ),
        start_soon_day_groups AS (
            -- Group messages by day and type, keeping track of position within assessment messages
            SELECT 
                day,
                is_assessment,
                message_object,
                CASE 
                    WHEN is_assessment THEN
                        row_number() OVER (PARTITION BY day, is_assessment ORDER BY id) - 1
                    ELSE 0
                END as msg_position,
                CASE 
                    WHEN is_assessment THEN
                        count(*) OVER (PARTITION BY day, is_assessment)
                    ELSE 1
                END as group_size,
                -- Global counter for assessment messages across all days
                CASE 
                    WHEN is_assessment THEN
                        dense_rank() OVER (ORDER BY day) - 1
                    ELSE 0
                END as day_counter
            FROM start_soon_message_objects
        )
        SELECT 
            (
                SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
                FROM start_soon_day_groups
                WHERE NOT is_assessment  -- Include all core messages
                   OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
            ),
            array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
        INTO start_soon_messages, start_soon_stage_ids
        FROM start_soon_base_data;
        
        -- Combine stage IDs from both programs
        IF start_soon_stage_ids IS NOT NULL THEN
            stage_ids := array_cat(stage_ids, start_soon_stage_ids);
        END IF;
    END IF;

    -- Step 3: Fetch stage information for all referenced stages (from both programs)
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM title), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM subtitle), '\n', '', 'g'),
            'body', TRIM(TRAILING FROM REPLACE(body, '_CLIENTNAME_', _user_name)),
            'color1', color1,
            'color2', color2,
            'fred_experience', fred_experience
        )
    )
    INTO stages
    FROM programs.program_stages
    WHERE stage = ANY(stage_ids);

    -- Step 4: Return final combined structure with start_soon_messages if available
    IF _start_soon_program_id IS NOT NULL AND start_soon_messages IS NOT NULL THEN
        RETURN jsonb_build_object(
            'stages', COALESCE(stages, '[]'::jsonb),
            'messages', COALESCE(messages, '[]'::jsonb),
            'start_soon_messages', COALESCE(start_soon_messages, '[]'::jsonb)
        );
    ELSE
        RETURN jsonb_build_object(
            'stages', COALESCE(stages, '[]'::jsonb),
            'messages', COALESCE(messages, '[]'::jsonb)
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."program_get_messages"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_get_messages_qa"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    recent_assessment RECORD;
    messages JSONB;
    stages JSONB;
    stage_ids TEXT[];
    _auth_id uuid;
    _user_id text;
    _user_name text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Get user name
    SELECT name INTO _user_name
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Get user ID
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Step 1: Find the most recent assessment response for the user
    SELECT ar.assessment, pa.program, ar.responses
    INTO recent_assessment
    FROM programs.program_assessment_responses ar
    JOIN programs.program_assessments pa ON ar.assessment = pa.id
    WHERE ar.user_id = _user_id
    ORDER BY ar.timestamp DESC
    LIMIT 1;

    -- Ensure a recent assessment exists
    IF recent_assessment IS NULL THEN
        RAISE EXCEPTION 'No assessments found for authenticated user';
    END IF;

    -- Get messages with assessment message limitation
    WITH base_message_data AS (
        SELECT 
            pm.id,
            pm.day,
            pm.question_id,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.title), '\n', '', 'g') as title,
            REGEXP_REPLACE(TRIM(TRAILING FROM pm.subtitle), '\n', '', 'g') as subtitle,
            TRIM(TRAILING FROM REPLACE(pm.body, '_CLIENTNAME_', _user_name)) as body,
            pm.stage,
            CASE 
                WHEN pm.resources IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'url', value
                    )) FROM jsonb_each_text(pm.resources))
                ELSE NULL
            END as resources,
            CASE 
                WHEN pm.meditation IS NOT NULL THEN
                    jsonb_build_object(
                        'name', (SELECT key FROM jsonb_each_text(pm.meditation) LIMIT 1),
                        'url', (SELECT value FROM jsonb_each_text(pm.meditation) LIMIT 1)
                    )
                ELSE NULL
            END as meditation,
            CASE 
                WHEN pm.claire_prompts IS NOT NULL THEN
                    (SELECT jsonb_agg(jsonb_build_object(
                        'title', key,
                        'prompt', value
                    )) FROM jsonb_each_text(pm.claire_prompts))
                ELSE NULL
            END as claire_prompts,
            pm.journal_prompts,
            CASE 
                WHEN pg.id IS NOT NULL THEN
                    (
                        SELECT jsonb_agg(jsonb_build_object('title', title, 'body', content))
                        FROM (
                            SELECT section_1_title as title, section_1_content as content FROM (SELECT pg.*) s WHERE section_1_title IS NOT NULL AND section_1_content IS NOT NULL
                            UNION ALL
                            SELECT section_2_title, section_2_content FROM (SELECT pg.*) s WHERE section_2_title IS NOT NULL AND section_2_content IS NOT NULL
                            UNION ALL
                            SELECT section_3_title, section_3_content FROM (SELECT pg.*) s WHERE section_3_title IS NOT NULL AND section_3_content IS NOT NULL
                            UNION ALL
                            SELECT section_4_title, section_4_content FROM (SELECT pg.*) s WHERE section_4_title IS NOT NULL AND section_4_content IS NOT NULL
                            UNION ALL
                            SELECT section_5_title, section_5_content FROM (SELECT pg.*) s WHERE section_5_title IS NOT NULL AND section_5_content IS NOT NULL
                            UNION ALL
                            SELECT section_6_title, section_6_content FROM (SELECT pg.*) s WHERE section_6_title IS NOT NULL AND section_6_content IS NOT NULL
                            UNION ALL
                            SELECT section_7_title, section_7_content FROM (SELECT pg.*) s WHERE section_7_title IS NOT NULL AND section_7_content IS NOT NULL
                            UNION ALL
                            SELECT section_8_title, section_8_content FROM (SELECT pg.*) s WHERE section_8_title IS NOT NULL AND section_8_content IS NOT NULL
                            UNION ALL
                            SELECT section_9_title, section_9_content FROM (SELECT pg.*) s WHERE section_9_title IS NOT NULL AND section_9_content IS NOT NULL
                        ) sections
                    )
                ELSE NULL
            END as page_info
        FROM programs.program_messages_qa pm
        LEFT JOIN programs.program_guides_qa pg ON pm.guide_id = pg.id
        WHERE pm.program = recent_assessment.program
        AND (
            pm.question_id IS NULL
            OR (
                pm.question_id IS NOT NULL
                AND (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'string'
                    AND pm.question_response = recent_assessment.responses ->> pm.question_id
                )
                OR (
                    jsonb_typeof(recent_assessment.responses -> pm.question_id) = 'array'
                    AND pm.question_response = ANY (
                        SELECT jsonb_array_elements_text(recent_assessment.responses -> pm.question_id)
                    )
                )
            )
        )
    ),
    message_objects AS (
        -- Convert base data into JSON objects
        SELECT 
            id,
            day,
            stage,
            question_id IS NOT NULL as is_assessment,
            jsonb_build_object(
                'id', id,
                'day', day,
                'title', title,
                'subtitle', subtitle,
                'body', body,
                'stage', stage,
                'resources', resources,
                'meditation', meditation,
                'claire_prompts', claire_prompts,
                'journal_prompts', journal_prompts,
                'page_info', page_info
            ) as message_object
        FROM base_message_data
    ),
    day_groups AS (
        -- Group messages by day and type, keeping track of position within assessment messages
        SELECT 
            day,
            is_assessment,
            message_object,
            CASE 
                WHEN is_assessment THEN
                    row_number() OVER (PARTITION BY day, is_assessment ORDER BY id) - 1
                ELSE 0
            END as msg_position,
            CASE 
                WHEN is_assessment THEN
                    count(*) OVER (PARTITION BY day, is_assessment)
                ELSE 1
            END as group_size,
            -- Global counter for assessment messages across all days
            CASE 
                WHEN is_assessment THEN
                    dense_rank() OVER (ORDER BY day) - 1
                ELSE 0
            END as day_counter
        FROM message_objects
    )
    SELECT 
        (
            SELECT jsonb_agg(message_object ORDER BY (message_object->>'day')::bigint, (message_object->>'id')::bigint)
            FROM day_groups
            WHERE NOT is_assessment  -- Include all core messages
               OR (is_assessment AND msg_position = (day_counter % group_size))  -- Select one assessment message per day based on counter
        ),
        array_agg(DISTINCT stage) FILTER (WHERE stage IS NOT NULL)
    INTO messages, stage_ids
    FROM base_message_data;

    -- Step 3: Fetch stage information for all referenced stages
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', stage,
            'title', REGEXP_REPLACE(TRIM(TRAILING FROM title), '\n', '', 'g'),
            'subtitle', REGEXP_REPLACE(TRIM(TRAILING FROM subtitle), '\n', '', 'g'),
            'body', TRIM(TRAILING FROM REPLACE(body, '_CLIENTNAME_', _user_name)),
            'color1', color1,
            'color2', color2,
            'fred_experience', fred_experience
        )
    )
    INTO stages
    FROM programs.program_stages
    WHERE stage = ANY(stage_ids);

    -- Step 4: Return final combined structure
    RETURN jsonb_build_object(
        'stages', COALESCE(stages, '[]'::jsonb),
        'messages', COALESCE(messages, '[]'::jsonb)
    );
END;$$;


ALTER FUNCTION "public"."program_get_messages_qa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    valid_question_ids JSONB;
    filtered_responses JSONB := '{}';
    _auth_id uuid;
    _user_id text;
BEGIN
    -- Get the authenticated user's ID
    _auth_id := auth.uid();
    
    -- Check if user is authenticated
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User is not authenticated';
    END IF;

    -- Ensure the auth_id exists in users table
    IF NOT EXISTS (SELECT 1 FROM public.users WHERE auth_id = _auth_id) THEN
        RAISE EXCEPTION 'User record not found for authenticated user';
    END IF;

    -- Get user id
    SELECT id INTO _user_id
    FROM public.users
    WHERE auth_id = _auth_id;

    -- Ensure the assessment exists
    IF NOT EXISTS (SELECT 1 FROM programs.program_assessments WHERE id = assessment_id) THEN
        RAISE EXCEPTION 'Assessment with ID % does not exist', assessment_id;
    END IF;

    -- Get valid question IDs from the programs.program_assessments table
    SELECT question_ids
    INTO valid_question_ids
    FROM programs.program_assessments
    WHERE id = assessment_id;

    -- Filter the input JSON for valid question IDs
    SELECT jsonb_object_agg(key, value)
    INTO filtered_responses
    FROM jsonb_each(responses)
    WHERE key = ANY (SELECT jsonb_array_elements_text(valid_question_ids));

    -- Insert into the AssessmentResponses table
    INSERT INTO programs.program_assessment_responses (
        user_id, assessment, responses
    )
    VALUES (
        _user_id, assessment_id, filtered_responses
    );

END;$$;


ALTER FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- Step 1: Remove all activities related to the user and group
    DELETE FROM groups.group_activity ga
    WHERE ga.user_id = rem_group_mem.user_id
      AND ga.group_id = rem_group_mem.group_id;

    -- Step 2: Remove the user from the group_members table
    DELETE FROM groups.group_members gm
    WHERE gm.user_id = rem_group_mem.user_id
      AND gm.group_id = rem_group_mem.group_id;

    -- Step 3: Remove all subscriptions for the user and group
    DELETE FROM groups.group_subscriptions gs
    WHERE gs.user_id = rem_group_mem.user_id
      AND gs.group_id = rem_group_mem.group_id;

    -- Step 4: Remove all related group notes (sent or received by the user)
    DELETE FROM groups.group_notes gn
    WHERE gn.group_id = rem_group_mem.group_id
      AND (gn.to_member_id = rem_group_mem.user_id OR gn.from_member_id = rem_group_mem.user_id);

    -- Step 5: Check if the user was the last member of the group
    IF NOT EXISTS (
        SELECT 1 FROM groups.group_members gm
        WHERE gm.group_id = rem_group_mem.group_id
    ) THEN
        -- Call the delete_group function to delete the group if no members remain
        PERFORM group_delete(rem_group_mem.group_id);
    END IF;
END;$$;


ALTER FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_switchboard_number"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    v_request_id BIGINT;
    v_success BOOLEAN := FALSE;
    v_error_message TEXT;
    v_retry_count INTEGER := 0;
    v_max_retries INTEGER := 1;
    v_switchboard_url TEXT := 'https://aqhzgkdkjebmdqgbzkbu.supabase.co/functions/v1/sms_handle_service_clear30';
    v_jwt TEXT := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFxaHpna2RramVibWRxZ2J6a2J1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1NTkwMDMsImV4cCI6MjA1NzEzNTAwM30.ei7F4MlGxbUpPHmzxa4CWy6YZ-1JjF2hmPelBbDeaSk';
BEGIN
    -- Only proceed if the user has a phone number
    IF NEW.phone_number IS NULL OR NEW.phone_number = '' THEN
        RETURN NEW;
    END IF;

    -- Only proceed if the user has an auth id
    IF NEW.auth_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- For updates, only proceed if phone_number has changed
    IF TG_OP = 'UPDATE' THEN
        IF NEW.phone_number = OLD.phone_number THEN
            RETURN NEW;
        END IF;
    END IF;

    -- Try to call the switchboard function
    <<retry_loop>>
    WHILE v_retry_count <= v_max_retries LOOP
        BEGIN
            -- Make HTTP request using pg_net
            SELECT net.http_post(
                -- URL for the request
                url := v_switchboard_url,
                
                -- Body of the POST request (as jsonb)
                body := jsonb_build_object('phone_number', NEW.phone_number),
                
                -- No URL parameters
                params := '{}'::jsonb,
                
                -- Headers including Content-Type and Authorization
                headers := jsonb_build_object(
                    'Content-Type', 'application/json',
                    'Authorization', 'Bearer ' || v_jwt
                ),
                
                -- Timeout in milliseconds
                timeout_milliseconds := 5000
            )
            INTO v_request_id;

            -- If we got a request ID, consider it a success
            v_success := v_request_id IS NOT NULL;
            
            -- Exit the retry loop
            EXIT retry_loop;
            
        EXCEPTION WHEN OTHERS THEN
            -- Catch any other errors
            v_success := FALSE;
            v_error_message := SQLERRM;
            v_retry_count := v_retry_count + 1;
            
            -- Wait a moment before retrying (500ms)
            PERFORM pg_sleep(0.5);
        END;
    END LOOP retry_loop;

    -- Log the result
    INSERT INTO public.sms_verify_logs (
        auth_id,
        phone_number,
        success,
        error_message,
        retry_count
    ) VALUES (
        NEW.auth_id,  -- Using auth_id field from public.users
        NEW.phone_number,
        v_success,
        CASE 
            WHEN v_success THEN 'HTTP request initiated with request ID: ' || v_request_id
            ELSE v_error_message
        END,
        v_retry_count
    );

    -- Return the NEW record to allow the operation to proceed
    RETURN NEW;
END;$$;


ALTER FUNCTION "public"."set_switchboard_number"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_clear"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_auth_id uuid;
BEGIN
    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user id from the users table
    SELECT id 
    INTO v_user_id
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Update future messages to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = v_user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false);
END;$$;


ALTER FUNCTION "public"."sms_clear"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_clear"("message" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_auth_id uuid;
BEGIN
    -- Input validation
    IF message IS NULL OR trim(message) = '' THEN
        RAISE EXCEPTION 'Message pattern cannot be empty';
    END IF;

    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user id from the users table
    SELECT id 
    INTO v_user_id
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- Update future messages containing the pattern to be canceled
    UPDATE comms.sms_messages
    SET canceled = true
    WHERE user_id = v_user_id
    AND scheduled_for > now()
    AND sent_at IS NULL
    AND (canceled IS NULL OR canceled = false)
    AND text LIKE '%' || message || '%';

END;$$;


ALTER FUNCTION "public"."sms_clear"("message" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sms_schedule"("sms_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
    v_user_id text;
    v_phone_number text;
    v_message text;
    v_offset_minutes int;
    v_auth_id uuid;
BEGIN
    -- Get the message and offset from the JSONB input
    v_message := sms_data->>'message';
    v_offset_minutes := (sms_data->>'offset_minutes')::int;

    -- Input validation
    IF v_message IS NULL OR trim(v_message) = '' THEN
        RAISE EXCEPTION 'Message cannot be empty';
    END IF;

    IF v_offset_minutes IS NULL OR v_offset_minutes < 0 THEN
        RAISE EXCEPTION 'Offset minutes must be a positive number';
    END IF;

    -- Get the current user's auth ID
    v_auth_id := auth.uid();
    
    -- Look up the user details from the users table
    SELECT id, phone_number 
    INTO v_user_id, v_phone_number
    FROM public.users 
    WHERE auth_id = v_auth_id;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    IF v_phone_number IS NULL THEN
        RAISE EXCEPTION 'User has no associated phone number';
    END IF;

    -- Insert the SMS message
    INSERT INTO comms.sms_messages (
        user_id,
        phone_number,
        text,
        scheduled_for
    ) VALUES (
        v_user_id,
        v_phone_number,
        v_message,
        now() + (v_offset_minutes * interval '1 minute')
    );
END;$$;


ALTER FUNCTION "public"."sms_schedule"("sms_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") RETURNS "void"
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public'
    AS $$BEGIN
    INSERT INTO comms.feedback (user_id, feedback, timestamp)
    VALUES (user_id, feedback, CURRENT_TIMESTAMP);
END;$$;


ALTER FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    WITH json_activities AS (
        -- Step 1: Unnest the JSONB array into a table format for easy comparison
        SELECT 
            -- Associate all activities with the provided user_id
            update_group_activity.user_id AS user_id,
            -- Map string values to the corresponding enum type
            CASE
                WHEN activity->>'type' = 'smoked' THEN 'smoked'::group_activity_type
                WHEN activity->>'type' = 'sober' THEN 'sober'::group_activity_type
                WHEN activity->>'type' = 'joined' THEN 'joined'::group_activity_type
                WHEN activity->>'type' = 'message' THEN 'message'::group_activity_type
                ELSE NULL
            END AS activity_type,
            (activity->>'timestamp')::timestamptz AS activity_timestamp
        FROM jsonb_array_elements(activity_data) AS activity
    ),
    -- Step 2: Delete activities for the specified user that are in the table but not in the provided JSON
    delete_activities AS (
        DELETE FROM groups.group_activity ga
        WHERE ga.group_id = update_group_activity.group_id
          AND ga.user_id = update_group_activity.user_id
          AND NOT EXISTS (
              SELECT 1 FROM json_activities ja
              WHERE ja.user_id = ga.user_id
                AND ja.activity_type = ga.activity
                AND ja.activity_timestamp = ga.timestamp
          )
        RETURNING *
    )
    -- Step 3: Insert activities from JSON if they don't already exist in the table
    INSERT INTO groups.group_activity (group_id, user_id, activity, timestamp)
    SELECT update_group_activity.group_id, ja.user_id, ja.activity_type, ja.activity_timestamp
    FROM json_activities ja
    WHERE ja.activity_type IS NOT NULL  -- Only insert valid activity types
      AND NOT EXISTS (
        SELECT 1 FROM groups.group_activity ga
        WHERE ga.group_id = update_group_activity.group_id
          AND ga.user_id = update_group_activity.user_id
          AND ga.activity = ja.activity_type
          AND ga.timestamp = ja.activity_timestamp
    );
END;$$;


ALTER FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- 1. Check if the user is in the group
    IF NOT EXISTS (
        SELECT 1 
        FROM groups.group_members
        WHERE group_members.group_id = update_group_subscriptions.group_id
        AND group_members.user_id = update_group_subscriptions.user_id
    ) THEN
        RAISE EXCEPTION 'User is not a member of the group';
    END IF;

    -- 2. Remove all existing rows in group_subscriptions for the group_id and user_id
    DELETE FROM groups.group_subscriptions
    WHERE group_subscriptions.group_id = update_group_subscriptions.group_id
    AND group_subscriptions.user_id = update_group_subscriptions.user_id;

    -- 3. Insert new subscriptions into group_subscriptions
    INSERT INTO groups.group_subscriptions (group_id, user_id, subscribed_to)
    SELECT update_group_subscriptions.group_id, update_group_subscriptions.user_id, value::TEXT
    FROM jsonb_array_elements_text(update_group_subscriptions.subscriptions) AS value;

END;$$;


ALTER FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$BEGIN
    -- If an 'id' is provided, check if the group exists and the user is part of the group
    IF group_data ? 'id' THEN
        -- Check if the group exists
        IF EXISTS (SELECT 1 FROM groups.groups WHERE id = (group_data->>'id')::uuid) THEN
            -- Check if the user is part of the group
            IF NOT EXISTS (
                SELECT 1
                FROM groups.group_members gm
                WHERE gm.group_id = (group_data->>'id')::uuid
                  AND gm.user_id = upsert_group.user_id
            ) THEN
                -- Raise an exception if the user is not part of the group
                RAISE EXCEPTION 'User % is not a member of the group %', upsert_group.user_id, group_data->>'id';
            END IF;
        END IF;

        -- Insert or update the group with the provided 'id'
        INSERT INTO groups.groups (
            id, 
            name, 
            hue
        )
        VALUES (
            (group_data->>'id')::uuid, -- Use the provided 'id'
            group_data->>'name',        -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        )
        ON CONFLICT (id)
        DO UPDATE
        SET name = COALESCE(group_data->>'name', groups.name),
            hue = COALESCE((group_data->>'hue')::numeric, groups.hue);

    ELSE
        -- Insert without 'id', letting Supabase generate the UUID
        INSERT INTO groups.groups (
            name, 
            hue
        )
        VALUES (
            group_data->>'name', -- Name must be provided for creation
            (group_data->>'hue')::numeric -- Hue must be provided for creation
        );
    END IF;
END;$$;


ALTER FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."upsert_user"("user_data" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$DECLARE
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
END;$$;


ALTER FUNCTION "public"."upsert_user"("user_data" "jsonb") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "comms"."dr_fred" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "text" "text" NOT NULL,
    "outbound" boolean NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."dr_fred" OWNER TO "postgres";


ALTER TABLE "comms"."dr_fred" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."dr_fred_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."feedback" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "feedback" "text" NOT NULL,
    "timestamp" timestamp with time zone NOT NULL
);


ALTER TABLE "comms"."feedback" OWNER TO "postgres";


ALTER TABLE "comms"."feedback" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."feedback_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."notifications" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "metadata" "jsonb",
    "silent" boolean DEFAULT false
);


ALTER TABLE "comms"."notifications" OWNER TO "postgres";


ALTER TABLE "comms"."notifications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."notifications_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."silent_notifications" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "scheduled_for" timestamp with time zone NOT NULL,
    "metadata" "jsonb" NOT NULL,
    "processed" boolean DEFAULT false NOT NULL
);


ALTER TABLE "comms"."silent_notifications" OWNER TO "postgres";


ALTER TABLE "comms"."silent_notifications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."silent_notifications_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."sms_blocked" (
    "id" bigint NOT NULL,
    "phone_number" "text" NOT NULL,
    "hard_stop" boolean DEFAULT false NOT NULL,
    "blocked_on" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."sms_blocked" OWNER TO "postgres";


ALTER TABLE "comms"."sms_blocked" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."sms_blocked_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."sms_broadcasts" (
    "id" bigint NOT NULL,
    "message" "text" NOT NULL,
    "is_canceled" boolean DEFAULT false,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "batch_size" integer DEFAULT 100,
    "batch_interval" integer DEFAULT 2,
    "rest_period" integer DEFAULT 300
);


ALTER TABLE "comms"."sms_broadcasts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "comms"."sms_messages" (
    "id" bigint NOT NULL,
    "user_id" "text",
    "phone_number" "text" NOT NULL,
    "text" "text" NOT NULL,
    "outbound" boolean DEFAULT true NOT NULL,
    "scheduled_for" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "twilio_id" "text",
    "sent_at" timestamp with time zone,
    "canceled" boolean DEFAULT false NOT NULL,
    "broadcast_id" bigint
);


ALTER TABLE "comms"."sms_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "comms"."sms_statuses" (
    "id" bigint NOT NULL,
    "message_id" bigint NOT NULL,
    "status" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."sms_statuses" OWNER TO "postgres";


CREATE OR REPLACE VIEW "comms"."sms_broadcast_summary" WITH ("security_invoker"='on') AS
 WITH "message_stats" AS (
         SELECT "sms_messages"."broadcast_id",
            "count"(*) AS "total_messages",
            "sum"(
                CASE
                    WHEN ("sms_messages"."canceled" = true) THEN 1
                    ELSE 0
                END) AS "canceled_count",
            "sum"(
                CASE
                    WHEN (("sms_messages"."sent_at" IS NOT NULL) AND ("sms_messages"."canceled" = false)) THEN 1
                    ELSE 0
                END) AS "sent_count",
            "sum"(
                CASE
                    WHEN (("sms_messages"."sent_at" IS NULL) AND ("sms_messages"."canceled" = false)) THEN 1
                    ELSE 0
                END) AS "pending_count"
           FROM "comms"."sms_messages"
          WHERE ("sms_messages"."broadcast_id" IS NOT NULL)
          GROUP BY "sms_messages"."broadcast_id"
        ), "status_stats" AS (
         SELECT "m"."broadcast_id",
            "s"."status",
            "count"(*) AS "status_count"
           FROM ("comms"."sms_statuses" "s"
             JOIN "comms"."sms_messages" "m" ON (("s"."message_id" = "m"."id")))
          WHERE ("m"."broadcast_id" IS NOT NULL)
          GROUP BY "m"."broadcast_id", "s"."status"
        ), "status_pivot" AS (
         SELECT "status_stats"."broadcast_id",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'delivered'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "delivered_count",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'undelivered'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "undelivered_count",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'failed'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "failed_count",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'queued'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "queued_count",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'sent'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "sent_status_count",
            "sum"(
                CASE
                    WHEN ("status_stats"."status" = 'accepted'::"text") THEN "status_stats"."status_count"
                    ELSE (0)::bigint
                END) AS "accepted_count"
           FROM "status_stats"
          GROUP BY "status_stats"."broadcast_id"
        )
 SELECT "b"."id" AS "broadcast_id",
    "b"."message",
    "b"."created_at",
    "b"."is_canceled",
    COALESCE("ms"."total_messages", (0)::bigint) AS "total_messages",
    COALESCE("ms"."canceled_count", (0)::bigint) AS "canceled_count",
    COALESCE("ms"."sent_count", (0)::bigint) AS "sent_count",
    COALESCE("ms"."pending_count", (0)::bigint) AS "pending_count",
    COALESCE("sp"."delivered_count", (0)::numeric) AS "delivered_count",
    COALESCE("sp"."undelivered_count", (0)::numeric) AS "undelivered_count",
    COALESCE("sp"."failed_count", (0)::numeric) AS "failed_count",
    COALESCE("sp"."queued_count", (0)::numeric) AS "queued_count",
    COALESCE("sp"."sent_status_count", (0)::numeric) AS "sent_status_count",
    COALESCE("sp"."accepted_count", (0)::numeric) AS "accepted_count",
        CASE
            WHEN "b"."is_canceled" THEN 'Canceled'::"text"
            WHEN (("ms"."pending_count" = 0) AND ("ms"."total_messages" > 0)) THEN 'Completed'::"text"
            WHEN ("ms"."sent_count" > 0) THEN 'In Progress'::"text"
            ELSE 'Pending'::"text"
        END AS "status"
   FROM (("comms"."sms_broadcasts" "b"
     LEFT JOIN "message_stats" "ms" ON (("b"."id" = "ms"."broadcast_id")))
     LEFT JOIN "status_pivot" "sp" ON (("b"."id" = "sp"."broadcast_id")))
  ORDER BY "b"."created_at" DESC;


ALTER TABLE "comms"."sms_broadcast_summary" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "comms"."sms_broadcasts_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "comms"."sms_broadcasts_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "comms"."sms_broadcasts_id_seq" OWNED BY "comms"."sms_broadcasts"."id";



ALTER TABLE "comms"."sms_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."sms_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "comms"."sms_notify_team_summaries" (
    "id" integer NOT NULL,
    "summary" "text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "sent_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "comms"."sms_notify_team_summaries" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "comms"."sms_notify_team_summaries_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "comms"."sms_notify_team_summaries_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "comms"."sms_notify_team_summaries_id_seq" OWNED BY "comms"."sms_notify_team_summaries"."id";



ALTER TABLE "comms"."sms_statuses" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "comms"."sms_statuses_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "community"."activities" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "actor_id" "text" NOT NULL,
    "recipient_id" "text",
    "entity_type" "community"."activity_entity_type" NOT NULL,
    "entity_id" "uuid" NOT NULL,
    "action" "community"."activity_action" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "is_read" boolean DEFAULT false NOT NULL
);


ALTER TABLE "community"."activities" OWNER TO "postgres";


COMMENT ON TABLE "community"."activities" IS 'Stores activity events for posts and comments';



COMMENT ON COLUMN "community"."activities"."actor_id" IS 'User who performed the action';



COMMENT ON COLUMN "community"."activities"."recipient_id" IS 'User who should be notified of this activity (null for broadcast activities)';



COMMENT ON COLUMN "community"."activities"."entity_type" IS 'Type of entity this activity relates to (post or comment)';



COMMENT ON COLUMN "community"."activities"."entity_id" IS 'ID of the related entity (post_id or comment_id)';



COMMENT ON COLUMN "community"."activities"."action" IS 'Type of action (created, commented, replied)';



CREATE TABLE IF NOT EXISTS "community"."comments" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "post_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "body" "text",
    "parent_comment_id" "uuid"
);


ALTER TABLE "community"."comments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "community"."community_prompts" (
    "id" bigint NOT NULL,
    "start_date" "date" NOT NULL,
    "end_date" "date" NOT NULL,
    "prompt" "text" NOT NULL,
    "tag" "uuid",
    "title" "text" DEFAULT ''::"text" NOT NULL
);


ALTER TABLE "community"."community_prompts" OWNER TO "postgres";


ALTER TABLE "community"."community_prompts" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "community"."community_prompts_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "community"."deleted_posts_log" (
    "id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "deleted_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "deleted_by_user_id" "text",
    "deleted_by_admin" boolean DEFAULT false,
    "metadata" "jsonb"
);


ALTER TABLE "community"."deleted_posts_log" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "community"."post_tags" (
    "id" bigint NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "post_id" "uuid",
    "tag_id" "uuid",
    "updated_at" timestamp with time zone
);


ALTER TABLE "community"."post_tags" OWNER TO "postgres";


ALTER TABLE "community"."post_tags" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "community"."post_tags_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "community"."posts" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "text",
    "title" character varying,
    "content_type" "text",
    "body" "text",
    "video_url" "text",
    "view_count" bigint DEFAULT '0'::bigint,
    "is_pinned" boolean DEFAULT false,
    "is_hidden" boolean DEFAULT false,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone,
    "is_flagged_by_llm" boolean DEFAULT false,
    "thumbnail_url" "text"
);


ALTER TABLE "community"."posts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "community"."profiles" (
    "id" "text" NOT NULL,
    "name" "text" NOT NULL,
    "emoji" "text" NOT NULL,
    "is_ban" boolean DEFAULT false
);


ALTER TABLE "community"."profiles" OWNER TO "postgres";


COMMENT ON COLUMN "community"."profiles"."is_ban" IS 'Indicates whether the profile is banned (true) or not (false). Default is false.';



CREATE TABLE IF NOT EXISTS "community"."reactions" (
    "id" bigint NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "user_id" "text",
    "post_id" "uuid" NOT NULL,
    "emoji" character varying NOT NULL
);


ALTER TABLE "community"."reactions" OWNER TO "postgres";


ALTER TABLE "community"."reactions" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "community"."reactions_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "community"."reported_posts" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "user_id" "text",
    "post_id" "uuid"
);


ALTER TABLE "community"."reported_posts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "community"."settings" (
    "id" "text" NOT NULL,
    "value" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone
);


ALTER TABLE "community"."settings" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "community"."tags" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "name" character varying NOT NULL,
    "updated_at" timestamp with time zone,
    "type" "community"."tag_type" DEFAULT 'general'::"community"."tag_type" NOT NULL,
    "color" "text",
    CONSTRAINT "valid_hex_color" CHECK ((("color" IS NULL) OR ("color" ~ '^#[0-9A-Fa-f]{6}$'::"text")))
);


ALTER TABLE "community"."tags" OWNER TO "postgres";


COMMENT ON COLUMN "community"."tags"."color" IS 'Hex color code for the tag (e.g., #FF0000 for red)';



CREATE TABLE IF NOT EXISTS "events"."event_pop_ups" (
    "id" bigint NOT NULL,
    "event" "text" NOT NULL,
    "start_date" "date" NOT NULL,
    "end_date" "date" NOT NULL,
    "text" "text" NOT NULL,
    "user_ids" "text"[] DEFAULT '{}'::"text"[] NOT NULL
);


ALTER TABLE "events"."event_pop_ups" OWNER TO "postgres";


ALTER TABLE "events"."event_pop_ups" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "events"."event_pop_ups_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "events"."event_users" (
    "id" integer NOT NULL,
    "user_id" "text" NOT NULL,
    "event_id" "text" NOT NULL,
    "entered_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "events"."event_users" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "events"."event_users_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "events"."event_users_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "events"."event_users_id_seq" OWNED BY "events"."event_users"."id";



CREATE TABLE IF NOT EXISTS "events"."events" (
    "id" "text" NOT NULL,
    "name" "text" NOT NULL,
    "short_name" "text" NOT NULL,
    "num_waiting" bigint NOT NULL,
    "onboarding_start_date" "date" NOT NULL,
    "onboarding_end_date" "date" NOT NULL,
    "program_day_1" "date" NOT NULL,
    "app_unlock_date" "date" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "sms_id" "text" NOT NULL,
    "lobby_community_tag" "uuid" NOT NULL,
    "community_tag" "uuid",
    "end_date" "date" DEFAULT "now"() NOT NULL
);


ALTER TABLE "events"."events" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_activity" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "activity" "public"."group_activity_type" DEFAULT 'joined'::"public"."group_activity_type" NOT NULL
);


ALTER TABLE "groups"."group_activity" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_members" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "joined_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_members" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_messages" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "message" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "is_deleted" boolean DEFAULT false NOT NULL
);


ALTER TABLE "groups"."group_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_notes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "to_member_id" "text" NOT NULL,
    "from_member_id" "text" NOT NULL,
    "message" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_notes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_pings" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "from_user_id" "text" DEFAULT "now"() NOT NULL,
    "to_user_id" "text" NOT NULL,
    "group_id" "uuid" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_pings" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."group_subscriptions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "group_id" "uuid" NOT NULL,
    "user_id" "text" NOT NULL,
    "subscribed_to" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "groups"."group_subscriptions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "groups"."groups" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "hue" numeric NOT NULL
);


ALTER TABLE "groups"."groups" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."cache" (
    "type" "text" NOT NULL,
    "json" "json" NOT NULL,
    "last_updated" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "library"."cache" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."craving_resources" (
    "id" bigint NOT NULL,
    "type" "text" NOT NULL,
    "data" "jsonb" NOT NULL
);


ALTER TABLE "library"."craving_resources" OWNER TO "postgres";


ALTER TABLE "library"."craving_resources" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."craving_resources_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."general_copy" (
    "id" "text" NOT NULL,
    "copy" "text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "library"."general_copy" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."onboarding_reviews" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "priority" bigint DEFAULT '1'::bigint NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "author" "text" NOT NULL
);


ALTER TABLE "library"."onboarding_reviews" OWNER TO "postgres";


ALTER TABLE "library"."onboarding_reviews" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."onboarding_reviews_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."one_offs" (
    "key" "text" NOT NULL,
    "value" "text" NOT NULL
);


ALTER TABLE "library"."one_offs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."push_check_in" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text"
);


ALTER TABLE "library"."push_check_in" OWNER TO "postgres";


ALTER TABLE "library"."push_check_in" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."push_check_in_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_check_in" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_check_in" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."sms_day" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "hour" bigint,
    "min_offset" bigint,
    "program" "text" NOT NULL,
    "matches_program_time" boolean DEFAULT true,
    "question_id" "text",
    "question_response" "text",
    "text" "text" NOT NULL,
    "additional_id" "text",
    "paid" boolean
);


ALTER TABLE "library"."sms_day" OWNER TO "postgres";


ALTER TABLE "library"."sms_day" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_day_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_eow_summary" (
    "id" bigint NOT NULL,
    "program" "text" DEFAULT ''::"text",
    "question_id" "text",
    "question_response" "text",
    "check_in_days" bigint,
    "sober_days" bigint,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_eow_summary" OWNER TO "postgres";


ALTER TABLE "library"."sms_eow_summary" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_eow_summary_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_get_back" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_get_back" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "library"."sms_inactive" (
    "id" bigint NOT NULL,
    "day" "date",
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_inactive" OWNER TO "postgres";


ALTER TABLE "library"."sms_inactive" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "library"."sms_inactive_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "library"."sms_slip_up" (
    "day" bigint NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "library"."sms_slip_up" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."domain_allowlist" (
    "domain" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL,
    "org" "text" DEFAULT ''::"text" NOT NULL,
    "school_id" "text"
);


ALTER TABLE "payment"."domain_allowlist" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."hard_paywalls" (
    "id" bigint NOT NULL,
    "paywall_id" "text" NOT NULL,
    "hard" boolean NOT NULL
);


ALTER TABLE "payment"."hard_paywalls" OWNER TO "postgres";


ALTER TABLE "payment"."hard_paywalls" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "payment"."hard_paywalls_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "payment"."one_time_offers" (
    "id" bigint NOT NULL,
    "paywall_id" "text" NOT NULL,
    "oto_placement" "text" NOT NULL,
    "enabled" boolean DEFAULT true NOT NULL
);


ALTER TABLE "payment"."one_time_offers" OWNER TO "postgres";


ALTER TABLE "payment"."one_time_offers" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "payment"."one_time_offers_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "payment"."promo_codes" (
    "code" "text" NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL
);


ALTER TABLE "payment"."promo_codes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "payment"."sale" (
    "id" bigint NOT NULL,
    "starts_on" timestamp with time zone NOT NULL,
    "ends_on" timestamp with time zone NOT NULL,
    "uses" bigint DEFAULT '0'::bigint NOT NULL
);


ALTER TABLE "payment"."sale" OWNER TO "postgres";


ALTER TABLE "payment"."sale" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "payment"."sale_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "payment"."sale_users" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "sale_id" bigint NOT NULL,
    "created_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE "payment"."sale_users" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "payment"."sale_users_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "payment"."sale_users_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "payment"."sale_users_id_seq" OWNED BY "payment"."sale_users"."id";



CREATE TABLE IF NOT EXISTS "programs"."program_assessment_responses" (
    "id" bigint NOT NULL,
    "user_id" "text" DEFAULT ''::"text" NOT NULL,
    "assessment" "text" NOT NULL,
    "responses" "jsonb" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "programs"."program_assessment_responses" OWNER TO "postgres";


ALTER TABLE "programs"."program_assessment_responses" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_assessment_responses_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_assessments" (
    "id" "text" NOT NULL,
    "desc" "text" NOT NULL,
    "program" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "question_ids" "jsonb" NOT NULL
);


ALTER TABLE "programs"."program_assessments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_feedback" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "order" bigint,
    "links" "jsonb"
);


ALTER TABLE "programs"."program_feedback" OWNER TO "postgres";


ALTER TABLE "programs"."program_feedback" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_feedback_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_guides" (
    "id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "published_on" "date" NOT NULL,
    "thumbnail" "text" NOT NULL,
    "section_1_title" "text",
    "section_1_content" "text",
    "section_2_title" "text",
    "section_2_content" "text",
    "section_3_title" "text",
    "section_3_content" "text",
    "section_4_title" "text",
    "section_4_content" "text",
    "section_5_title" "text",
    "section_5_content" "text",
    "section_6_title" "text",
    "section_6_content" "text",
    "section_7_title" "text",
    "section_7_content" "text",
    "section_8_title" "text",
    "section_8_content" "text",
    "section_9_title" "text",
    "section_9_content" "text",
    "resources" "jsonb"
);


ALTER TABLE "programs"."program_guides" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_guides_qa" (
    "id" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "published_on" "date" NOT NULL,
    "thumbnail" "text" NOT NULL,
    "section_1_title" "text",
    "section_1_content" "text",
    "section_2_title" "text",
    "section_2_content" "text",
    "section_3_title" "text",
    "section_3_content" "text",
    "section_4_title" "text",
    "section_4_content" "text",
    "section_5_title" "text",
    "section_5_content" "text",
    "section_6_title" "text",
    "section_6_content" "text",
    "section_7_title" "text",
    "section_7_content" "text",
    "section_8_title" "text",
    "section_8_content" "text",
    "section_9_title" "text",
    "section_9_content" "text",
    "resources" "jsonb"
);


ALTER TABLE "programs"."program_guides_qa" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."program_messages" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb",
    "page_info" "jsonb",
    "stage" "text",
    "guide_id" "text",
    "notification_body" "text",
    "notification_title" "text",
    "thumbnail_url" "text",
    "video_url" "text"
);


ALTER TABLE "programs"."program_messages" OWNER TO "postgres";


ALTER TABLE "programs"."program_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_messages_qa" (
    "id" bigint NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "program" "text" NOT NULL,
    "question_id" "text",
    "question_response" "text",
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb",
    "page_info" "jsonb",
    "stage" "text",
    "guide_id" "text"
);


ALTER TABLE "programs"."program_messages_qa" OWNER TO "postgres";


ALTER TABLE "programs"."program_messages_qa" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "programs"."program_messages_qa_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "programs"."program_stages" (
    "stage" "text" NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "body" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "fred_experience" "text"
);


ALTER TABLE "programs"."program_stages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "programs"."programs" (
    "id" "text" NOT NULL,
    "descriptions" "text" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "start_soon" "text"
);


ALTER TABLE "programs"."programs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."admins" (
    "id" bigint NOT NULL,
    "auth_id" "uuid" NOT NULL
);


ALTER TABLE "public"."admins" OWNER TO "postgres";


ALTER TABLE "public"."admins" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."admins_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."amplitude_test" (
    "id" bigint NOT NULL,
    "text" "text" NOT NULL
);


ALTER TABLE "public"."amplitude_test" OWNER TO "postgres";


ALTER TABLE "public"."amplitude_test" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."amplitude_test_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."api_keys" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "key" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "timezone"('utc'::"text", "now"())
);


ALTER TABLE "public"."api_keys" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."events" (
    "id" bigint NOT NULL,
    "user_id" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "event" "text" NOT NULL,
    "extra_data" "json"
);


ALTER TABLE "public"."events" OWNER TO "postgres";


ALTER TABLE "public"."events" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."events_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."forms" (
    "id" bigint NOT NULL,
    "form_name" "text" NOT NULL,
    "response" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."forms" OWNER TO "postgres";


ALTER TABLE "public"."forms" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."forms_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."sms_verify_logs" (
    "id" integer NOT NULL,
    "auth_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "phone_number" "text" NOT NULL,
    "success" boolean NOT NULL,
    "error_message" "text",
    "retry_count" integer DEFAULT 0 NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."sms_verify_logs" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."sms_verify_logs_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "public"."sms_verify_logs_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."sms_verify_logs_id_seq" OWNED BY "public"."sms_verify_logs"."id";



CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" "text" NOT NULL,
    "name" "text" NOT NULL,
    "emoji" "text" NOT NULL,
    "days_sober" boolean[],
    "initial_frequency" numeric,
    "show_in_group_rank" boolean,
    "fcm_token" "text",
    "start_date" timestamp with time zone,
    "day_info" "jsonb",
    "auth_id" "uuid",
    "phone_number" "text",
    "adjust_token" "text",
    "email" "text",
    "sms_settings" "jsonb",
    "notification_settings" "jsonb",
    "logging_id" "text"[]
);


ALTER TABLE "public"."users" OWNER TO "postgres";


COMMENT ON TABLE "public"."users" IS 'Clear30 users';



CREATE TABLE IF NOT EXISTS "schools"."school_activities" (
    "school_id" "text" NOT NULL,
    "org_name" "text" NOT NULL,
    "title" "text" NOT NULL,
    "date_time" timestamp with time zone NOT NULL,
    "link" "text" NOT NULL,
    "subtitle" "text",
    "description" "text",
    "facilitated_by" "text",
    "location" "text",
    "phone_number" "text",
    "email" "text",
    "repeats" "public"."school_activity_repeat_rate",
    "sub_links" "json",
    "id" bigint NOT NULL
);


ALTER TABLE "schools"."school_activities" OWNER TO "postgres";


ALTER TABLE "schools"."school_activities" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_activities_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."school_messages" (
    "school_id" "text" NOT NULL,
    "day" bigint NOT NULL,
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "message" "text" NOT NULL,
    "id" bigint NOT NULL,
    "resources" "jsonb",
    "claire_prompts" "jsonb",
    "journal_prompts" "jsonb",
    "meditation" "jsonb"
);


ALTER TABLE "schools"."school_messages" OWNER TO "postgres";


ALTER TABLE "schools"."school_messages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_messages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."school_resources" (
    "title" "text" NOT NULL,
    "subtitle" "text" NOT NULL,
    "link" "text" NOT NULL,
    "description" "text",
    "badge" "text",
    "phone_number" "text",
    "location" "text",
    "section_title" "text" NOT NULL,
    "school_id" "text" NOT NULL,
    "id" bigint NOT NULL
);


ALTER TABLE "schools"."school_resources" OWNER TO "postgres";


ALTER TABLE "schools"."school_resources" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "schools"."school_resources_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "schools"."schools" (
    "school_id" "text" NOT NULL,
    "short_name" "text" NOT NULL,
    "long_name" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "reddit_flair" "text",
    "sf_symbol_icon" "text"
);


ALTER TABLE "schools"."schools" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "symptoms"."symptom_messages" (
    "symptom" "text" NOT NULL,
    "message" "text" NOT NULL
);


ALTER TABLE "symptoms"."symptom_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "symptoms"."symptom_tip_sections" (
    "id" bigint NOT NULL,
    "symptom_tip" bigint NOT NULL,
    "heading" "text" NOT NULL,
    "content" "text" NOT NULL,
    "example" "text"
);


ALTER TABLE "symptoms"."symptom_tip_sections" OWNER TO "postgres";


ALTER TABLE "symptoms"."symptom_tip_sections" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "symptoms"."symptom_tip_sections_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "symptoms"."symptom_tips" (
    "id" bigint NOT NULL,
    "title" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "symptom" "text" NOT NULL
);


ALTER TABLE "symptoms"."symptom_tips" OWNER TO "postgres";


ALTER TABLE "symptoms"."symptom_tips" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "symptoms"."symptom_tips_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "symptoms"."symptoms" (
    "symptom" "text" NOT NULL,
    "color1" "text" NOT NULL,
    "color2" "text" NOT NULL,
    "emoji" "text" NOT NULL,
    "resources" "jsonb",
    "claire_prompts" "jsonb"
);


ALTER TABLE "symptoms"."symptoms" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."assessment_responses_clear30" WITH ("security_invoker"='on') AS
 SELECT "par"."user_id",
    "u"."name" AS "user_name",
    "par"."id" AS "response_id",
    ("par"."responses" -> 'LO-Age'::"text") AS "Age",
    ("par"."responses" -> 'Consumption-Method'::"text") AS "Consumption Method",
    ("par"."responses" -> 'Days-Using'::"text") AS "Days Using",
    ("par"."responses" -> 'Trigger'::"text") AS "Triggers",
    ("par"."responses" -> 'Help-Harm'::"text") AS "Help vs Harm",
    ("par"."responses" -> 'Previous-Break'::"text") AS "Previous Break",
    ("par"."responses" -> 'Break-Reason'::"text") AS "Break Reason",
    ("par"."responses" -> 'Goal30'::"text") AS "30 Day Goal",
    ("par"."responses" -> 'Commitment'::"text") AS "Commitment",
    ("par"."responses" -> 'Then-What'::"text") AS "Then What",
    "par"."timestamp",
    ("par"."responses" -> 'Money-Spent'::"text") AS "Money Spent",
    ("par"."responses" -> 'Start-Date'::"text") AS "Start Date"
   FROM ("programs"."program_assessment_responses" "par"
     JOIN "public"."users" "u" ON (("par"."user_id" = "u"."id")))
  WHERE ("par"."assessment" = 'clear30'::"text")
  ORDER BY "par"."timestamp" DESC;


ALTER TABLE "views"."assessment_responses_clear30" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."assessment_responses_life" AS
 SELECT "par"."user_id",
    "u"."name" AS "user_name",
    "par"."id" AS "response_id",
    ("par"."responses" -> 'Days-Using'::"text") AS "Days Using",
    ("par"."responses" -> 'Used-Less'::"text") AS "Used Less",
    ("par"."responses" -> 'Participation-Reason'::"text") AS "Participation Reason",
    ("par"."responses" -> 'Helpful'::"text") AS "Helpful",
    ("par"."responses" -> 'Not-Helpful'::"text") AS "Not Helpful",
    ("par"."responses" -> 'Positive-Results'::"text") AS "Positive Results",
    ("par"."responses" -> 'Mental-Clarity'::"text") AS "Mental Clarity",
    ("par"."responses" -> 'Self-Growth'::"text") AS "Self Growth",
    ("par"."responses" -> 'Relationship'::"text") AS "Relationship",
    ("par"."responses" -> 'Identity'::"text") AS "Identity",
    ("par"."responses" -> 'Worth-It'::"text") AS "Worth It",
    ("par"."responses" -> 'Mental-Health'::"text") AS "Mental Health",
    ("par"."responses" -> 'LO-Use-State'::"text") AS "LO-Use-State",
    ("par"."responses" -> 'Moderation-Tech'::"text") AS "Moderation Tech",
    ("par"."responses" -> 'Comments'::"text") AS "Comments",
    ("par"."responses" -> 'Gain_Mental_Clarity-Met'::"text") AS "Gain Mental Clarity Met",
    ("par"."responses" -> 'Reduce_Anxiety-Met'::"text") AS "Reduce Anxiety Met",
    ("par"."responses" -> 'Reduce_Depression-Met'::"text") AS "Reduce Depression Met",
    ("par"."responses" -> 'Reduce_Being_Stuck_in_Own_Head-Met'::"text") AS "Reduce Being Stuck in Own Head Met",
    ("par"."responses" -> 'Improve_Sleep_Quality-Met'::"text") AS "Improve Sleep Quality Met",
    ("par"."responses" -> 'Improve_Self-Control_and_Intention-Met'::"text") AS "Improve Self-Control and Intention Met",
    ("par"."responses" -> 'Reduce_Dependency_on_Cannabis-Met'::"text") AS "Reduce Dependency on Cannabis Met",
    ("par"."responses" -> 'Explore_Life_Without_Cannabis-Met'::"text") AS "Explore Life Without Cannabis Met",
    ("par"."responses" -> 'Lower_Tolerance-Met'::"text") AS "Lower Tolerance Met",
    ("par"."responses" -> 'Improve_Overall_Health-Met'::"text") AS "Improve Overall Health Met",
    ("par"."responses" -> 'Improve_Lung_Health-Met'::"text") AS "Improve Lung Health Met",
    ("par"."responses" -> 'Increase_Productivity-Met'::"text") AS "Increase Productivity Met",
    ("par"."responses" -> 'Increase_Motivation-Met'::"text") AS "Increase Motivation Met",
    ("par"."responses" -> 'Save_Money-Met'::"text") AS "Save Money Met",
    ("par"."responses" -> 'Improve_Current_Relationships-Met'::"text") AS "Improve Current Relationships Met",
    ("par"."responses" -> 'Enhance_Social_Connections-Met'::"text") AS "Enhance Social Connections Met",
    ("par"."responses" -> 'Pass_Work-Required_Drug_Test-Met'::"text") AS "Pass Work-Required Drug Test Met",
    ("par"."responses" -> 'Meet_Legal_Obligations-Met'::"text") AS "Meet Legal Obligations Met",
    ("par"."responses" -> 'Other-Met'::"text") AS "Other Met",
    "par"."timestamp"
   FROM ("programs"."program_assessment_responses" "par"
     JOIN "public"."users" "u" ON (("par"."user_id" = "u"."id")))
  WHERE ("par"."assessment" = 'life'::"text")
  ORDER BY "par"."timestamp" DESC;


ALTER TABLE "views"."assessment_responses_life" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."claire_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."name",
    ("last_message"."extra_data" ->> 'claire_message'::"text") AS "text",
    "last_message"."timestamp"
   FROM (( SELECT DISTINCT ON ("events"."user_id") "events"."user_id",
            "events"."id",
            "events"."timestamp",
            "events"."event",
            "events"."extra_data"
           FROM "public"."events"
          WHERE (("events"."event" = 'used_claire'::"text") AND ("events"."user_id" IS NOT NULL))
          ORDER BY "events"."user_id", "events"."timestamp" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."timestamp" DESC;


ALTER TABLE "views"."claire_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."dr_fred_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."name",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM (( SELECT DISTINCT ON ("dr_fred"."user_id") "dr_fred"."user_id",
            "dr_fred"."id",
            "dr_fred"."text",
            "dr_fred"."created_at",
            "dr_fred"."outbound"
           FROM "comms"."dr_fred"
          ORDER BY "dr_fred"."user_id", "dr_fred"."created_at" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."dr_fred_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."groups_summary" AS
 SELECT "g"."id" AS "group_id",
    "g"."name" AS "group_name",
    ( SELECT "json_agg"(DISTINCT "u"."name") AS "json_agg"
           FROM ("groups"."group_members" "gm"
             LEFT JOIN "public"."users" "u" ON (("gm"."user_id" = "u"."id")))
          WHERE ("gm"."group_id" = "g"."id")) AS "members_in_group",
    "json_agg"("ga"."activity") AS "activities_in_group",
    "max"("ga"."timestamp") AS "most_recent_activity_timestamp"
   FROM ("groups"."groups" "g"
     LEFT JOIN "groups"."group_activity" "ga" ON (("g"."id" = "ga"."group_id")))
  GROUP BY "g"."id", "g"."name"
  ORDER BY "g"."id";


ALTER TABLE "views"."groups_summary" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."latest_user_events" AS
 SELECT "events"."user_id",
    "max"("events"."timestamp") AS "latest_activity"
   FROM "public"."events"
  GROUP BY "events"."user_id";


ALTER TABLE "views"."latest_user_events" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."inactive_users_31_days" AS
 WITH "all_users" AS (
         SELECT "users"."id",
            "users"."logging_id"
           FROM "public"."users"
        ), "active_user_ids" AS (
         SELECT DISTINCT "latest_user_events"."user_id"
           FROM "views"."latest_user_events"
          WHERE ("latest_user_events"."latest_activity" >= (CURRENT_DATE - '31 days'::interval))
        )
 SELECT "u"."id" AS "user_id"
   FROM "all_users" "u"
  WHERE ((NOT ("u"."id" IN ( SELECT "active_user_ids"."user_id"
           FROM "active_user_ids"))) AND (NOT (EXISTS ( SELECT 1
           FROM "unnest"("u"."logging_id") "logging_id"("logging_id")
          WHERE ("logging_id"."logging_id" IN ( SELECT "active_user_ids"."user_id"
                   FROM "active_user_ids"))))));


ALTER TABLE "views"."inactive_users_31_days" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."inbound_sms_users" AS
 SELECT DISTINCT COALESCE("sm"."user_id", "u"."id") AS "user_id",
    "u"."name" AS "user_name",
    "sm"."phone_number",
    "count"("sm"."id") AS "message_count",
    "max"("sm"."created_at") AS "last_message_date"
   FROM ("comms"."sms_messages" "sm"
     LEFT JOIN "public"."users" "u" ON ((("sm"."user_id" = "u"."id") OR ("sm"."phone_number" = "u"."phone_number"))))
  WHERE ("sm"."outbound" = false)
  GROUP BY COALESCE("sm"."user_id", "u"."id"), "u"."name", "sm"."phone_number"
  ORDER BY ("max"("sm"."created_at")) DESC;


ALTER TABLE "views"."inbound_sms_users" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."sms_non_user_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."phone_number",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM ( SELECT DISTINCT ON ("sms_messages"."phone_number") "sms_messages"."user_id",
            "sms_messages"."phone_number",
            "sms_messages"."id",
            "sms_messages"."text",
            "sms_messages"."created_at",
            "sms_messages"."outbound"
           FROM "comms"."sms_messages"
          WHERE (("sms_messages"."canceled" = false) AND ("sms_messages"."user_id" IS NULL) AND ("sms_messages"."scheduled_for" <= "now"()))
          ORDER BY "sms_messages"."phone_number", "sms_messages"."created_at" DESC) "last_message"
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."sms_non_user_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."sms_user_conversations" WITH ("security_invoker"='true') AS
 SELECT "last_message"."id",
    "last_message"."user_id",
    "u"."phone_number",
    "u"."name",
    "last_message"."text",
    "last_message"."created_at" AS "timestamp",
    "last_message"."outbound"
   FROM (( SELECT DISTINCT ON ("sms_messages"."user_id") "sms_messages"."user_id",
            "sms_messages"."id",
            "sms_messages"."text",
            "sms_messages"."created_at",
            "sms_messages"."outbound"
           FROM "comms"."sms_messages"
          WHERE (("sms_messages"."canceled" = false) AND ("sms_messages"."user_id" IS NOT NULL) AND ("sms_messages"."scheduled_for" <= "now"()))
          ORDER BY "sms_messages"."user_id", "sms_messages"."created_at" DESC) "last_message"
     LEFT JOIN "public"."users" "u" ON (("last_message"."user_id" = "u"."id")))
  ORDER BY "last_message"."outbound", "last_message"."created_at" DESC;


ALTER TABLE "views"."sms_user_conversations" OWNER TO "postgres";


CREATE OR REPLACE VIEW "views"."thatchers_fuck_up" AS
 SELECT DISTINCT "sm"."phone_number"
   FROM ("comms"."sms_statuses" "ss"
     JOIN "comms"."sms_messages" "sm" ON (("ss"."message_id" = "sm"."id")))
  WHERE (("ss"."status" = 'delivered'::"text") AND (("ss"."id" >= 85628) AND ("ss"."id" <= 88989)));


ALTER TABLE "views"."thatchers_fuck_up" OWNER TO "postgres";


ALTER TABLE ONLY "comms"."sms_broadcasts" ALTER COLUMN "id" SET DEFAULT "nextval"('"comms"."sms_broadcasts_id_seq"'::"regclass");



ALTER TABLE ONLY "comms"."sms_notify_team_summaries" ALTER COLUMN "id" SET DEFAULT "nextval"('"comms"."sms_notify_team_summaries_id_seq"'::"regclass");



ALTER TABLE ONLY "events"."event_users" ALTER COLUMN "id" SET DEFAULT "nextval"('"events"."event_users_id_seq"'::"regclass");



ALTER TABLE ONLY "payment"."sale_users" ALTER COLUMN "id" SET DEFAULT "nextval"('"payment"."sale_users_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."sms_verify_logs" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."sms_verify_logs_id_seq"'::"regclass");



ALTER TABLE ONLY "comms"."dr_fred"
    ADD CONSTRAINT "dr_fred_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."feedback"
    ADD CONSTRAINT "feedback_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."silent_notifications"
    ADD CONSTRAINT "silent_notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_blocked"
    ADD CONSTRAINT "sms_blocked_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_broadcasts"
    ADD CONSTRAINT "sms_broadcasts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_messages"
    ADD CONSTRAINT "sms_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_notify_team_summaries"
    ADD CONSTRAINT "sms_notify_team_summaries_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "comms"."sms_statuses"
    ADD CONSTRAINT "sms_statuses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."activities"
    ADD CONSTRAINT "activities_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."comments"
    ADD CONSTRAINT "comments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."community_prompts"
    ADD CONSTRAINT "community_prompts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."deleted_posts_log"
    ADD CONSTRAINT "deleted_posts_log_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."post_tags"
    ADD CONSTRAINT "post_tags_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."posts"
    ADD CONSTRAINT "posts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."profiles"
    ADD CONSTRAINT "profiles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."reactions"
    ADD CONSTRAINT "reactions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."reported_posts"
    ADD CONSTRAINT "reported_posts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."settings"
    ADD CONSTRAINT "settings_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "community"."tags"
    ADD CONSTRAINT "tags_name_key" UNIQUE ("name");



ALTER TABLE ONLY "community"."tags"
    ADD CONSTRAINT "tags_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "events"."event_pop_ups"
    ADD CONSTRAINT "event_pop_ups_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "events"."event_users"
    ADD CONSTRAINT "event_users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "events"."event_users"
    ADD CONSTRAINT "event_users_user_id_event_id_key" UNIQUE ("user_id", "event_id");



ALTER TABLE ONLY "events"."events"
    ADD CONSTRAINT "events_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_messages"
    ADD CONSTRAINT "group_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_unique_constraint" UNIQUE ("group_id", "from_member_id", "to_member_id", "timestamp");



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."groups"
    ADD CONSTRAINT "groups_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "unique_group_activity" UNIQUE ("group_id", "user_id", "timestamp");



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "unique_group_user" UNIQUE ("group_id", "user_id");



ALTER TABLE ONLY "library"."cache"
    ADD CONSTRAINT "cache_pkey" PRIMARY KEY ("type");



ALTER TABLE ONLY "library"."sms_check_in"
    ADD CONSTRAINT "check_in_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "library"."craving_resources"
    ADD CONSTRAINT "cravings_resources_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."general_copy"
    ADD CONSTRAINT "general_copy_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_get_back"
    ADD CONSTRAINT "get_back_messages_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "library"."onboarding_reviews"
    ADD CONSTRAINT "onboarding_reviews_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."one_offs"
    ADD CONSTRAINT "one_offs_pkey" PRIMARY KEY ("key");



ALTER TABLE ONLY "library"."push_check_in"
    ADD CONSTRAINT "push_check_in_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_day"
    ADD CONSTRAINT "sms_day_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_eow_summary"
    ADD CONSTRAINT "sms_eow_summary_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_inactive"
    ADD CONSTRAINT "sms_inactive_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "library"."sms_slip_up"
    ADD CONSTRAINT "sms_slip_up_pkey" PRIMARY KEY ("day", "message");



ALTER TABLE ONLY "payment"."domain_allowlist"
    ADD CONSTRAINT "domain_allowlist_pkey" PRIMARY KEY ("domain");



ALTER TABLE ONLY "payment"."hard_paywalls"
    ADD CONSTRAINT "hard_paywalls_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "payment"."one_time_offers"
    ADD CONSTRAINT "one_time_offers_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "payment"."promo_codes"
    ADD CONSTRAINT "promo_codes_pkey" PRIMARY KEY ("code");



ALTER TABLE ONLY "payment"."sale"
    ADD CONSTRAINT "sale_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "payment"."sale_users"
    ADD CONSTRAINT "sale_users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "payment"."sale_users"
    ADD CONSTRAINT "sale_users_user_id_sale_id_key" UNIQUE ("user_id", "sale_id");



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_assessments"
    ADD CONSTRAINT "program_assessments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_feedback"
    ADD CONSTRAINT "program_feedback_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_guides"
    ADD CONSTRAINT "program_guides_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_guides_qa"
    ADD CONSTRAINT "program_guides_qa_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "programs"."program_stages"
    ADD CONSTRAINT "program_stages_pkey" PRIMARY KEY ("stage");



ALTER TABLE ONLY "programs"."programs"
    ADD CONSTRAINT "programs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."admins"
    ADD CONSTRAINT "admins_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."amplitude_test"
    ADD CONSTRAINT "amplitude_test_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."api_keys"
    ADD CONSTRAINT "api_keys_key_key" UNIQUE ("key");



ALTER TABLE ONLY "public"."api_keys"
    ADD CONSTRAINT "api_keys_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."events"
    ADD CONSTRAINT "events_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."forms"
    ADD CONSTRAINT "forms_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."sms_verify_logs"
    ADD CONSTRAINT "sms_verify_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_key" UNIQUE ("auth_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_activities"
    ADD CONSTRAINT "school_activities_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_messages"
    ADD CONSTRAINT "school_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."school_resources"
    ADD CONSTRAINT "school_resources_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "schools"."schools"
    ADD CONSTRAINT "schools_pkey" PRIMARY KEY ("school_id");



ALTER TABLE ONLY "symptoms"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_pkey" PRIMARY KEY ("symptom", "message");



ALTER TABLE ONLY "symptoms"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "symptoms"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "symptoms"."symptoms"
    ADD CONSTRAINT "symptoms_pkey" PRIMARY KEY ("symptom");



CREATE INDEX "idx_sms_messages_broadcast_id" ON "comms"."sms_messages" USING "btree" ("broadcast_id");



CREATE INDEX "idx_sms_notify_team_summaries_sent_at" ON "comms"."sms_notify_team_summaries" USING "btree" ("sent_at");



CREATE INDEX "idx_sms_notify_team_summaries_updated_at" ON "comms"."sms_notify_team_summaries" USING "btree" ("updated_at");



CREATE INDEX "idx_activities_actor" ON "community"."activities" USING "btree" ("actor_id");



CREATE INDEX "idx_activities_created_at" ON "community"."activities" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_activities_entity" ON "community"."activities" USING "btree" ("entity_type", "entity_id");



CREATE INDEX "idx_activities_recipient_unread" ON "community"."activities" USING "btree" ("recipient_id") WHERE (NOT "is_read");



CREATE INDEX "idx_comments_parent_comment_id" ON "community"."comments" USING "btree" ("parent_comment_id");



CREATE INDEX "idx_posts_user_id" ON "community"."posts" USING "btree" ("user_id");



CREATE INDEX "tags_type_idx" ON "community"."tags" USING "btree" ("type");



CREATE INDEX "idx_sale_users_sale_id" ON "payment"."sale_users" USING "btree" ("sale_id");



CREATE INDEX "idx_sale_users_user_id" ON "payment"."sale_users" USING "btree" ("user_id");



CREATE OR REPLACE TRIGGER "dr_fred_auto_respond_trigger" AFTER INSERT ON "comms"."dr_fred" FOR EACH ROW EXECUTE FUNCTION "comms"."dr_fred_auto_respond"();



CREATE OR REPLACE TRIGGER "dr_fred_notify_team" AFTER INSERT ON "comms"."dr_fred" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/dr_fred_notify_team', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "notification_send_trigger" AFTER INSERT ON "comms"."notifications" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/notification_send', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "schedule_dr_fred_message_notification" AFTER INSERT ON "comms"."dr_fred" FOR EACH ROW EXECUTE FUNCTION "comms"."add_dr_fred_message_notification"();



CREATE OR REPLACE TRIGGER "after_activity_created" AFTER INSERT ON "community"."activities" FOR EACH ROW EXECUTE FUNCTION "community"."create_notification_from_activity"();



CREATE OR REPLACE TRIGGER "after_comment_created_activity" AFTER INSERT ON "community"."comments" FOR EACH ROW EXECUTE FUNCTION "community"."create_comment_activity"();



CREATE OR REPLACE TRIGGER "after_comment_created_profile" AFTER INSERT ON "community"."comments" FOR EACH ROW EXECUTE FUNCTION "community"."create_profile"();



CREATE OR REPLACE TRIGGER "after_post_created_activity" AFTER INSERT ON "community"."posts" FOR EACH ROW EXECUTE FUNCTION "community"."create_post_activity"();



CREATE OR REPLACE TRIGGER "after_post_created_profile" AFTER INSERT ON "community"."posts" FOR EACH ROW EXECUTE FUNCTION "community"."create_profile"();



CREATE OR REPLACE TRIGGER "community_report_notify_team" AFTER INSERT ON "community"."reported_posts" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/community_report_notify_team', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "reported_posts_trigger" AFTER INSERT ON "community"."reported_posts" FOR EACH ROW EXECUTE FUNCTION "community"."set_post_visibility"();



CREATE OR REPLACE TRIGGER "trigger_check_event_sale" AFTER INSERT ON "events"."event_users" FOR EACH ROW EXECUTE FUNCTION "events"."check_event_sale"();



CREATE OR REPLACE TRIGGER "schedule_group_message_notification" AFTER INSERT ON "groups"."group_messages" FOR EACH ROW EXECUTE FUNCTION "comms"."add_group_message_notification"();



CREATE OR REPLACE TRIGGER "schedule_group_note_notification" AFTER INSERT ON "groups"."group_notes" FOR EACH ROW EXECUTE FUNCTION "comms"."add_group_note_notification"();



CREATE OR REPLACE TRIGGER "schedule_group_ping_notification" AFTER INSERT ON "groups"."group_pings" FOR EACH ROW EXECUTE FUNCTION "comms"."add_group_ping_notification"();



CREATE OR REPLACE TRIGGER "amplitude_forward_assessment_response" AFTER INSERT ON "programs"."program_assessment_responses" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_assessment_response', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "after_user_insert" AFTER INSERT ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."set_switchboard_number"();



CREATE OR REPLACE TRIGGER "after_user_phone_update" AFTER UPDATE ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."set_switchboard_number"();



CREATE OR REPLACE TRIGGER "after_user_update" AFTER UPDATE ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."community_update_profile"();



CREATE OR REPLACE TRIGGER "amplitude_forward_event" AFTER INSERT ON "public"."events" FOR EACH ROW EXECUTE FUNCTION "supabase_functions"."http_request"('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_event', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');



CREATE OR REPLACE TRIGGER "trigger_handle_event_users" AFTER INSERT ON "public"."events" FOR EACH ROW EXECUTE FUNCTION "events"."handle_event_users"();



ALTER TABLE ONLY "comms"."dr_fred"
    ADD CONSTRAINT "dr_fred_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "comms"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "comms"."silent_notifications"
    ADD CONSTRAINT "silent_notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "comms"."sms_messages"
    ADD CONSTRAINT "sms_messages_broadcast_id_fkey" FOREIGN KEY ("broadcast_id") REFERENCES "comms"."sms_broadcasts"("id");



ALTER TABLE ONLY "comms"."sms_messages"
    ADD CONSTRAINT "sms_messages_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "comms"."sms_statuses"
    ADD CONSTRAINT "sms_statuses_message_id_fkey" FOREIGN KEY ("message_id") REFERENCES "comms"."sms_messages"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "community"."activities"
    ADD CONSTRAINT "activities_actor_id_fkey" FOREIGN KEY ("actor_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "community"."activities"
    ADD CONSTRAINT "activities_recipient_id_fkey" FOREIGN KEY ("recipient_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "community"."comments"
    ADD CONSTRAINT "comments_parent_comment_id_fkey" FOREIGN KEY ("parent_comment_id") REFERENCES "community"."comments"("id");



ALTER TABLE ONLY "community"."comments"
    ADD CONSTRAINT "comments_post_id_fkey" FOREIGN KEY ("post_id") REFERENCES "community"."posts"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "community"."comments"
    ADD CONSTRAINT "comments_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "community"."community_prompts"
    ADD CONSTRAINT "community_prompts_tag_fkey" FOREIGN KEY ("tag") REFERENCES "community"."tags"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "community"."deleted_posts_log"
    ADD CONSTRAINT "deleted_posts_log_deleted_by_user_id_fkey" FOREIGN KEY ("deleted_by_user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "community"."post_tags"
    ADD CONSTRAINT "post_tags_post_id_fkey" FOREIGN KEY ("post_id") REFERENCES "community"."posts"("id");



ALTER TABLE ONLY "community"."post_tags"
    ADD CONSTRAINT "post_tags_tag_id_fkey" FOREIGN KEY ("tag_id") REFERENCES "community"."tags"("id");



ALTER TABLE ONLY "community"."posts"
    ADD CONSTRAINT "posts_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "community"."profiles"
    ADD CONSTRAINT "profiles_id_fkey" FOREIGN KEY ("id") REFERENCES "public"."users"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "community"."reactions"
    ADD CONSTRAINT "reactions_post_id_fkey" FOREIGN KEY ("post_id") REFERENCES "community"."posts"("id");



ALTER TABLE ONLY "community"."reactions"
    ADD CONSTRAINT "reactions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "community"."reported_posts"
    ADD CONSTRAINT "reported_posts_post_id_fkey" FOREIGN KEY ("post_id") REFERENCES "community"."posts"("id");



ALTER TABLE ONLY "community"."reported_posts"
    ADD CONSTRAINT "reported_posts_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "events"."event_pop_ups"
    ADD CONSTRAINT "event_pop_ups_event_fkey" FOREIGN KEY ("event") REFERENCES "events"."events"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "events"."event_users"
    ADD CONSTRAINT "event_users_event_id_fkey" FOREIGN KEY ("event_id") REFERENCES "events"."events"("id");



ALTER TABLE ONLY "events"."event_users"
    ADD CONSTRAINT "event_users_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "events"."events"
    ADD CONSTRAINT "events_community_tag_fkey" FOREIGN KEY ("community_tag") REFERENCES "community"."tags"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "events"."events"
    ADD CONSTRAINT "events_lobby_community_tag_fkey" FOREIGN KEY ("lobby_community_tag") REFERENCES "community"."tags"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_activity"
    ADD CONSTRAINT "group_activity_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_members"
    ADD CONSTRAINT "group_members_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_messages"
    ADD CONSTRAINT "group_messages_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_messages"
    ADD CONSTRAINT "group_messages_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_from_member_id_fkey" FOREIGN KEY ("from_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_notes"
    ADD CONSTRAINT "group_notes_to_member_id_fkey" FOREIGN KEY ("to_member_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_from_user_id_fkey" FOREIGN KEY ("from_user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_pings"
    ADD CONSTRAINT "group_pings_to_user_id_fkey" FOREIGN KEY ("to_user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_group_id_fkey1" FOREIGN KEY ("group_id") REFERENCES "groups"."groups"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_subscribed_to_fkey" FOREIGN KEY ("subscribed_to") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "groups"."group_subscriptions"
    ADD CONSTRAINT "group_subscriptions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "library"."sms_day"
    ADD CONSTRAINT "sms_day_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "library"."sms_eow_summary"
    ADD CONSTRAINT "sms_eow_summary_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "payment"."domain_allowlist"
    ADD CONSTRAINT "domain_allowlist_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "payment"."sale_users"
    ADD CONSTRAINT "sale_users_sale_id_fkey" FOREIGN KEY ("sale_id") REFERENCES "payment"."sale"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "payment"."sale_users"
    ADD CONSTRAINT "sale_users_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_assessment_fkey" FOREIGN KEY ("assessment") REFERENCES "programs"."program_assessments"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_assessment_responses"
    ADD CONSTRAINT "program_assessment_responses_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_assessments"
    ADD CONSTRAINT "program_assessments_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_feedback"
    ADD CONSTRAINT "program_feedback_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_guide_id_fkey" FOREIGN KEY ("guide_id") REFERENCES "programs"."program_guides"("id") ON UPDATE CASCADE ON DELETE SET NULL;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_guide_id_fkey" FOREIGN KEY ("guide_id") REFERENCES "programs"."program_guides_qa"("id");



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_program_fkey" FOREIGN KEY ("program") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "programs"."program_messages_qa"
    ADD CONSTRAINT "program_messages_qa_stage_fkey" FOREIGN KEY ("stage") REFERENCES "programs"."program_stages"("stage") ON UPDATE CASCADE;



ALTER TABLE ONLY "programs"."program_messages"
    ADD CONSTRAINT "program_messages_stage_fkey" FOREIGN KEY ("stage") REFERENCES "programs"."program_stages"("stage") ON UPDATE CASCADE;



ALTER TABLE ONLY "programs"."programs"
    ADD CONSTRAINT "programs_start_soon_fkey" FOREIGN KEY ("start_soon") REFERENCES "programs"."programs"("id") ON UPDATE CASCADE;



ALTER TABLE ONLY "public"."admins"
    ADD CONSTRAINT "admins_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."sms_verify_logs"
    ADD CONSTRAINT "sms_verify_logs_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_auth_id_fkey" FOREIGN KEY ("auth_id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "schools"."school_activities"
    ADD CONSTRAINT "school_activities_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "schools"."school_messages"
    ADD CONSTRAINT "school_messages_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "schools"."school_resources"
    ADD CONSTRAINT "school_resources_school_id_fkey" FOREIGN KEY ("school_id") REFERENCES "schools"."schools"("school_id") ON UPDATE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_messages"
    ADD CONSTRAINT "symptom_messages_symptom_fkey1" FOREIGN KEY ("symptom") REFERENCES "symptoms"."symptoms"("symptom") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_tip_sections"
    ADD CONSTRAINT "symptom_tip_sections_symptom_tip_fkey1" FOREIGN KEY ("symptom_tip") REFERENCES "symptoms"."symptom_tips"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "symptoms"."symptom_tips"
    ADD CONSTRAINT "symptom_tips_symptom_fkey1" FOREIGN KEY ("symptom") REFERENCES "symptoms"."symptoms"("symptom") ON UPDATE CASCADE ON DELETE CASCADE;



CREATE POLICY "Admin Check" ON "comms"."sms_blocked" USING ("public"."admin_check"());



CREATE POLICY "Admin only" ON "comms"."dr_fred" USING ("public"."admin_check"());



CREATE POLICY "Admin only" ON "comms"."feedback" FOR SELECT USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Admin only" ON "comms"."sms_messages" USING ("public"."admin_check"());



CREATE POLICY "Disable access for all" ON "comms"."notifications" USING (false);



CREATE POLICY "Disable access for all" ON "comms"."sms_statuses" USING (false);



CREATE POLICY "Disable all access" ON "comms"."silent_notifications" USING (false);



CREATE POLICY "Disable all access" ON "comms"."sms_broadcasts" USING (false);



CREATE POLICY "Disable all access" ON "comms"."sms_notify_team_summaries" USING (false);



CREATE POLICY "User can get messages" ON "comms"."dr_fred" FOR SELECT USING (("user_id" = ( SELECT "users"."id"
   FROM "public"."users"
  WHERE ("users"."auth_id" = ( SELECT "auth"."uid"() AS "uid")))));



CREATE POLICY "Users can add feedback" ON "comms"."feedback" FOR INSERT WITH CHECK (true);



ALTER TABLE "comms"."dr_fred" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."feedback" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."silent_notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_blocked" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_broadcasts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_notify_team_summaries" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "comms"."sms_statuses" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Access to authed" ON "community"."community_prompts" FOR SELECT USING (( SELECT ("auth"."uid"() IS NOT NULL)));



CREATE POLICY "Admin all" ON "community"."reported_posts" USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Admin all" ON "community"."tags" USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Admin only" ON "community"."deleted_posts_log" TO "authenticated" USING ("public"."admin_check"());



CREATE POLICY "Disable all access" ON "community"."settings" USING (false);



CREATE POLICY "Public select" ON "community"."comments" FOR SELECT USING (true);



CREATE POLICY "Public select" ON "community"."post_tags" FOR SELECT USING (true);



CREATE POLICY "Public select" ON "community"."posts" FOR SELECT USING (true);



CREATE POLICY "Public select" ON "community"."profiles" FOR SELECT USING (true);



CREATE POLICY "Public select" ON "community"."reactions" FOR SELECT USING (true);



CREATE POLICY "Public select" ON "community"."tags" FOR SELECT USING (true);



CREATE POLICY "Users can create for self" ON "community"."comments" FOR INSERT WITH CHECK (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can edit own posts" ON "community"."posts" FOR UPDATE USING (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id"))) WITH CHECK (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can insert for self" ON "community"."reported_posts" FOR INSERT WITH CHECK (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can react for self" ON "community"."reactions" FOR INSERT WITH CHECK (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can select for self" ON "community"."reported_posts" FOR SELECT USING (("user_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can set read for own activity" ON "community"."activities" FOR UPDATE USING (("recipient_id" = ( SELECT "public"."get_user_id"() AS "get_user_id"))) WITH CHECK (("recipient_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



CREATE POLICY "Users can view own activity" ON "community"."activities" FOR SELECT USING (("recipient_id" = ( SELECT "public"."get_user_id"() AS "get_user_id")));



ALTER TABLE "community"."activities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."comments" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."community_prompts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."deleted_posts_log" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."post_tags" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."posts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."profiles" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."reactions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."reported_posts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."settings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "community"."tags" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "All can select" ON "events"."events" FOR SELECT USING (true);



CREATE POLICY "Disable all access" ON "events"."event_pop_ups" USING (false);



CREATE POLICY "Disable all access" ON "events"."event_users" USING (false);



ALTER TABLE "events"."event_pop_ups" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "events"."event_users" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "events"."events" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all users" ON "groups"."group_members" USING (false);



CREATE POLICY "Insert for authed users" ON "groups"."groups" FOR INSERT WITH CHECK (( SELECT ("public"."get_user_id"() IS NOT NULL)));



CREATE POLICY "User can add for self" ON "groups"."group_subscriptions" FOR INSERT WITH CHECK ((( SELECT ("public"."get_user_id"() = "group_subscriptions"."user_id")) AND ( SELECT ("groups"."get_user_group"() = "group_subscriptions"."group_id")) AND ( SELECT "groups"."check_user_group"("group_subscriptions"."subscribed_to", "group_subscriptions"."group_id") AS "check_user_group")));



CREATE POLICY "User can delete for self" ON "groups"."group_subscriptions" FOR DELETE USING ((( SELECT ("public"."get_user_id"() = "group_subscriptions"."user_id")) AND ( SELECT ("groups"."get_user_group"() = "group_subscriptions"."group_id")) AND ( SELECT "groups"."check_user_group"("group_subscriptions"."subscribed_to", "group_subscriptions"."group_id") AS "check_user_group")));



CREATE POLICY "Users can add" ON "groups"."group_pings" FOR INSERT WITH CHECK ((( SELECT ("public"."get_user_id"() = "group_pings"."from_user_id")) AND ( SELECT ("groups"."get_user_group"() = "group_pings"."group_id")) AND ( SELECT "groups"."check_user_group"("group_pings"."to_user_id", "group_pings"."group_id") AS "check_user_group")));



CREATE POLICY "Users can add for self" ON "groups"."group_activity" FOR INSERT WITH CHECK ((( SELECT ("public"."get_user_id"() = "group_activity"."user_id")) AND ( SELECT ("groups"."get_user_group"() = "group_activity"."group_id"))));



CREATE POLICY "Users can add for self" ON "groups"."group_notes" FOR INSERT WITH CHECK ((( SELECT ("public"."get_user_id"() = "group_notes"."from_member_id")) AND ( SELECT ("groups"."get_user_group"() = "group_notes"."group_id")) AND ( SELECT "groups"."check_user_group"("group_notes"."to_member_id", "group_notes"."group_id") AS "check_user_group")));



CREATE POLICY "Users can read for own group" ON "groups"."group_activity" FOR SELECT USING (( SELECT ("groups"."get_user_group"() = "group_activity"."group_id")));



CREATE POLICY "Users can read own notes" ON "groups"."group_notes" FOR SELECT USING ((( SELECT ("public"."get_user_id"() = "group_notes"."to_member_id")) AND ( SELECT ("groups"."get_user_group"() = "group_notes"."group_id"))));



CREATE POLICY "Users can select for self" ON "groups"."group_pings" FOR SELECT USING ((( SELECT ("public"."get_user_id"() = "group_pings"."from_user_id")) AND ( SELECT ("groups"."get_user_group"() = "group_pings"."group_id")) AND ( SELECT "groups"."check_user_group"("group_pings"."to_user_id", "group_pings"."group_id") AS "check_user_group")));



CREATE POLICY "Users can select for self" ON "groups"."group_subscriptions" FOR SELECT USING (( SELECT ("groups"."get_user_group"() = "group_subscriptions"."group_id")));



CREATE POLICY "Users can select own group" ON "groups"."groups" FOR SELECT USING (( SELECT ("groups"."get_user_group"() = "groups"."id")));



CREATE POLICY "Users can update own group" ON "groups"."groups" FOR UPDATE USING (( SELECT ("groups"."get_user_group"() = "groups"."id")));



ALTER TABLE "groups"."group_activity" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_notes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_pings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."group_subscriptions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "groups"."groups" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "insert_group_messages" ON "groups"."group_messages" FOR INSERT WITH CHECK ((("group_id" = "groups"."get_user_group"()) AND ("user_id" = "public"."get_user_id"())));



CREATE POLICY "select_group_messages" ON "groups"."group_messages" FOR SELECT USING (("group_id" = "groups"."get_user_group"()));



CREATE POLICY "update_group_messages" ON "groups"."group_messages" FOR UPDATE USING ((("user_id" = "public"."get_user_id"()) AND ("group_id" = "groups"."get_user_group"())));



CREATE POLICY "Access to all" ON "library"."craving_resources" FOR SELECT USING (true);



CREATE POLICY "Access to authed" ON "library"."push_check_in" FOR SELECT USING (("auth"."uid"() IS NOT NULL));



CREATE POLICY "Access to authed" ON "library"."sms_day" FOR SELECT USING (("auth"."uid"() IS NOT NULL));



CREATE POLICY "All can access" ON "library"."general_copy" FOR SELECT USING (true);



CREATE POLICY "All can select" ON "library"."onboarding_reviews" FOR SELECT USING (true);



CREATE POLICY "Disable access for all" ON "library"."cache" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_check_in" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_get_back" USING (false);



CREATE POLICY "Disable access for all" ON "library"."sms_slip_up" USING (false);



CREATE POLICY "Disable all access" ON "library"."sms_eow_summary" USING (false);



CREATE POLICY "Disable all access" ON "library"."sms_inactive" USING (false);



CREATE POLICY "Select to all" ON "library"."one_offs" FOR SELECT USING (true);



ALTER TABLE "library"."cache" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."craving_resources" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."general_copy" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."onboarding_reviews" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."one_offs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."push_check_in" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_check_in" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_day" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_eow_summary" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_get_back" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_inactive" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "library"."sms_slip_up" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Access to all" ON "payment"."hard_paywalls" FOR SELECT USING (true);



CREATE POLICY "Disable all access" ON "payment"."domain_allowlist" USING (false);



CREATE POLICY "Disable all access" ON "payment"."promo_codes" USING (false);



CREATE POLICY "Disable public access" ON "payment"."sale" USING (false);



CREATE POLICY "Disable public access" ON "payment"."sale_users" USING (false);



CREATE POLICY "Select to all" ON "payment"."one_time_offers" FOR SELECT USING (true);



ALTER TABLE "payment"."domain_allowlist" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."hard_paywalls" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."one_time_offers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."promo_codes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."sale" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "payment"."sale_users" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Admin only" ON "programs"."program_assessment_responses" USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Disable access for all" ON "programs"."program_assessments" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_feedback" FOR SELECT USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_guides" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_messages" FOR SELECT USING (false);



CREATE POLICY "Disable access for all" ON "programs"."program_stages" USING (false);



CREATE POLICY "Disable access for all" ON "programs"."programs" FOR SELECT USING (false);



ALTER TABLE "programs"."program_assessment_responses" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_assessments" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_feedback" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_guides" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_guides_qa" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_messages_qa" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."program_stages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "programs"."programs" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Admin only" ON "public"."events" FOR SELECT USING (( SELECT "public"."admin_check"() AS "admin_check"));



CREATE POLICY "Disable access for all" ON "public"."admins" USING (false);



CREATE POLICY "Disable access for all" ON "public"."forms" USING (false);



CREATE POLICY "Disable all access" ON "public"."amplitude_test" USING (false);



CREATE POLICY "Disable all acess" ON "public"."sms_verify_logs" USING (false);



CREATE POLICY "Disable public access for all users" ON "public"."api_keys" USING (false);



CREATE POLICY "Enable SELECT for authed users and admins" ON "public"."users" FOR SELECT USING ((("auth_id" = ( SELECT "auth"."uid"() AS "uid")) OR "public"."admin_check"()));



CREATE POLICY "Enable UPDATE for authed users" ON "public"."users" FOR UPDATE USING (("auth_id" = ( SELECT "auth"."uid"() AS "uid"))) WITH CHECK ((("auth_id" = ( SELECT "auth"."uid"() AS "uid")) AND ("id" = "id")));



CREATE POLICY "Enable insert access for all users" ON "public"."events" FOR INSERT WITH CHECK (true);



ALTER TABLE "public"."admins" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."amplitude_test" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."api_keys" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."events" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."forms" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."sms_verify_logs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all" ON "schools"."school_activities" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."school_messages" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."school_resources" USING (false);



CREATE POLICY "Disable access for all" ON "schools"."schools" USING (false);



ALTER TABLE "schools"."school_activities" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."school_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."school_resources" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "schools"."schools" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Disable access for all" ON "symptoms"."symptom_messages" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptom_tip_sections" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptom_tips" USING (false);



CREATE POLICY "Disable access for all" ON "symptoms"."symptoms" USING (false);



ALTER TABLE "symptoms"."symptom_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptom_tip_sections" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptom_tips" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "symptoms"."symptoms" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";






ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "groups"."group_activity";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "groups"."group_messages";



GRANT USAGE ON SCHEMA "comms" TO "service_role";
GRANT USAGE ON SCHEMA "comms" TO "authenticated";
GRANT USAGE ON SCHEMA "comms" TO "anon";



GRANT USAGE ON SCHEMA "community" TO "anon";
GRANT USAGE ON SCHEMA "community" TO "authenticated";
GRANT USAGE ON SCHEMA "community" TO "authenticator";
GRANT USAGE ON SCHEMA "community" TO "service_role";






GRANT USAGE ON SCHEMA "events" TO "anon";
GRANT USAGE ON SCHEMA "events" TO "authenticated";
GRANT USAGE ON SCHEMA "events" TO "service_role";



GRANT USAGE ON SCHEMA "groups" TO "anon";
GRANT USAGE ON SCHEMA "groups" TO "authenticated";
GRANT USAGE ON SCHEMA "groups" TO "service_role";



GRANT USAGE ON SCHEMA "library" TO "anon";
GRANT USAGE ON SCHEMA "library" TO "authenticated";
GRANT USAGE ON SCHEMA "library" TO "service_role";






GRANT USAGE ON SCHEMA "payment" TO "anon";
GRANT USAGE ON SCHEMA "payment" TO "authenticated";
GRANT USAGE ON SCHEMA "payment" TO "service_role";



GRANT USAGE ON SCHEMA "programs" TO "authenticated";
GRANT USAGE ON SCHEMA "programs" TO "service_role";



GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT USAGE ON SCHEMA "views" TO "authenticated";



GRANT ALL ON FUNCTION "comms"."add_dr_fred_message_notification"() TO "anon";
GRANT ALL ON FUNCTION "comms"."add_dr_fred_message_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."add_dr_fred_message_notification"() TO "service_role";



GRANT ALL ON FUNCTION "comms"."add_group_message_notification"() TO "anon";
GRANT ALL ON FUNCTION "comms"."add_group_message_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."add_group_message_notification"() TO "service_role";



GRANT ALL ON FUNCTION "comms"."add_group_note_notification"() TO "anon";
GRANT ALL ON FUNCTION "comms"."add_group_note_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."add_group_note_notification"() TO "service_role";



GRANT ALL ON FUNCTION "comms"."add_group_ping_notification"() TO "anon";
GRANT ALL ON FUNCTION "comms"."add_group_ping_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."add_group_ping_notification"() TO "service_role";



GRANT ALL ON FUNCTION "comms"."add_silent_notification"() TO "anon";
GRANT ALL ON FUNCTION "comms"."add_silent_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."add_silent_notification"() TO "service_role";



GRANT ALL ON FUNCTION "comms"."cancel_sms_broadcast"("p_broadcast_id" bigint) TO "anon";
GRANT ALL ON FUNCTION "comms"."cancel_sms_broadcast"("p_broadcast_id" bigint) TO "authenticated";
GRANT ALL ON FUNCTION "comms"."cancel_sms_broadcast"("p_broadcast_id" bigint) TO "service_role";



GRANT ALL ON FUNCTION "comms"."create_sms_broadcast"("p_message" "text", "p_user_ids" "text"[], "p_batch_size" integer, "p_batch_interval" integer, "p_rest_period" integer) TO "anon";
GRANT ALL ON FUNCTION "comms"."create_sms_broadcast"("p_message" "text", "p_user_ids" "text"[], "p_batch_size" integer, "p_batch_interval" integer, "p_rest_period" integer) TO "authenticated";
GRANT ALL ON FUNCTION "comms"."create_sms_broadcast"("p_message" "text", "p_user_ids" "text"[], "p_batch_size" integer, "p_batch_interval" integer, "p_rest_period" integer) TO "service_role";



GRANT ALL ON FUNCTION "comms"."dr_fred_auto_respond"() TO "anon";
GRANT ALL ON FUNCTION "comms"."dr_fred_auto_respond"() TO "authenticated";
GRANT ALL ON FUNCTION "comms"."dr_fred_auto_respond"() TO "service_role";



GRANT ALL ON FUNCTION "community"."delete_post"("post_id" "uuid") TO "authenticated";



GRANT ALL ON FUNCTION "community"."get_activity_feed"("start_range" integer, "end_range" integer) TO "authenticated";












































































































































































































GRANT ALL ON FUNCTION "groups"."add_checkin_activity"("activity_type" "text", "timezone" "text") TO "anon";
GRANT ALL ON FUNCTION "groups"."add_checkin_activity"("activity_type" "text", "timezone" "text") TO "authenticated";
GRANT ALL ON FUNCTION "groups"."add_checkin_activity"("activity_type" "text", "timezone" "text") TO "service_role";



GRANT ALL ON FUNCTION "groups"."add_member"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "groups"."add_member"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "groups"."add_member"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "groups"."check_user_group"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "groups"."check_user_group"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "groups"."check_user_group"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "groups"."delete"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "groups"."delete"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "groups"."delete"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "groups"."get"() TO "anon";
GRANT ALL ON FUNCTION "groups"."get"() TO "authenticated";
GRANT ALL ON FUNCTION "groups"."get"() TO "service_role";



GRANT ALL ON FUNCTION "groups"."get_user_group"() TO "anon";
GRANT ALL ON FUNCTION "groups"."get_user_group"() TO "authenticated";
GRANT ALL ON FUNCTION "groups"."get_user_group"() TO "service_role";



GRANT ALL ON FUNCTION "groups"."remove_member"("user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "groups"."remove_member"("user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "groups"."remove_member"("user_id" "text") TO "service_role";


















GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_note"("group_id" "uuid", "to_member_id" "text", "from_member_id" "text", "message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_group_ping"("group_id" "uuid", "from_user_id" "text", "to_user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."admin_check"() TO "anon";
GRANT ALL ON FUNCTION "public"."admin_check"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."admin_check"() TO "service_role";



GRANT ALL ON FUNCTION "public"."community_update_profile"() TO "anon";
GRANT ALL ON FUNCTION "public"."community_update_profile"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."community_update_profile"() TO "service_role";



GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "anon";
GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_user"("user_data" "json") TO "service_role";



GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_group"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."dr_fred_get_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."dr_fred_send_message"("message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_check_in_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_claire_prompt"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_generic_demo"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_get_back_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_group"("group_id" "uuid", "user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events"("excluded_users" "text"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_obfuscated_events_count"("excluded_users" "text"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_school_data"("school_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_school_demo"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_slip_up_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_symptom_infos"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_symptom_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_user_id"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_id"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_id"() TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_mem"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_note"("group_id" "uuid", "to_member_id" "text", "message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_add_ping"("group_id" "uuid", "to_user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_delete"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_get"("group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_rem_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_update_activity"("group_id" "uuid", "activity_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_update_subscriptions"("group_id" "uuid", "subscriptions" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."group_upsert"("group_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."http"("request" "public"."http_request") TO "postgres";
GRANT ALL ON FUNCTION "public"."http"("request" "public"."http_request") TO "anon";
GRANT ALL ON FUNCTION "public"."http"("request" "public"."http_request") TO "authenticated";
GRANT ALL ON FUNCTION "public"."http"("request" "public"."http_request") TO "service_role";



GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying, "content" character varying, "content_type" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying, "content" character varying, "content_type" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying, "content" character varying, "content_type" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_delete"("uri" character varying, "content" character varying, "content_type" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying, "data" "jsonb") TO "postgres";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying, "data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying, "data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_get"("uri" character varying, "data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."http_head"("uri" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_head"("uri" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_head"("uri" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_head"("uri" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_header"("field" character varying, "value" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_header"("field" character varying, "value" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_header"("field" character varying, "value" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_header"("field" character varying, "value" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_list_curlopt"() TO "postgres";
GRANT ALL ON FUNCTION "public"."http_list_curlopt"() TO "anon";
GRANT ALL ON FUNCTION "public"."http_list_curlopt"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_list_curlopt"() TO "service_role";



GRANT ALL ON FUNCTION "public"."http_patch"("uri" character varying, "content" character varying, "content_type" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_patch"("uri" character varying, "content" character varying, "content_type" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_patch"("uri" character varying, "content" character varying, "content_type" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_patch"("uri" character varying, "content" character varying, "content_type" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "data" "jsonb") TO "postgres";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "content" character varying, "content_type" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "content" character varying, "content_type" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "content" character varying, "content_type" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_post"("uri" character varying, "content" character varying, "content_type" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_put"("uri" character varying, "content" character varying, "content_type" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_put"("uri" character varying, "content" character varying, "content_type" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_put"("uri" character varying, "content" character varying, "content_type" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_put"("uri" character varying, "content" character varying, "content_type" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."http_reset_curlopt"() TO "postgres";
GRANT ALL ON FUNCTION "public"."http_reset_curlopt"() TO "anon";
GRANT ALL ON FUNCTION "public"."http_reset_curlopt"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_reset_curlopt"() TO "service_role";



GRANT ALL ON FUNCTION "public"."http_set_curlopt"("curlopt" character varying, "value" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."http_set_curlopt"("curlopt" character varying, "value" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."http_set_curlopt"("curlopt" character varying, "value" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."http_set_curlopt"("curlopt" character varying, "value" character varying) TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_code"("input_code" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_email"() TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_email_json"() TO "service_role";



GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "anon";
GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."payment_check_sale"() TO "service_role";



GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "anon";
GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."popin_request_clear"() TO "service_role";



GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "anon";
GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "authenticated";
GRANT ALL ON FUNCTION "public"."popin_request_schedule"("scheduled_for" timestamp with time zone) TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_feedback"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_latest_update"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_messages"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "anon";
GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_get_messages_qa"() TO "service_role";



GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."program_submit_assessment_response"("assessment_id" "text", "responses" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."rem_group_mem"("user_id" "text", "group_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."set_switchboard_number"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_switchboard_number"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_switchboard_number"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_clear"() TO "anon";
GRANT ALL ON FUNCTION "public"."sms_clear"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_clear"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_clear"("message" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sms_schedule"("sms_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."submit_feedback"("user_id" "text", "feedback" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_group_activity"("group_id" "uuid", "user_id" "text", "activity_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_group_subscriptions"("group_id" "uuid", "user_id" "text", "subscriptions" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."upsert_group"("group_data" "jsonb", "user_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."upsert_user"("user_data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."urlencode"("string" "bytea") TO "postgres";
GRANT ALL ON FUNCTION "public"."urlencode"("string" "bytea") TO "anon";
GRANT ALL ON FUNCTION "public"."urlencode"("string" "bytea") TO "authenticated";
GRANT ALL ON FUNCTION "public"."urlencode"("string" "bytea") TO "service_role";



GRANT ALL ON FUNCTION "public"."urlencode"("data" "jsonb") TO "postgres";
GRANT ALL ON FUNCTION "public"."urlencode"("data" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."urlencode"("data" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."urlencode"("data" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."urlencode"("string" character varying) TO "postgres";
GRANT ALL ON FUNCTION "public"."urlencode"("string" character varying) TO "anon";
GRANT ALL ON FUNCTION "public"."urlencode"("string" character varying) TO "authenticated";
GRANT ALL ON FUNCTION "public"."urlencode"("string" character varying) TO "service_role";



GRANT ALL ON TABLE "comms"."dr_fred" TO "authenticated";
GRANT ALL ON TABLE "comms"."dr_fred" TO "service_role";
GRANT ALL ON TABLE "comms"."dr_fred" TO "anon";



GRANT ALL ON SEQUENCE "comms"."dr_fred_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."dr_fred_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."dr_fred_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."feedback" TO "anon";
GRANT ALL ON TABLE "comms"."feedback" TO "authenticated";
GRANT ALL ON TABLE "comms"."feedback" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."feedback_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."feedback_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."feedback_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."notifications" TO "anon";
GRANT ALL ON TABLE "comms"."notifications" TO "authenticated";
GRANT ALL ON TABLE "comms"."notifications" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."notifications_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."notifications_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."notifications_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."silent_notifications" TO "anon";
GRANT ALL ON TABLE "comms"."silent_notifications" TO "authenticated";
GRANT ALL ON TABLE "comms"."silent_notifications" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."silent_notifications_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."silent_notifications_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."silent_notifications_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."sms_blocked" TO "anon";
GRANT ALL ON TABLE "comms"."sms_blocked" TO "authenticated";
GRANT ALL ON TABLE "comms"."sms_blocked" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."sms_blocked_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."sms_blocked_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."sms_blocked_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."sms_broadcasts" TO "anon";
GRANT ALL ON TABLE "comms"."sms_broadcasts" TO "authenticated";
GRANT ALL ON TABLE "comms"."sms_broadcasts" TO "service_role";



GRANT ALL ON TABLE "comms"."sms_messages" TO "service_role";
GRANT ALL ON TABLE "comms"."sms_messages" TO "authenticated";
GRANT ALL ON TABLE "comms"."sms_messages" TO "anon";



GRANT ALL ON TABLE "comms"."sms_statuses" TO "service_role";
GRANT ALL ON TABLE "comms"."sms_statuses" TO "authenticated";
GRANT ALL ON TABLE "comms"."sms_statuses" TO "anon";



GRANT ALL ON TABLE "comms"."sms_broadcast_summary" TO "anon";
GRANT ALL ON TABLE "comms"."sms_broadcast_summary" TO "authenticated";
GRANT ALL ON TABLE "comms"."sms_broadcast_summary" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."sms_broadcasts_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."sms_broadcasts_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."sms_broadcasts_id_seq" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."sms_messages_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."sms_messages_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."sms_messages_id_seq" TO "service_role";



GRANT ALL ON TABLE "comms"."sms_notify_team_summaries" TO "service_role";
GRANT ALL ON TABLE "comms"."sms_notify_team_summaries" TO "anon";
GRANT ALL ON TABLE "comms"."sms_notify_team_summaries" TO "authenticated";



GRANT ALL ON SEQUENCE "comms"."sms_notify_team_summaries_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."sms_notify_team_summaries_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."sms_notify_team_summaries_id_seq" TO "service_role";



GRANT ALL ON SEQUENCE "comms"."sms_statuses_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "comms"."sms_statuses_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "comms"."sms_statuses_id_seq" TO "service_role";



GRANT ALL ON TABLE "community"."activities" TO "authenticated";
GRANT ALL ON TABLE "community"."activities" TO "anon";
GRANT ALL ON TABLE "community"."activities" TO "service_role";



GRANT ALL ON TABLE "community"."comments" TO "anon";
GRANT ALL ON TABLE "community"."comments" TO "authenticated";
GRANT SELECT,INSERT ON TABLE "community"."comments" TO "authenticator";
GRANT ALL ON TABLE "community"."comments" TO "service_role";



GRANT ALL ON TABLE "community"."community_prompts" TO "anon";
GRANT ALL ON TABLE "community"."community_prompts" TO "authenticated";
GRANT ALL ON TABLE "community"."community_prompts" TO "service_role";



GRANT ALL ON TABLE "community"."deleted_posts_log" TO "anon";
GRANT ALL ON TABLE "community"."deleted_posts_log" TO "authenticated";
GRANT ALL ON TABLE "community"."deleted_posts_log" TO "service_role";



GRANT ALL ON TABLE "community"."post_tags" TO "anon";
GRANT ALL ON TABLE "community"."post_tags" TO "authenticated";
GRANT SELECT,INSERT ON TABLE "community"."post_tags" TO "authenticator";
GRANT ALL ON TABLE "community"."post_tags" TO "service_role";



GRANT USAGE ON SEQUENCE "community"."post_tags_id_seq" TO "authenticated";
GRANT USAGE ON SEQUENCE "community"."post_tags_id_seq" TO "authenticator";



GRANT ALL ON TABLE "community"."posts" TO "anon";
GRANT ALL ON TABLE "community"."posts" TO "authenticated";
GRANT SELECT,INSERT,UPDATE ON TABLE "community"."posts" TO "authenticator";
GRANT ALL ON TABLE "community"."posts" TO "service_role";



GRANT ALL ON TABLE "community"."profiles" TO "anon";
GRANT ALL ON TABLE "community"."profiles" TO "authenticated";
GRANT ALL ON TABLE "community"."profiles" TO "service_role";



GRANT ALL ON TABLE "community"."reactions" TO "anon";
GRANT ALL ON TABLE "community"."reactions" TO "authenticated";
GRANT SELECT,INSERT,DELETE ON TABLE "community"."reactions" TO "authenticator";
GRANT ALL ON TABLE "community"."reactions" TO "service_role";



GRANT USAGE ON SEQUENCE "community"."reactions_id_seq" TO "authenticated";
GRANT USAGE ON SEQUENCE "community"."reactions_id_seq" TO "authenticator";



GRANT ALL ON TABLE "community"."reported_posts" TO "anon";
GRANT ALL ON TABLE "community"."reported_posts" TO "authenticated";
GRANT SELECT,INSERT ON TABLE "community"."reported_posts" TO "authenticator";
GRANT ALL ON TABLE "community"."reported_posts" TO "service_role";



GRANT ALL ON TABLE "community"."settings" TO "anon";
GRANT ALL ON TABLE "community"."settings" TO "authenticated";
GRANT ALL ON TABLE "community"."settings" TO "service_role";



GRANT ALL ON TABLE "community"."tags" TO "anon";
GRANT ALL ON TABLE "community"."tags" TO "authenticated";
GRANT SELECT ON TABLE "community"."tags" TO "authenticator";
GRANT ALL ON TABLE "community"."tags" TO "service_role";









GRANT ALL ON TABLE "events"."event_pop_ups" TO "anon";
GRANT ALL ON TABLE "events"."event_pop_ups" TO "authenticated";
GRANT ALL ON TABLE "events"."event_pop_ups" TO "service_role";



GRANT ALL ON TABLE "events"."events" TO "anon";
GRANT ALL ON TABLE "events"."events" TO "authenticated";
GRANT ALL ON TABLE "events"."events" TO "service_role";












GRANT ALL ON TABLE "groups"."group_activity" TO "anon";
GRANT ALL ON TABLE "groups"."group_activity" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_activity" TO "service_role";



GRANT ALL ON TABLE "groups"."group_members" TO "anon";
GRANT ALL ON TABLE "groups"."group_members" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_members" TO "service_role";



GRANT ALL ON TABLE "groups"."group_messages" TO "anon";
GRANT ALL ON TABLE "groups"."group_messages" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_messages" TO "service_role";



GRANT ALL ON TABLE "groups"."group_notes" TO "anon";
GRANT ALL ON TABLE "groups"."group_notes" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_notes" TO "service_role";



GRANT ALL ON TABLE "groups"."group_pings" TO "anon";
GRANT ALL ON TABLE "groups"."group_pings" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_pings" TO "service_role";



GRANT ALL ON TABLE "groups"."group_subscriptions" TO "anon";
GRANT ALL ON TABLE "groups"."group_subscriptions" TO "authenticated";
GRANT ALL ON TABLE "groups"."group_subscriptions" TO "service_role";



GRANT ALL ON TABLE "groups"."groups" TO "anon";
GRANT ALL ON TABLE "groups"."groups" TO "authenticated";
GRANT ALL ON TABLE "groups"."groups" TO "service_role";



GRANT ALL ON TABLE "library"."cache" TO "anon";
GRANT ALL ON TABLE "library"."cache" TO "authenticated";
GRANT ALL ON TABLE "library"."cache" TO "service_role";



GRANT ALL ON TABLE "library"."craving_resources" TO "anon";
GRANT ALL ON TABLE "library"."craving_resources" TO "authenticated";
GRANT ALL ON TABLE "library"."craving_resources" TO "service_role";



GRANT ALL ON SEQUENCE "library"."craving_resources_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."craving_resources_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."craving_resources_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."general_copy" TO "anon";
GRANT ALL ON TABLE "library"."general_copy" TO "authenticated";
GRANT ALL ON TABLE "library"."general_copy" TO "service_role";



GRANT ALL ON TABLE "library"."onboarding_reviews" TO "anon";
GRANT ALL ON TABLE "library"."onboarding_reviews" TO "authenticated";
GRANT ALL ON TABLE "library"."onboarding_reviews" TO "service_role";



GRANT ALL ON SEQUENCE "library"."onboarding_reviews_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."onboarding_reviews_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."onboarding_reviews_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."one_offs" TO "anon";
GRANT ALL ON TABLE "library"."one_offs" TO "authenticated";
GRANT ALL ON TABLE "library"."one_offs" TO "service_role";



GRANT ALL ON TABLE "library"."push_check_in" TO "authenticated";
GRANT ALL ON TABLE "library"."push_check_in" TO "service_role";
GRANT ALL ON TABLE "library"."push_check_in" TO "anon";



GRANT ALL ON SEQUENCE "library"."push_check_in_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."push_check_in_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."push_check_in_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."sms_check_in" TO "anon";
GRANT ALL ON TABLE "library"."sms_check_in" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_check_in" TO "service_role";



GRANT ALL ON TABLE "library"."sms_day" TO "anon";
GRANT ALL ON TABLE "library"."sms_day" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_day" TO "service_role";



GRANT ALL ON SEQUENCE "library"."sms_day_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."sms_day_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."sms_day_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."sms_eow_summary" TO "service_role";
GRANT ALL ON TABLE "library"."sms_eow_summary" TO "anon";
GRANT ALL ON TABLE "library"."sms_eow_summary" TO "authenticated";



GRANT ALL ON SEQUENCE "library"."sms_eow_summary_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."sms_eow_summary_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."sms_eow_summary_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."sms_get_back" TO "anon";
GRANT ALL ON TABLE "library"."sms_get_back" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_get_back" TO "service_role";



GRANT ALL ON TABLE "library"."sms_inactive" TO "service_role";
GRANT ALL ON TABLE "library"."sms_inactive" TO "anon";
GRANT ALL ON TABLE "library"."sms_inactive" TO "authenticated";



GRANT ALL ON SEQUENCE "library"."sms_inactive_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "library"."sms_inactive_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "library"."sms_inactive_id_seq" TO "service_role";



GRANT ALL ON TABLE "library"."sms_slip_up" TO "anon";
GRANT ALL ON TABLE "library"."sms_slip_up" TO "authenticated";
GRANT ALL ON TABLE "library"."sms_slip_up" TO "service_role";



GRANT ALL ON TABLE "payment"."domain_allowlist" TO "anon";
GRANT ALL ON TABLE "payment"."domain_allowlist" TO "authenticated";
GRANT ALL ON TABLE "payment"."domain_allowlist" TO "service_role";



GRANT ALL ON TABLE "payment"."hard_paywalls" TO "anon";
GRANT ALL ON TABLE "payment"."hard_paywalls" TO "authenticated";
GRANT ALL ON TABLE "payment"."hard_paywalls" TO "service_role";



GRANT ALL ON SEQUENCE "payment"."hard_paywalls_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "payment"."hard_paywalls_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "payment"."hard_paywalls_id_seq" TO "service_role";



GRANT ALL ON TABLE "payment"."one_time_offers" TO "anon";
GRANT ALL ON TABLE "payment"."one_time_offers" TO "authenticated";
GRANT ALL ON TABLE "payment"."one_time_offers" TO "service_role";



GRANT ALL ON SEQUENCE "payment"."one_time_offers_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "payment"."one_time_offers_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "payment"."one_time_offers_id_seq" TO "service_role";



GRANT ALL ON TABLE "payment"."promo_codes" TO "anon";
GRANT ALL ON TABLE "payment"."promo_codes" TO "authenticated";
GRANT ALL ON TABLE "payment"."promo_codes" TO "service_role";



GRANT ALL ON TABLE "payment"."sale" TO "anon";
GRANT ALL ON TABLE "payment"."sale" TO "authenticated";
GRANT ALL ON TABLE "payment"."sale" TO "service_role";



GRANT ALL ON SEQUENCE "payment"."sale_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "payment"."sale_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "payment"."sale_id_seq" TO "service_role";



GRANT ALL ON TABLE "payment"."sale_users" TO "anon";
GRANT ALL ON TABLE "payment"."sale_users" TO "authenticated";
GRANT ALL ON TABLE "payment"."sale_users" TO "service_role";



GRANT ALL ON SEQUENCE "payment"."sale_users_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "payment"."sale_users_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "payment"."sale_users_id_seq" TO "service_role";












GRANT SELECT ON TABLE "programs"."program_assessment_responses" TO "authenticated";
GRANT ALL ON TABLE "programs"."program_assessment_responses" TO "service_role";



GRANT ALL ON TABLE "programs"."program_assessments" TO "service_role";



GRANT ALL ON TABLE "programs"."program_feedback" TO "service_role";



GRANT ALL ON TABLE "programs"."program_guides" TO "service_role";



GRANT ALL ON TABLE "programs"."program_guides_qa" TO "service_role";



GRANT ALL ON TABLE "programs"."program_messages" TO "service_role";



GRANT ALL ON TABLE "programs"."program_messages_qa" TO "service_role";



GRANT ALL ON TABLE "programs"."program_stages" TO "service_role";



GRANT ALL ON TABLE "programs"."programs" TO "service_role";



GRANT ALL ON TABLE "public"."admins" TO "anon";
GRANT ALL ON TABLE "public"."admins" TO "authenticated";
GRANT ALL ON TABLE "public"."admins" TO "service_role";



GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."admins_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."amplitude_test" TO "anon";
GRANT ALL ON TABLE "public"."amplitude_test" TO "authenticated";
GRANT ALL ON TABLE "public"."amplitude_test" TO "service_role";



GRANT ALL ON SEQUENCE "public"."amplitude_test_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."amplitude_test_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."amplitude_test_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."api_keys" TO "anon";
GRANT ALL ON TABLE "public"."api_keys" TO "authenticated";
GRANT ALL ON TABLE "public"."api_keys" TO "service_role";



GRANT ALL ON TABLE "public"."events" TO "anon";
GRANT ALL ON TABLE "public"."events" TO "authenticated";
GRANT ALL ON TABLE "public"."events" TO "service_role";



GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."events_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."forms" TO "anon";
GRANT ALL ON TABLE "public"."forms" TO "authenticated";
GRANT ALL ON TABLE "public"."forms" TO "service_role";



GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."forms_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."sms_verify_logs" TO "anon";
GRANT ALL ON TABLE "public"."sms_verify_logs" TO "authenticated";
GRANT ALL ON TABLE "public"."sms_verify_logs" TO "service_role";



GRANT ALL ON SEQUENCE "public"."sms_verify_logs_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."sms_verify_logs_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."sms_verify_logs_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



GRANT ALL ON TABLE "schools"."school_activities" TO "anon";
GRANT ALL ON TABLE "schools"."school_activities" TO "authenticated";



GRANT ALL ON TABLE "schools"."school_messages" TO "anon";
GRANT ALL ON TABLE "schools"."school_messages" TO "authenticated";



GRANT ALL ON TABLE "schools"."school_resources" TO "anon";
GRANT ALL ON TABLE "schools"."school_resources" TO "authenticated";



GRANT ALL ON TABLE "schools"."schools" TO "anon";
GRANT ALL ON TABLE "schools"."schools" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_messages" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_messages" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_tip_sections" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_tip_sections" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptom_tips" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptom_tips" TO "authenticated";



GRANT ALL ON TABLE "symptoms"."symptoms" TO "anon";
GRANT ALL ON TABLE "symptoms"."symptoms" TO "authenticated";



GRANT SELECT ON TABLE "views"."assessment_responses_clear30" TO "authenticated";



GRANT SELECT ON TABLE "views"."claire_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."dr_fred_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."sms_non_user_conversations" TO "authenticated";



GRANT SELECT ON TABLE "views"."sms_user_conversations" TO "authenticated";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON SEQUENCES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON FUNCTIONS  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "comms" GRANT ALL ON TABLES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON SEQUENCES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON FUNCTIONS  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "groups" GRANT ALL ON TABLES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON SEQUENCES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON FUNCTIONS  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "library" GRANT ALL ON TABLES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON SEQUENCES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON FUNCTIONS  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "payment" GRANT ALL ON TABLES  TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "service_role";






























RESET ALL;
