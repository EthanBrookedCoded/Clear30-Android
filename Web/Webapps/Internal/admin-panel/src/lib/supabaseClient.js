import { createClient } from '@supabase/supabase-js'
import { SUPABASE_CONFIG, QA_MODE } from './config'

// Get current Supabase configuration based on QA mode
const config = QA_MODE ? SUPABASE_CONFIG.qa : SUPABASE_CONFIG.production

// Create Supabase client with current configuration
export const supabase = createClient(config.url, config.anonKey)

