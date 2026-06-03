create or replace view "platform"."assessment_responses_clear30" as  SELECT par.user_id,
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
    (par.responses -> 'Money-Spent'::text) AS "Money Spent",
    (par.responses -> 'Start-Date'::text) AS "Start Date"
   FROM (programs.program_assessment_responses par
     JOIN users u ON ((par.user_id = u.id)))
  WHERE (par.assessment = 'clear30'::text)
  ORDER BY par."timestamp" DESC;


create or replace view "platform"."assessment_responses_life" as  SELECT par.user_id,
    u.name AS user_name,
    par.id AS response_id,
    (par.responses -> 'Days-Using'::text) AS "Days Using",
    (par.responses -> 'Used-Less'::text) AS "Used Less",
    (par.responses -> 'Participation-Reason'::text) AS "Participation Reason",
    (par.responses -> 'Helpful'::text) AS "Helpful",
    (par.responses -> 'Not-Helpful'::text) AS "Not Helpful",
    (par.responses -> 'Positive-Results'::text) AS "Positive Results",
    (par.responses -> 'Mental-Clarity'::text) AS "Mental Clarity",
    (par.responses -> 'Self-Growth'::text) AS "Self Growth",
    (par.responses -> 'Relationship'::text) AS "Relationship",
    (par.responses -> 'Identity'::text) AS "Identity",
    (par.responses -> 'Worth-It'::text) AS "Worth It",
    (par.responses -> 'Mental-Health'::text) AS "Mental Health",
    (par.responses -> 'LO-Use-State'::text) AS "LO-Use-State",
    (par.responses -> 'Moderation-Tech'::text) AS "Moderation Tech",
    (par.responses -> 'Comments'::text) AS "Comments",
    (par.responses -> 'Gain_Mental_Clarity-Met'::text) AS "Gain Mental Clarity Met",
    (par.responses -> 'Reduce_Anxiety-Met'::text) AS "Reduce Anxiety Met",
    (par.responses -> 'Reduce_Depression-Met'::text) AS "Reduce Depression Met",
    (par.responses -> 'Reduce_Being_Stuck_in_Own_Head-Met'::text) AS "Reduce Being Stuck in Own Head Met",
    (par.responses -> 'Improve_Sleep_Quality-Met'::text) AS "Improve Sleep Quality Met",
    (par.responses -> 'Improve_Self-Control_and_Intention-Met'::text) AS "Improve Self-Control and Intention Met",
    (par.responses -> 'Reduce_Dependency_on_Cannabis-Met'::text) AS "Reduce Dependency on Cannabis Met",
    (par.responses -> 'Explore_Life_Without_Cannabis-Met'::text) AS "Explore Life Without Cannabis Met",
    (par.responses -> 'Lower_Tolerance-Met'::text) AS "Lower Tolerance Met",
    (par.responses -> 'Improve_Overall_Health-Met'::text) AS "Improve Overall Health Met",
    (par.responses -> 'Improve_Lung_Health-Met'::text) AS "Improve Lung Health Met",
    (par.responses -> 'Increase_Productivity-Met'::text) AS "Increase Productivity Met",
    (par.responses -> 'Increase_Motivation-Met'::text) AS "Increase Motivation Met",
    (par.responses -> 'Save_Money-Met'::text) AS "Save Money Met",
    (par.responses -> 'Improve_Current_Relationships-Met'::text) AS "Improve Current Relationships Met",
    (par.responses -> 'Enhance_Social_Connections-Met'::text) AS "Enhance Social Connections Met",
    (par.responses -> 'Pass_Work-Required_Drug_Test-Met'::text) AS "Pass Work-Required Drug Test Met",
    (par.responses -> 'Meet_Legal_Obligations-Met'::text) AS "Meet Legal Obligations Met",
    (par.responses -> 'Other-Met'::text) AS "Other Met",
    par."timestamp"
   FROM (programs.program_assessment_responses par
     JOIN users u ON ((par.user_id = u.id)))
  WHERE (par.assessment = 'life'::text)
  ORDER BY par."timestamp" DESC;



