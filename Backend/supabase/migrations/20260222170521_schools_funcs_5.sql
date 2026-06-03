CREATE OR REPLACE FUNCTION schools.get_school_assessment_stats(p_school_id text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
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
    ),
    first_assessments AS (
      SELECT DISTINCT ON (par.user_id)
        par.responses
      FROM programs.program_assessment_responses par
      WHERE par.assessment = 'clear30'
        AND par.user_id IN (SELECT id FROM school_user_ids)
      ORDER BY par.user_id, par.timestamp ASC
    )
    SELECT COALESCE(
      jsonb_agg(
        jsonb_build_object(
          'Trigger',      responses->'Trigger',
          'Break-Reason', responses->'Break-Reason',
          'Goal30',       responses->'Goal30',
          'Then-What',    responses->'Then-What',
          'Days-Using',   responses->'Days-Using'
        )
      ),
      '[]'::jsonb
    )
    FROM first_assessments
  );
END;
$$;
