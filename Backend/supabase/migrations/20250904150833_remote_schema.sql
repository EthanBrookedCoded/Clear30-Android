set check_function_bodies = off;

CREATE OR REPLACE FUNCTION library.increment_assessment_social_proof_count()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    current_json jsonb;
    current_count text;
    new_count text;
    updated_json jsonb;
BEGIN
    -- Get the current JSON data
    SELECT value::jsonb INTO current_json
    FROM library.one_offs
    WHERE key = 'assessment_social_proof';
    
    -- Check if the record exists
    IF current_json IS NULL THEN
        RAISE EXCEPTION 'assessment_social_proof data not found';
    END IF;
    
    -- Extract current people count
    current_count := current_json->>'peopleCount';
    
    -- Remove commas and convert to integer, then increment
    new_count := (REPLACE(current_count, ',', '')::integer + 1)::text;
    
    -- Add commas back for display (every 3 digits)
    new_count := REGEXP_REPLACE(new_count, '(\d)(?=(\d{3})+(?!\d))', '\1,', 'g');
    
    -- Update the JSON with new count
    updated_json := jsonb_set(current_json, '{peopleCount}', to_jsonb(new_count));
    
    -- Update the record
    UPDATE library.one_offs
    SET value = updated_json::text
    WHERE key = 'assessment_social_proof';
    
END;
$function$
;


