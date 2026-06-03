-- Add mascot column to domain_allowlist for referral link generation
ALTER TABLE payment.domain_allowlist
ADD COLUMN mascot text DEFAULT ''::text;
