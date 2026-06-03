drop trigger if exists "amplitude_forward_assessment_response" on "programs"."program_assessment_responses";

CREATE TRIGGER amplitude_forward_assessment_response AFTER INSERT ON programs.program_assessment_responses FOR EACH ROW EXECUTE FUNCTION supabase_functions.http_request('https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_assessment_response', 'POST', '{"Content-type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTcyNDY5NTgzNywiZXhwIjoyMDQwMjcxODM3fQ.UQ4uafnOA_nMELdw05JcySLuBqDIrOcQCrWzFNFR9FY"}', '{}', '5000');
ALTER TABLE "programs"."program_assessment_responses" DISABLE TRIGGER "amplitude_forward_assessment_response";


