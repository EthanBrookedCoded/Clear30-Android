-- Drop the maybe_follow_up column
ALTER TABLE marketing.creators 
DROP COLUMN IF EXISTS maybe_follow_up;

-- Drop the old status constraint
ALTER TABLE marketing.creators 
DROP CONSTRAINT IF EXISTS creators_status_check;

-- Add new status constraint with "maybe_follow_up" added and "approved"/"need_to_decline" removed
ALTER TABLE marketing.creators 
ADD CONSTRAINT creators_status_check 
CHECK (status = ANY (ARRAY[
  'sent'::text,
  'replied'::text,
  'maybe_follow_up'::text,
  'followup'::text,
  'declined'::text,
  'sent_more_info'::text,
  'call_scheduled'::text,
  'contract_sent'::text,
  'guidelines_sent'::text,
  'references_sent'::text,
  'active'::text,
  'delayed'::text,
  'loose_ends'::text
]));
