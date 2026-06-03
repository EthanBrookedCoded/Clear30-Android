create schema if not exists "views";

create or replace view "views"."messages_life_program" as  SELECT pm.day,
        CASE
            WHEN ((pm.question_id = 'LO-Use-State'::text) AND (pm.question_response = 'I want to moderate my use'::text)) THEN 'moderation'::text
            WHEN (pm.question_id = 'LO-Use-State'::text) THEN 'abstinence'::text
            ELSE NULL::text
        END AS state,
    pm.title,
    pm.body,
    jsonb_object_agg(r.title, r.url) AS resources,
    jsonb_object_agg(cp.title, cp.prompt) AS claire_prompts,
    COALESCE(journal_prompts.journal_prompts, '[]'::jsonb) AS journal_prompts
   FROM (((((programs.program_messages pm
     LEFT JOIN programs.program_message_resources pmr ON ((pm.id = pmr.message_id)))
     LEFT JOIN library.resources r ON ((pmr.resource_id = r.id)))
     LEFT JOIN programs.program_message_claire_prompts pmcp ON ((pm.id = pmcp.message_id)))
     LEFT JOIN library.claire_prompts cp ON ((pmcp.prompt_id = cp.id)))
     LEFT JOIN ( SELECT pmjp.message_id,
            jsonb_agg(jp.prompt) AS journal_prompts
           FROM (programs.program_message_journal_prompts pmjp
             LEFT JOIN library.journal_prompts jp ON ((pmjp.prompt_id = jp.id)))
          GROUP BY pmjp.message_id) journal_prompts ON ((pm.id = journal_prompts.message_id)))
  WHERE (pm.program = 'life'::text)
  GROUP BY pm.day,
        CASE
            WHEN ((pm.question_id = 'LO-Use-State'::text) AND (pm.question_response = 'I want to moderate my use'::text)) THEN 'moderation'::text
            WHEN (pm.question_id = 'LO-Use-State'::text) THEN 'abstinence'::text
            ELSE NULL::text
        END, pm.title, pm.body, journal_prompts.journal_prompts
  ORDER BY pm.day;



