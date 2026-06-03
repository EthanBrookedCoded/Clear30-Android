drop view if exists "views"."dr_fred_conversations";

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
     LEFT JOIN public.users u ON ((last_message.user_id = u.id)))
  ORDER BY last_message.outbound, last_message.created_at DESC;



