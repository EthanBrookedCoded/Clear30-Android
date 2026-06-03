set check_function_bodies = off;

CREATE OR REPLACE FUNCTION comms.find_testimonial_candidates()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_inserted INTEGER := 0;
    v_candidates RECORD;
    v_candidate_list TEXT := '';
    v_metadata JSONB;
BEGIN
    -- Insert new candidates who meet all criteria
    INSERT INTO comms.video_testimonial_candidates (user_id, sober_days, last_check_in_at)
    SELECT
        u.id,
        comms.calculate_sober_days(u.id) as sober_days,
        comms.get_last_check_in_date(u.id) as last_check_in_at
    FROM public.users u
    WHERE
        -- Not already a candidate
        u.id NOT IN (SELECT user_id FROM comms.video_testimonial_candidates WHERE user_id IS NOT NULL)
        -- Not already submitted a testimonial
        AND u.id NOT IN (SELECT user_id FROM comms.video_testimonials WHERE user_id IS NOT NULL)
        -- Sober for > 30 days
        AND comms.calculate_sober_days(u.id) > 30
        -- Checked in within last 5 days (based on last date in day_info)
        AND comms.get_last_check_in_date(u.id) > NOW() - INTERVAL '5 days'
    ON CONFLICT (user_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    -- Send daily summary Slack notification if any new candidates found
    IF v_inserted > 0 THEN
        -- Build list of new candidates (added in this run)
        FOR v_candidates IN
            SELECT c.user_id, c.sober_days, u.name, u.phone_number, u.email
            FROM comms.video_testimonial_candidates c
            JOIN public.users u ON u.id = c.user_id
            WHERE c.created_at > NOW() - INTERVAL '1 minute'
            ORDER BY c.sober_days DESC
            LIMIT 10
        LOOP
            v_candidate_list := v_candidate_list ||
                E'• *' || COALESCE(v_candidates.name, 'Unknown') || '* - ' ||
                v_candidates.sober_days || ' days sober' || E'\n' ||
                '   Phone: ' || COALESCE(v_candidates.phone_number, 'N/A') ||
                ' | Email: ' || COALESCE(v_candidates.email, 'N/A') || E'\n';
        END LOOP;

        v_metadata := jsonb_build_object(
            'blocks', jsonb_build_array(
                jsonb_build_object(
                    'type', 'header',
                    'text', jsonb_build_object(
                        'type', 'plain_text',
                        'text', '🎬 Daily Testimonial Candidates Report'
                    )
                ),
                jsonb_build_object(
                    'type', 'section',
                    'text', jsonb_build_object(
                        'type', 'mrkdwn',
                        'text', '*Found ' || v_inserted || ' new candidate(s) today:*' || E'\n\n' || v_candidate_list
                    )
                ),
                jsonb_build_object(
                    'type', 'context',
                    'elements', jsonb_build_array(
                        jsonb_build_object(
                            'type', 'mrkdwn',
                            'text', 'Criteria: 30+ sober days, checked in within last 5 days, no prior submission'
                        )
                    )
                )
            )
        );

        INSERT INTO comms.slack_notifications (
            channel_type, title, message, metadata, status, priority
        ) VALUES (
            'testimonials',
            'Daily Testimonial Candidates',
            'Found ' || v_inserted || ' new testimonial candidate(s)',
            v_metadata,
            'pending',
            'normal'
        );
    END IF;

    RETURN v_inserted;
END;$function$
;


