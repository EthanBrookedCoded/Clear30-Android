-- Mid-pilot assessment table for university partner students
CREATE TABLE schools.mid_pilot_assessments (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    school_id TEXT NOT NULL,
    responses JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- RLS
ALTER TABLE schools.mid_pilot_assessments ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can insert their own" ON schools.mid_pilot_assessments
    FOR INSERT TO authenticated WITH CHECK (user_id = (SELECT public.get_user_id()));

-- Grants
GRANT ALL ON schools.mid_pilot_assessments TO authenticated;
GRANT ALL ON SEQUENCE schools.mid_pilot_assessments_id_seq TO authenticated;

-- RPC function
CREATE OR REPLACE FUNCTION schools.submit_mid_pilot_assessment(
    p_school_id TEXT,
    p_responses JSONB
) RETURNS INT AS $$
DECLARE
    v_id INT;
BEGIN
    INSERT INTO schools.mid_pilot_assessments (user_id, school_id, responses)
    VALUES (public.get_user_id(), p_school_id, p_responses)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION schools.submit_mid_pilot_assessment TO authenticated;
