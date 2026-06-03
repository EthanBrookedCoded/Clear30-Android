CREATE OR REPLACE FUNCTION public.get_school_data(school_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    result JSONB;
    cached_result JSONB;
BEGIN
    -- Early exit: if school has no white-label content, return NULL.
    -- Domain-allowlist-only schools may exist in schools.schools but have no
    -- resources, activities, or messages. The iOS app keeps userInfo.schoolData = nil
    -- when this function returns NULL, so no school UI is shown.
    IF NOT EXISTS (SELECT 1 FROM schools.school_resources r WHERE r.school_id = $1 LIMIT 1)
    AND NOT EXISTS (SELECT 1 FROM schools.school_activities a WHERE a.school_id = $1 LIMIT 1)
    AND NOT EXISTS (SELECT 1 FROM schools.school_messages m WHERE m.school_id = $1 LIMIT 1)
    THEN
        RETURN NULL;
    END IF;

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
END;$function$
;
