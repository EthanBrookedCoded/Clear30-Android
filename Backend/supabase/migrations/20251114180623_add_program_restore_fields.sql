-- Add program restore fields to public.users table

-- Add new columns
ALTER TABLE public.users ADD COLUMN last_smoked TIMESTAMPTZ;
ALTER TABLE public.users ADD COLUMN your_why TEXT;
ALTER TABLE public.users ADD COLUMN custom_check_ins JSONB DEFAULT '[]'::jsonb;
ALTER TABLE public.users ADD COLUMN content_info JSONB DEFAULT '[]'::jsonb;
ALTER TABLE public.users ADD COLUMN program_breaks JSONB DEFAULT '[]'::jsonb;

-- Add comments for documentation
COMMENT ON COLUMN public.users.last_smoked IS 'Timestamp when user last smoked';
COMMENT ON COLUMN public.users.your_why IS 'User motivation text';
COMMENT ON COLUMN public.users.custom_check_ins IS 'Array of CustomCheckIn configs';
COMMENT ON COLUMN public.users.content_info IS 'Dict mapping dates to message schedules';
COMMENT ON COLUMN public.users.program_breaks IS 'Array of ProgramBreak metadata';

