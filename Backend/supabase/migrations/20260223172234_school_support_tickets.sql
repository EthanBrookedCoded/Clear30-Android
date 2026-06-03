-- 1. Create support_tickets table
CREATE TABLE schools.support_tickets (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  school_id text NOT NULL REFERENCES schools.schools(school_id),
  user_id bigint NOT NULL,
  user_email text NOT NULL,
  user_name text NOT NULL,
  category text NOT NULL,
  subject text NOT NULL,
  message text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE schools.support_tickets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can insert tickets"
  ON schools.support_tickets FOR INSERT
  TO authenticated
  WITH CHECK (true);

CREATE POLICY "Users can read their own tickets"
  ON schools.support_tickets FOR SELECT
  TO authenticated
  USING (user_id IN (
    SELECT id FROM schools.portal_users WHERE auth_id = auth.uid()
  ));

-- 2. Drop role CHECK constraint to allow free-text roles
ALTER TABLE schools.portal_users DROP CONSTRAINT portal_users_role_check;

-- 3. Fix UMich long_name (has newline character)
UPDATE schools.schools
SET long_name = 'University of Michigan Ann Arbor'
WHERE school_id = 'umich';
