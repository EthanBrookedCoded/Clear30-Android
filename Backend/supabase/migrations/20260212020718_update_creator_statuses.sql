-- Update creator statuses to match the new outreach workflow
-- Old statuses: reached_out, sent_cal_link, call_scheduled, negotiating, not_interested, future, closed
-- New statuses: sent, replied, need_to_decline, declined, approved, sent_more_info, call_scheduled, contract_sent, guidelines_sent, references_sent, active, delayed

BEGIN;

-- 1. Migrate existing data
UPDATE marketing.creators SET status = 'sent' WHERE status = 'reached_out';
UPDATE marketing.creators SET status = 'declined' WHERE status = 'not_interested';
UPDATE marketing.creators SET status = 'approved' WHERE status = 'negotiating';
UPDATE marketing.creators SET status = 'delayed' WHERE status = 'future';
UPDATE marketing.creators SET status = 'active' WHERE status = 'closed';
UPDATE marketing.creators SET status = 'sent' WHERE status = 'sent_cal_link';

-- 2. Also update status change history
UPDATE marketing.creator_status_changes SET old_status = 'sent' WHERE old_status = 'reached_out';
UPDATE marketing.creator_status_changes SET new_status = 'sent' WHERE new_status = 'reached_out';
UPDATE marketing.creator_status_changes SET old_status = 'declined' WHERE old_status = 'not_interested';
UPDATE marketing.creator_status_changes SET new_status = 'declined' WHERE new_status = 'not_interested';
UPDATE marketing.creator_status_changes SET old_status = 'approved' WHERE old_status = 'negotiating';
UPDATE marketing.creator_status_changes SET new_status = 'approved' WHERE new_status = 'negotiating';
UPDATE marketing.creator_status_changes SET old_status = 'delayed' WHERE old_status = 'future';
UPDATE marketing.creator_status_changes SET new_status = 'delayed' WHERE new_status = 'future';
UPDATE marketing.creator_status_changes SET old_status = 'active' WHERE old_status = 'closed';
UPDATE marketing.creator_status_changes SET new_status = 'active' WHERE new_status = 'closed';
UPDATE marketing.creator_status_changes SET old_status = 'sent' WHERE old_status = 'sent_cal_link';
UPDATE marketing.creator_status_changes SET new_status = 'sent' WHERE new_status = 'sent_cal_link';

-- 3. Drop old CHECK constraint and add new one
ALTER TABLE marketing.creators DROP CONSTRAINT creators_status_check;
ALTER TABLE marketing.creators ADD CONSTRAINT creators_status_check CHECK (
  status = ANY (ARRAY[
    'sent'::text,
    'replied'::text,
    'need_to_decline'::text,
    'declined'::text,
    'approved'::text,
    'sent_more_info'::text,
    'call_scheduled'::text,
    'contract_sent'::text,
    'guidelines_sent'::text,
    'references_sent'::text,
    'active'::text,
    'delayed'::text
  ])
);

-- 4. Update the default status
ALTER TABLE marketing.creators ALTER COLUMN status SET DEFAULT 'sent';

COMMIT;
