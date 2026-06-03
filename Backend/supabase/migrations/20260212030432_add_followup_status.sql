ALTER TABLE marketing.creators DROP CONSTRAINT creators_status_check;

ALTER TABLE marketing.creators ADD CONSTRAINT creators_status_check
  CHECK (status = ANY (ARRAY[
    'sent', 'replied', 'followup', 'need_to_decline', 'declined',
    'approved', 'sent_more_info', 'call_scheduled', 'contract_sent',
    'guidelines_sent', 'references_sent', 'active', 'delayed'
  ]));
