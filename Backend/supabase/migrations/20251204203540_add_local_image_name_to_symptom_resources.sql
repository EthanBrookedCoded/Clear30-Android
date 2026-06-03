-- Add optional local_image_name column to symptom_resources table
alter table "symptoms"."symptom_resources"
add column "local_image_name" text;

