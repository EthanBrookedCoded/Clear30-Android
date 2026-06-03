-- Read-side RPC for mid-pilot assessment results, scoped to a school.
-- The schools.mid_pilot_assessments table has INSERT-only RLS for authenticated
-- users, so portal users (and platform admins) need a SECURITY DEFINER function
-- to read aggregated responses for their school dashboard.

CREATE OR REPLACE FUNCTION schools.get_school_mid_pilot_stats(p_school_id TEXT)
RETURNS TABLE (
    responses JSONB,
    created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = schools, public
AS $$
BEGIN
    RETURN QUERY
    SELECT mpa.responses, mpa.created_at
    FROM schools.mid_pilot_assessments mpa
    WHERE mpa.school_id = p_school_id
    ORDER BY mpa.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION schools.get_school_mid_pilot_stats(TEXT) TO authenticated;
