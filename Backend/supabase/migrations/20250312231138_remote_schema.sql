create table "views"."test" (
    "user_id" text not null default ''::text,
    "phone_number" text not null default ''::text,
    "name" text not null default ''::text,
    "emoji" text not null default ''::text
);


alter table "views"."test" enable row level security;

create or replace view "views"."clear30_users" as  WITH user_latest_assessment AS (
         SELECT program_assessment_responses.user_id,
            program_assessment_responses.assessment,
            row_number() OVER (PARTITION BY program_assessment_responses.user_id ORDER BY program_assessment_responses."timestamp" DESC) AS rn
           FROM programs.program_assessment_responses
        )
 SELECT ula.user_id,
    u.phone_number,
    u.name,
    u.emoji
   FROM (user_latest_assessment ula
     JOIN users u ON ((ula.user_id = u.id)))
  WHERE ((ula.rn = 1) AND (ula.assessment = 'clear30'::text) AND (u.phone_number IS NOT NULL));


create or replace view "views"."life_users" as  WITH user_latest_assessment AS (
         SELECT program_assessment_responses.user_id,
            program_assessment_responses.assessment,
            row_number() OVER (PARTITION BY program_assessment_responses.user_id ORDER BY program_assessment_responses."timestamp" DESC) AS rn
           FROM programs.program_assessment_responses
        )
 SELECT ula.user_id,
    u.phone_number,
    u.name,
    u.emoji
   FROM (user_latest_assessment ula
     JOIN users u ON ((ula.user_id = u.id)))
  WHERE ((ula.rn = 1) AND ((ula.assessment = 'life-short'::text) OR (ula.assessment = 'life'::text)) AND (u.phone_number IS NOT NULL));



