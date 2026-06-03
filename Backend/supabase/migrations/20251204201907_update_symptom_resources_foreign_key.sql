-- Add foreign key constraint to symptoms.symptoms table
alter table "symptoms"."symptom_resources"
add constraint "symptom_resources_symptom_fkey"
foreign key ("symptom")
references "symptoms"."symptoms"("symptom")
on delete cascade;

