-- LOCAL DEV ONLY: do not log analytics events when running against local Supabase.
--
-- The schema migrations create `amplitude_forward_*` triggers that fire an HTTP
-- request to the PRODUCTION edge-function URL
-- (https://quluipmdicjsolnsopkg.supabase.co/functions/v1/amplitude_send_*) on every
-- insert. That means even a purely local stack forwards real events into Amplitude.
--
-- Seeds run after migrations on `supabase db reset` and are never shipped by
-- `supabase db push`, so dropping the triggers here disables Amplitude forwarding
-- locally without touching production. Re-running `db reset` keeps them disabled.

DROP TRIGGER IF EXISTS amplitude_forward_event ON public.events;
DROP TRIGGER IF EXISTS amplitude_forward_assignment ON experiments.user_assignments;
DROP TRIGGER IF EXISTS amplitude_forward_assessment_response ON programs.program_assessment_responses;
