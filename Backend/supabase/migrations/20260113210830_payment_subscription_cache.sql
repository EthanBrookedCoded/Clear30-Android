-- Create subscription_cache table in payment schema
CREATE TABLE IF NOT EXISTS "payment"."subscription_cache" (
    "user_id" TEXT PRIMARY KEY REFERENCES "public"."users"("id") ON DELETE CASCADE,
    "status" TEXT NOT NULL,
    "purchased_at" TIMESTAMPTZ NOT NULL,
    "expires_at" TIMESTAMPTZ,
    "adjust_id" TEXT,
    "last_event_ms" BIGINT NOT NULL,
    "trial_2h_sent" BOOLEAN DEFAULT FALSE NOT NULL,
    "created_at" TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    "updated_at" TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Index for efficient cron job querying
CREATE INDEX IF NOT EXISTS "idx_subscription_cache_trial_check" 
ON "payment"."subscription_cache" ("trial_2h_sent", "status", "purchased_at");

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION "payment"."update_updated_at_column"()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER "tr_subscription_cache_updated_at"
BEFORE UPDATE ON "payment"."subscription_cache"
FOR EACH ROW
EXECUTE FUNCTION "payment"."update_updated_at_column"();

-- RLS Policies (Locked down to service role by default)
ALTER TABLE "payment"."subscription_cache" ENABLE ROW LEVEL SECURITY;

-- Allow service role to do everything
CREATE POLICY "Service role can do everything on subscription_cache"
ON "payment"."subscription_cache"
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);
