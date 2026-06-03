create or replace view "views"."assessment_responses_clear30" as  SELECT par.user_id,
    u.name AS user_name,
    par.id AS response_id,
    (par.responses -> 'LO-Age'::text) AS "Age",
    (par.responses -> 'Consumption-Method'::text) AS "Consumption Method",
    (par.responses -> 'Days-Using'::text) AS "Days Using",
    (par.responses -> 'Trigger'::text) AS "Triggers",
    (par.responses -> 'Help-Harm'::text) AS "Help vs Harm",
    (par.responses -> 'Previous-Break'::text) AS "Previous Break",
    (par.responses -> 'Break-Reason'::text) AS "Break Reason",
    (par.responses -> 'Goal30'::text) AS "30 Day Goal",
    (par.responses -> 'Commitment'::text) AS "Commitment",
    (par.responses -> 'Then-What'::text) AS "Then What",
    par."timestamp",
    (par.responses -> 'Money-Spent'::text) AS "Money Spent"
   FROM (programs.program_assessment_responses par
     JOIN users u ON ((par.user_id = u.id)))
  WHERE (par.assessment = 'clear30'::text)
  ORDER BY par."timestamp" DESC;



