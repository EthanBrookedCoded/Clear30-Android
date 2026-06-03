create or replace view "platform"."sms_non_user_conversations_v2" as  SELECT last_msg.id,
    last_msg.phone_number,
    last_msg.text,
    last_msg.created_at AS "timestamp",
    last_msg.outbound
   FROM ( SELECT DISTINCT ON (sms_messages.phone_number) sms_messages.id,
            sms_messages.phone_number,
            sms_messages.text,
            sms_messages.created_at,
            sms_messages.outbound
           FROM comms.sms_messages
          WHERE ((sms_messages.canceled = false) AND (sms_messages.user_id IS NULL) AND (sms_messages.scheduled_for <= CURRENT_TIMESTAMP))
          ORDER BY sms_messages.phone_number, sms_messages.created_at DESC) last_msg;


create or replace view "platform"."sms_user_conversations_v2" as  SELECT last_msg.id,
    last_msg.user_id,
    u.phone_number,
    u.name,
    last_msg.text,
    last_msg.created_at AS "timestamp",
    last_msg.outbound
   FROM (( SELECT DISTINCT ON (sms_messages.user_id) sms_messages.id,
            sms_messages.user_id,
            sms_messages.text,
            sms_messages.created_at,
            sms_messages.outbound
           FROM comms.sms_messages
          WHERE ((sms_messages.canceled = false) AND (sms_messages.user_id IS NOT NULL) AND (sms_messages.scheduled_for <= CURRENT_TIMESTAMP))
          ORDER BY sms_messages.user_id, sms_messages.created_at DESC) last_msg
     LEFT JOIN users u ON ((last_msg.user_id = u.id)));



