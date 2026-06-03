# Basics
supabase init
supabase login

# Link to project
supabase link --project-ref quluipmdicjsolnsopkg

supabase start
supabase stop
supabase stop --no-backup



# Pull from remote (updates migration files)
supabase db pull

# Clean local database
supabase db reset



# New migration
supabase migration new NAME

# New migration from changes in supabase through ui
supabase db diff -f NAME

# Push migration to remote
supabase db push




# Set secrets
supabase secrets set NAME=VALUE

# Download functions
supabase functions download NAME

# New function
supabase functions new NAME

# Serve function locally
supabase functions serve NAME --env-file .env

# Deploy function to remote
supabase functions deploy NAME




# Dumping the database schema
supabase db dump -f schema.sql

# Dumping the database data
supabase db dump -f data.sql --data-only


# Push local config to supabase
supabase config push