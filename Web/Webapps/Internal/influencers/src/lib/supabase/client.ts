import { createClient as createSupabaseClient } from "@supabase/supabase-js";

const supabase = createSupabaseClient(
  import.meta.env.VITE_SUPABASE_URL,
  import.meta.env.VITE_SUPABASE_ANON_KEY,
  {
    db: { schema: "marketing" },
  },
);

export function createClient() {
  return supabase;
}
