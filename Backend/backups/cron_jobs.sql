SELECT cron.schedule('Send ad spend to ampltiude', '0 */4 * * *', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_adspend',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
SELECT cron.schedule('Send silent notifications', '0 * * * *', $$SELECT comms.add_silent_notification()$$);
SELECT cron.schedule('Daily scheduled sms', '0 16 * * *', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/sms_schedule',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
SELECT cron.schedule('Send notifications', '30 seconds', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/notification_send',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
SELECT cron.schedule('Cleanup cron job details.', '0 4 * * *', $$DELETE FROM cron.job_run_details WHERE start_time < NOW() - INTERVAL '1 day'$$);
SELECT cron.schedule('Cleanup net logs.', '0 5 * * *', $$TRUNCATE TABLE net._http_response;$$);
SELECT cron.schedule('Send scheduled SMS messages', '5 seconds', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/sms_send',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
SELECT cron.schedule('Cleanup old user events.', '0 3 * * *', $$DELETE FROM public.events WHERE timestamp < NOW() - INTERVAL '7 days'$$);
SELECT cron.schedule('send_adjust_2h_trial', '0 * * * *', $$select   net.http_post(       url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/rc_trial_2h',       headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'),        timeout_milliseconds:=1000   );$$);
SELECT cron.schedule('check_testimonial_candidates', '0 9 * * *', $$SELECT comms.find_testimonial_candidates()$$);
SELECT cron.schedule('SMS Start Soon Schedule', '0 15 * * *', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/sms_start_soon_schedule',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
  SELECT cron.schedule('Peer Schedule Messages', '0 16 * * *', $$select
  net.http_post(
      url:='https://quluipmdicjsolnsopkg.supabase.co/functions/v1/peer_day_schedule',
      headers:=jsonb_build_object('Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY'), 
      timeout_milliseconds:=1000
  );$$);
SELECT cron.schedule('Peer notify for scheduled messages', '*/5 * * * *', $$INSERT INTO comms.notifications (user_id, title, body, metadata, timestamp, status)
    SELECT
        pm.user_id,
        '💬 Gerad',
        LEFT(pm.text, 100),
        jsonb_build_object('type', 'peer_message', 'message_id', pm.id),
        now(),
        'pending'
    FROM comms.peer_messages pm
    WHERE pm.scheduled_for <= now()
    AND pm.scheduled_for IS NOT NULL
    AND pm.notification_sent IS NULL
    AND pm.outbound = true;

    -- Mark messages as processed
    UPDATE comms.peer_messages
    SET notification_sent = now()
    WHERE scheduled_for <= now()
    AND scheduled_for IS NOT NULL
    AND notification_sent IS NULL
    AND outbound = true;$$);
SELECT cron.schedule('Delete 90 day old users', '0 0 * * *', $$DO $body$
  DECLARE
    rows_deleted int;
  BEGIN
    DELETE FROM public.deleted_users
    WHERE deleted_at <= now() - interval '90 days';
    
    GET DIAGNOSTICS rows_deleted = ROW_COUNT;
    RAISE LOG 'purge-old-deleted-users: deleted % rows', rows_deleted;
  END
$body$;$$);