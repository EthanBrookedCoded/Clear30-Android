alter table "library"."health_categories" add column "fda_disclaimer" text not null default ''::text;

alter table "library"."health_categories" add column "intro_disclaimer" text not null default ''::text;

alter table "library"."health_categories" add column "intro_science" text not null default ''::text;

alter table "library"."health_categories" add column "intro_title" text not null default ''::text;

alter table "library"."health_categories" add column "order" smallint not null;


