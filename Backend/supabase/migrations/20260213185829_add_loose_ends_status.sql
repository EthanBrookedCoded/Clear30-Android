-- Add "loose_ends" status for creators that have unresolved follow-ups or issues

ALTER TABLE marketing.creators
  DROP CONSTRAINT creators_status_check;

ALTER TABLE marketing.creators
  ADD CONSTRAINT creators_status_check CHECK (
    status = ANY (ARRAY[
      'sent'::text,
      'replied'::text,
      'followup'::text,
      'need_to_decline'::text,
      'declined'::text,
      'approved'::text,
      'sent_more_info'::text,
      'call_scheduled'::text,
      'contract_sent'::text,
      'guidelines_sent'::text,
      'references_sent'::text,
      'active'::text,
      'delayed'::text,
      'loose_ends'::text
    ])
  );
