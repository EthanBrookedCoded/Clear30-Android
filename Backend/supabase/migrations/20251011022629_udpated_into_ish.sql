alter table "library"."health_categories" drop column "intro_disclaimer";

alter table "library"."health_categories" drop column "intro_science";

alter table "library"."health_categories" drop column "intro_title";

alter table "library"."health_categories" add column "intro_content" jsonb;


