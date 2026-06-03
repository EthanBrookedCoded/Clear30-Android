set check_function_bodies = off;

CREATE OR REPLACE FUNCTION programs.get_matching_assessment_count(criteria jsonb)
 RETURNS integer
 LANGUAGE plpgsql
AS $function$
DECLARE
    sql_query TEXT;
    condition_parts TEXT[];
    key TEXT;
    value TEXT;
    result_count INTEGER;
BEGIN
    -- Initialize the base query
    sql_query := 'SELECT COUNT(*) FROM programs.program_assessment_responses WHERE ';
    
    -- Build conditions for each key-value pair in the criteria
    FOR key, value IN SELECT * FROM jsonb_each_text(criteria)
    LOOP
        -- Create condition that handles both array and string storage formats
        condition_parts := array_append(condition_parts, 
            format('(responses->%L @> %L::jsonb OR responses->>%L = %L)', 
                   key, 
                   '["' || value || '"]',
                   key,
                   value)
        );
    END LOOP;
    
    -- If no criteria provided, return 0
    IF array_length(condition_parts, 1) IS NULL THEN
        RETURN 0;
    END IF;
    
    -- Join all conditions with AND
    sql_query := sql_query || array_to_string(condition_parts, ' AND ');
    
    -- Execute the dynamic query
    EXECUTE sql_query INTO result_count;
    
    RETURN result_count;
END;
$function$
;


