-- Read-side RPC for the post-pilot ("life") assessment, scoped to a school.
-- Mirrors schools.get_school_assessment_stats, which returns each school user's
-- earliest "clear30" (onboarding) assessment. This sibling returns each user's
-- earliest "life" assessment so the dashboard can show 30-day outcomes alongside
-- the onboarding snapshot.

CREATE OR REPLACE FUNCTION schools.get_school_post_assessment_stats(p_school_id TEXT)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = schools, public, payment, programs
AS $$
BEGIN
  IF schools.get_portal_school_id() IS DISTINCT FROM p_school_id AND NOT schools.is_platform_admin() THEN
    RETURN '[]'::jsonb;
  END IF;

  RETURN (
    WITH school_user_ids AS (
      SELECT DISTINCT u.id::text AS id
      FROM public.users u
      JOIN payment.domain_allowlist dal ON u.email ILIKE ('%@' || dal.domain)
      WHERE dal.school_id = p_school_id
        AND u.email NOT IN (SELECT email FROM schools.portal_users WHERE school_id = p_school_id)
    ),
    first_assessments AS (
      SELECT DISTINCT ON (par.user_id)
        par.responses
      FROM programs.program_assessment_responses par
      WHERE par.assessment = 'life'
        AND par.user_id IN (SELECT id FROM school_user_ids)
      ORDER BY par.user_id, par.timestamp ASC
    )
    SELECT COALESCE(jsonb_agg(responses), '[]'::jsonb)
    FROM first_assessments
  );
END;
$$;

GRANT EXECUTE ON FUNCTION schools.get_school_post_assessment_stats(TEXT) TO authenticated;
