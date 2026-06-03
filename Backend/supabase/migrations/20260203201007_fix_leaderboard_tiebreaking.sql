-- Fix leaderboard tie-breaking: use ROW_NUMBER instead of RANK to ensure unique ranks
-- Secondary sort by name (alphabetically) for deterministic ordering when request counts are equal

CREATE OR REPLACE FUNCTION schools.get_leaderboard(p_limit integer DEFAULT 50)
RETURNS TABLE(university_id bigint, name text, short_name text, logo_url text, request_count bigint, rank bigint)
LANGUAGE plpgsql
SECURITY DEFINER
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        u.id AS university_id,
        u.name,
        u.short_name,
        u.logo_url,
        COUNT(r.id) AS request_count,
        ROW_NUMBER() OVER (ORDER BY COUNT(r.id) DESC, u.name ASC) AS rank
    FROM schools.all_universities u
    INNER JOIN schools.leaderboard_requests r ON u.id = r.university_id
    GROUP BY u.id, u.name, u.short_name, u.logo_url
    ORDER BY request_count DESC, u.name ASC
    LIMIT p_limit;
END;
$function$;
