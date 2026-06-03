-- Video Testimonial Candidates System
-- Automatically identifies users eligible for video testimonials and notifies via Slack

-------------------------------------------
-- 1. Create Table: video_testimonial_candidates
-------------------------------------------

CREATE TABLE comms.video_testimonial_candidates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id TEXT REFERENCES public.users(id),
    sober_days INTEGER NOT NULL,
    last_check_in_at TIMESTAMPTZ NOT NULL,
    notified_at TIMESTAMPTZ DEFAULT NOW(),
    status TEXT DEFAULT 'pending',  -- pending, contacted, declined, converted, backfilled
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id)  -- Prevent duplicate entries
);

-- Indexes
CREATE INDEX idx_vtc_user_id ON comms.video_testimonial_candidates(user_id);
CREATE INDEX idx_vtc_status ON comms.video_testimonial_candidates(status);
CREATE INDEX idx_vtc_created_at ON comms.video_testimonial_candidates(created_at);

-- RLS
ALTER TABLE comms.video_testimonial_candidates ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Service role only" ON comms.video_testimonial_candidates
    FOR ALL USING (false);  -- Only service role can access

-------------------------------------------
-- 2. Helper Function: Calculate Sober Days
-------------------------------------------

CREATE OR REPLACE FUNCTION comms.calculate_sober_days(p_user_id TEXT)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
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
    IF v_day_info IS NOT NULL AND jsonb_array_length(v_day_info) > 0 THEN
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
$$;

-------------------------------------------
-- 2b. Helper Function: Get Last Check-in Date from day_info
-------------------------------------------

CREATE OR REPLACE FUNCTION comms.get_last_check_in_date(p_user_id TEXT)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
DECLARE
    v_day_info JSONB;
    v_last_date TEXT;
BEGIN
    -- Get user day_info
    SELECT day_info INTO v_day_info
    FROM public.users WHERE id = p_user_id;

    -- day_info is array of pairs: [date, {sober: bool}, date, {sober: bool}, ...]
    -- Last date is at index (length - 2), second to last element
    IF v_day_info IS NOT NULL AND jsonb_array_length(v_day_info) >= 2 THEN
        v_last_date := v_day_info->>(jsonb_array_length(v_day_info) - 2);
        RETURN v_last_date::TIMESTAMPTZ;
    END IF;

    RETURN NULL;
END;
$$;

-------------------------------------------
-- 3. Conversion Trigger (when testimonial submitted)
-------------------------------------------

CREATE OR REPLACE FUNCTION comms.mark_testimonial_candidate_converted()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE comms.video_testimonial_candidates
    SET status = 'converted', notes = 'Submitted testimonial on ' || TO_CHAR(NOW(), 'MM/DD/YYYY')
    WHERE user_id = NEW.user_id;

    RETURN NEW;
END;
$$;

-- Trigger on video_testimonials insert
CREATE TRIGGER trigger_mark_candidate_converted
    AFTER INSERT ON comms.video_testimonials
    FOR EACH ROW
    EXECUTE FUNCTION comms.mark_testimonial_candidate_converted();

-------------------------------------------
-- 4. Daily Cron Job Function
-------------------------------------------

CREATE OR REPLACE FUNCTION comms.find_testimonial_candidates()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
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
            SELECT c.user_id, c.sober_days, u.name, u.phone, u.email
            FROM comms.video_testimonial_candidates c
            JOIN public.users u ON u.id = c.user_id
            WHERE c.created_at > NOW() - INTERVAL '1 minute'
            ORDER BY c.sober_days DESC
            LIMIT 10
        LOOP
            v_candidate_list := v_candidate_list ||
                E'• *' || COALESCE(v_candidates.name, 'Unknown') || '* - ' ||
                v_candidates.sober_days || ' days sober' || E'\n' ||
                '   Phone: ' || COALESCE(v_candidates.phone, 'N/A') ||
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
END;
$$;

