alter table "achievements"."definitions" drop column "icon_url";

alter table "achievements"."definitions" add column "sf_symbol" text not null;


