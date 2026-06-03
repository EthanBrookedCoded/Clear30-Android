create or replace view "views"."assessment_responses_life" as  SELECT par.user_id,
    u.name AS user_name,
    par.id AS response_id,
    (par.responses -> 'Days-Using'::text) AS "Days Using",
    (par.responses -> 'Participation-Reason'::text) AS "Participation Reason",
    (par.responses -> 'Helpful'::text) AS "Helpful",
    (par.responses -> 'Not-Helpful'::text) AS "Not Helpful",
    (par.responses -> 'Identity'::text) AS "Identity",
    (par.responses -> 'Self-Growth'::text) AS "Self Growth",
    (par.responses -> 'Relationship'::text) AS "Relationship",
    (par.responses -> 'Positive-Results'::text) AS "Positive Results",
    (par.responses -> 'Mental-Clarity'::text) AS "Mental Clarity",
    (par.responses -> 'Worth-It'::text) AS "Worth It",
    (par.responses -> 'Comments'::text) AS "Comments",
    (par.responses -> 'LO-Use-State'::text) AS "LO-Use-State",
    (par.responses -> 'Moderation-Tech'::text) AS "Moderation",
    par."timestamp"
   FROM (programs.program_assessment_responses par
     JOIN users u ON ((par.user_id = u.id)))
  WHERE (par.assessment = 'life'::text)
  ORDER BY par."timestamp" DESC;


create or replace view "views"."claire_conversations" as  SELECT last_message.id,
    last_message.user_id,
    u.name,
    (last_message.extra_data ->> 'claire_message'::text) AS text,
    last_message."timestamp"
   FROM (( SELECT DISTINCT ON (events.user_id) events.user_id,
            events.id,
            events."timestamp",
            events.event,
            events.extra_data
           FROM events
          WHERE ((events.event = 'used_claire'::text) AND (events.user_id IS NOT NULL))
          ORDER BY events.user_id, events."timestamp" DESC) last_message
     LEFT JOIN users u ON ((last_message.user_id = u.id)))
  ORDER BY last_message."timestamp" DESC;


create or replace view "views"."dr_fred_conversations" as  SELECT last_message.id,
    last_message.user_id,
    u.name,
    last_message.text,
    last_message.created_at AS "timestamp",
    last_message.outbound
   FROM (( SELECT DISTINCT ON (dr_fred.user_id) dr_fred.user_id,
            dr_fred.id,
            dr_fred.text,
            dr_fred.created_at,
            dr_fred.outbound
           FROM comms.dr_fred
          ORDER BY dr_fred.user_id, dr_fred.created_at DESC) last_message
     LEFT JOIN users u ON ((last_message.user_id = u.id)))
  ORDER BY last_message.outbound, last_message.created_at DESC;


create or replace view "views"."sms_non_user_conversations" as  SELECT last_message.id,
    last_message.phone_number,
    last_message.text,
    last_message.created_at AS "timestamp",
    last_message.outbound
   FROM ( SELECT DISTINCT ON (sms_messages.phone_number) sms_messages.user_id,
            sms_messages.phone_number,
            sms_messages.id,
            sms_messages.text,
            sms_messages.created_at,
            sms_messages.outbound
           FROM comms.sms_messages
          WHERE ((sms_messages.canceled = false) AND (sms_messages.user_id IS NULL) AND (sms_messages.scheduled_for <= now()))
          ORDER BY sms_messages.phone_number, sms_messages.created_at DESC) last_message
  ORDER BY last_message.outbound, last_message.created_at DESC;


create or replace view "views"."sms_user_conversations" as  SELECT last_message.id,
    last_message.user_id,
    u.phone_number,
    u.name,
    last_message.text,
    last_message.created_at AS "timestamp",
    last_message.outbound
   FROM (( SELECT DISTINCT ON (sms_messages.user_id) sms_messages.user_id,
            sms_messages.id,
            sms_messages.text,
            sms_messages.created_at,
            sms_messages.outbound
           FROM comms.sms_messages
          WHERE ((sms_messages.canceled = false) AND (sms_messages.user_id IS NOT NULL) AND (sms_messages.scheduled_for <= now()))
          ORDER BY sms_messages.user_id, sms_messages.created_at DESC) last_message
     LEFT JOIN users u ON ((last_message.user_id = u.id)))
  ORDER BY last_message.outbound, last_message.created_at DESC;



