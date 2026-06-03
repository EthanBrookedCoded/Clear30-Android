-- Drop the existing journal_entries table
DROP TABLE IF EXISTS public.journal_entries CASCADE;

-- Recreate with auto-incrementing integer ID
CREATE TABLE public.journal_entries (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    is_video BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_journal_entries_user_id ON public.journal_entries(user_id);
CREATE INDEX idx_journal_entries_created_at ON public.journal_entries(created_at);

-- Enable Row Level Security (RLS)
ALTER TABLE public.journal_entries ENABLE ROW LEVEL SECURITY;

-- Policies for RLS
CREATE POLICY "Users can view their own journal entries"
ON public.journal_entries FOR SELECT
USING (user_id = (select get_user_id()));

CREATE POLICY "Users can insert their own journal entries"
ON public.journal_entries FOR INSERT
WITH CHECK (user_id = (select get_user_id()));

CREATE POLICY "Users can update their own journal entries"
ON public.journal_entries FOR UPDATE
USING (user_id = (select get_user_id()));

CREATE POLICY "Users can delete their own journal entries"
ON public.journal_entries FOR DELETE
USING (user_id = (select get_user_id()));

-- Trigger to call the update_updated_at_column function
CREATE TRIGGER update_journal_entries_updated_at
BEFORE UPDATE ON public.journal_entries
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

