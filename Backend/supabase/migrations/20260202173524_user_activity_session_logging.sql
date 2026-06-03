-- User Session Activity Logging
-- Tracks when users are active based on day_info updates

-------------------------------------------
-- 1. Create Table: user_sessions
-------------------------------------------

CREATE TABLE public.user_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    session_start TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Index for CASCADE deletes when user is removed
CREATE INDEX idx_user_sessions_user_id ON public.user_sessions(user_id);

-- RLS disabled for all except service role
ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;

-------------------------------------------
-- 2. Trigger Function: Log Session Activity
-------------------------------------------

CREATE OR REPLACE FUNCTION public.log_user_session_activity()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Only log if day_info actually changed
    IF OLD.day_info IS DISTINCT FROM NEW.day_info THEN
        INSERT INTO public.user_sessions (user_id, metadata)
        VALUES (
            NEW.id,
            jsonb_build_object(
                'trigger', 'day_info_update'
            )
        );
    END IF;

    RETURN NEW;
END;
$$;

-------------------------------------------
-- 3. Create Trigger on Users Table
-------------------------------------------

CREATE TRIGGER after_user_day_info_update
    AFTER UPDATE OF day_info ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION public.log_user_session_activity();
