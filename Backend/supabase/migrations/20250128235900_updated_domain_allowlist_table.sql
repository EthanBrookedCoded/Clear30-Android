alter table "payment"."domain_allowlist" add column "school_id" text;

alter table "payment"."domain_allowlist" add constraint "domain_allowlist_school_id_fkey" FOREIGN KEY (school_id) REFERENCES schools.schools(school_id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "payment"."domain_allowlist" validate constraint "domain_allowlist_school_id_fkey";


set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.payment_check_email_json()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    user_email text;
    email_domain text;
    org_name text;
    org_school_id text;
    result jsonb;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name and school_id
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org, school_id INTO org_name, org_school_id;
    
    -- Raise exception if no matching domain was found
    IF org_name IS NULL THEN
        RAISE EXCEPTION 'Domain % not found in allowlist', email_domain;
    END IF;
    
    -- Construct the JSON return object
    result := jsonb_build_object(
        'org', org_name,
        'school_id', org_school_id
    );
    
    RETURN result;
END;$function$
;


