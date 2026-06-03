create or replace view "views"."thatchers_fuck_up" as  SELECT DISTINCT sm.phone_number
   FROM (comms.sms_statuses ss
     JOIN comms.sms_messages sm ON ((ss.message_id = sm.id)))
  WHERE ((ss.status = 'delivered'::text) AND ((ss.id >= 85628) AND (ss.id <= 88989)));



