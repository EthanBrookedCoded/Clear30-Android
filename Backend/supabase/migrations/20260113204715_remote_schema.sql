create extension if not exists "pg_cron" with schema "pg_catalog";

drop trigger if exists "Handle new users" on "public"."users";

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


CREATE TRIGGER handle_new_user AFTER INSERT ON public.users FOR EACH ROW EXECUTE FUNCTION supabase_functions.http_request('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/user_handle_new', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');


