alter table "library"."sms_downsell" drop column "desc";

alter table "library"."sms_downsell" add column "type" text not null;


