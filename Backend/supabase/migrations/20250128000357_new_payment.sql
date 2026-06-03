create schema if not exists "payment";

create table "payment"."domain_allowlist" (
    "domain" text not null,
    "created_at" timestamp with time zone not null default now(),
    "uses" bigint not null default '0'::bigint,
    "org" text not null default ''::text
);


alter table "payment"."domain_allowlist" enable row level security;

create table "payment"."promo_codes" (
    "code" text not null,
    "uses" bigint not null default '0'::bigint
);


alter table "payment"."promo_codes" enable row level security;

CREATE UNIQUE INDEX domain_allowlist_pkey ON payment.domain_allowlist USING btree (domain);

CREATE UNIQUE INDEX promo_codes_pkey ON payment.promo_codes USING btree (code);

alter table "payment"."domain_allowlist" add constraint "domain_allowlist_pkey" PRIMARY KEY using index "domain_allowlist_pkey";

alter table "payment"."promo_codes" add constraint "promo_codes_pkey" PRIMARY KEY using index "promo_codes_pkey";

create policy "Disable all access"
on "payment"."domain_allowlist"
as permissive
for all
to public
using (false);


create policy "Disable all access"
on "payment"."promo_codes"
as permissive
for all
to public
using (false);



set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.payment_check_code(input_code text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
    is_valid boolean;
BEGIN
    -- Check if code exists (case insensitive) and increment uses if it does
    UPDATE payment.promo_codes
    SET uses = uses + 1
    WHERE LOWER(code) = LOWER(input_code)
    RETURNING true INTO is_valid;
    
    -- Return the result (null becomes false)
    RETURN COALESCE(is_valid, false);
END;
$function$
;

CREATE OR REPLACE FUNCTION public.payment_check_email()
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
    user_email text;
    email_domain text;
    org_name text;
BEGIN
    -- Get the email of the currently authenticated user
    SELECT email INTO user_email
    FROM auth.users
    WHERE id = auth.uid();
    
    -- Extract domain from email (everything after @)
    email_domain := split_part(user_email, '@', 2);
    
    -- Check if the domain exists, update uses count, and get org name if it does
    UPDATE payment.domain_allowlist
    SET uses = uses + 1
    WHERE domain = email_domain
    RETURNING org INTO org_name;
    
    -- Return the org name (will be null if domain not found)
    RETURN org_name;
END;
$function$
;


