create or replace view "views"."users_with_assessment_no_checkin_90_days" as  SELECT DISTINCT par.user_id
   FROM (programs.program_assessment_responses par
     JOIN users u ON ((par.user_id = u.id)))
  WHERE ((par."timestamp" >= (now() - '90 days'::interval)) AND (u.day_info = '[]'::jsonb));



