alter table "library"."support_items" add column "button_image_size" real;

alter table "library"."support_items" add column "button_image_url" text;

alter table "library"."support_items" add column "direct_url" text;

alter table "library"."support_items" alter column "sheet_info" drop not null;


