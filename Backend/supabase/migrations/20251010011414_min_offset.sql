alter table "library"."health_steps" drop column "check_in_offset";

alter table "library"."health_steps" add column "minute_offset" smallint not null;


