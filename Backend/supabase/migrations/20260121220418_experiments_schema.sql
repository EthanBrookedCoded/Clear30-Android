-- Create experiments schema
CREATE SCHEMA IF NOT EXISTS experiments;

-- Grant permissions
GRANT USAGE ON SCHEMA experiments TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA experiments TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA experiments TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA experiments TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA experiments GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA experiments GRANT ALL ON ROUTINES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA experiments GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;

-- 1. Experiment definitions
CREATE TABLE experiments.experiments (
    id TEXT PRIMARY KEY,
    description TEXT,
    enabled BOOLEAN DEFAULT true,
    variants JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE experiments.experiments IS 'A/B test experiment definitions with weighted variants';
COMMENT ON COLUMN experiments.experiments.id IS 'Unique experiment identifier, e.g. video-testimonial-welcome';
COMMENT ON COLUMN experiments.experiments.variants IS 'JSON array of variants with name, weight (0-100), and payload';

-- 2. User assignments (sticky)
CREATE TABLE experiments.user_assignments (
    user_id TEXT NOT NULL,
    experiment_id TEXT NOT NULL REFERENCES experiments.experiments(id) ON DELETE CASCADE,
    variant TEXT NOT NULL,
    payload JSONB,
    assigned_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, experiment_id)
);

COMMENT ON TABLE experiments.user_assignments IS 'Sticky user-to-variant assignments for experiments';
COMMENT ON COLUMN experiments.user_assignments.user_id IS 'User logging_id';
COMMENT ON COLUMN experiments.user_assignments.variant IS 'Assigned variant name, or "none" if not enrolled';

-- Index for fast lookup by user
CREATE INDEX idx_user_assignments_user ON experiments.user_assignments(user_id);

-- RLS policies
ALTER TABLE experiments.experiments ENABLE ROW LEVEL SECURITY;
ALTER TABLE experiments.user_assignments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow read experiments" ON experiments.experiments
    FOR SELECT TO anon, authenticated
    USING (true);

CREATE POLICY "Allow read/write assignments" ON experiments.user_assignments
    FOR ALL TO anon, authenticated
    USING (true) WITH CHECK (true);

-- Function to get user experiments with sticky assignment
CREATE OR REPLACE FUNCTION experiments.get_user_experiments(p_user_id TEXT)
RETURNS TABLE (
    experiment_id TEXT,
    variant TEXT,
    payload JSONB
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    exp RECORD;
    assigned_variant TEXT;
    assigned_payload JSONB;
    random_val INT;
    cumulative INT;
    v RECORD;
    already_checked BOOLEAN;
BEGIN
    FOR exp IN SELECT * FROM experiments.experiments WHERE enabled = true
    LOOP
        assigned_variant := NULL;
        assigned_payload := NULL;
        already_checked := FALSE;

        -- Check existing assignment (including "none" assignments)
        SELECT ua.variant, ua.payload INTO assigned_variant, assigned_payload
        FROM experiments.user_assignments ua
        WHERE ua.user_id = p_user_id AND ua.experiment_id = exp.id;

        IF FOUND THEN
            already_checked := TRUE;
        END IF;

        IF NOT already_checked THEN
            -- Roll against 100 (percentage-based)
            -- If weights sum to less than 100, user may not be enrolled
            random_val := floor(random() * 100);
            cumulative := 0;

            FOR v IN SELECT * FROM jsonb_array_elements(exp.variants->'variants')
            LOOP
                cumulative := cumulative + (v.value->>'weight')::INT;
                IF random_val < cumulative THEN
                    assigned_variant := v.value->>'name';
                    assigned_payload := v.value->'payload';
                    EXIT;
                END IF;
            END LOOP;

            -- Insert assignment (even if NULL - marks user as "checked but not enrolled")
            -- Use "none" to indicate user was checked but not enrolled
            INSERT INTO experiments.user_assignments (user_id, experiment_id, variant, payload)
            VALUES (p_user_id, exp.id, COALESCE(assigned_variant, 'none'), assigned_payload);

            -- Update assigned_variant if we stored "none"
            IF assigned_variant IS NULL THEN
                assigned_variant := 'none';
            END IF;
        END IF;

        -- Only return if user is actually enrolled (not "none")
        IF assigned_variant IS NOT NULL AND assigned_variant != 'none' THEN
            experiment_id := exp.id;
            variant := assigned_variant;
            payload := assigned_payload;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION experiments.get_user_experiments IS 'Returns experiment assignments for a user, creating sticky assignments on first call. Weights are percentage-based (0-100). If weights sum to less than 100, some users will not be enrolled.';
