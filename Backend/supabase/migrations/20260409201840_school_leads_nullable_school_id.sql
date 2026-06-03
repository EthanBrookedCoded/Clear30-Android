-- Make school_id nullable for domains in the allowlist that aren't linked to a school yet
ALTER TABLE schools.school_leads ALTER COLUMN school_id DROP NOT NULL;

-- Replace the old unique constraint with a functional index that handles NULL school_id
ALTER TABLE schools.school_leads DROP CONSTRAINT school_leads_school_id_email_key;

CREATE UNIQUE INDEX school_leads_school_id_email_idx
  ON schools.school_leads (COALESCE(school_id, ''), email);
