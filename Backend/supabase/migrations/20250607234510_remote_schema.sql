create or replace view "platform"."sms_non_user_conversations" as  SELECT last_message.id,
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


create or replace view "platform"."sms_user_conversations" as  SELECT last_message.id,
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



