-- Allow null values for reasons and impact to support partial submissions
-- When user selects school and clicks next, we create record with null reasons/impact
-- When they complete the form, we update with actual values

ALTER TABLE schools.leaderboard_requests
    ALTER COLUMN reasons DROP NOT NULL,
    ALTER COLUMN impact DROP NOT NULL;
