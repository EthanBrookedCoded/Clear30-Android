import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

interface UserRecord {
  id: string
  name: string
  emoji: string
  platform?: string
  [key: string]: any
}

interface WebhookPayload {
  type: 'INSERT'
  table: 'users'
  record: UserRecord
  schema: 'public'
}

const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

Deno.serve(async (req) => {
  try {
    const payload: WebhookPayload = await req.json()
    console.log(`Processing new user: ${payload.record.id}`)

    // Only process iOS users
    if (payload.record.platform !== 'ios') {
      console.log(`Skipping non-iOS user: ${payload.record.id} (platform: ${payload.record.platform})`)
      return new Response(
        JSON.stringify({ message: 'User processed - not iOS, skipping downsell campaign' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Call the downsell schedule function
    console.log(`Scheduling downsell messages for iOS user: ${payload.record.id}`)

    const downsellResponse = await fetch(`${supabaseURL}/functions/v1/sms_downsell_schedule`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${supabaseServiceRoleKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ user_id: payload.record.id })
    })

    const downsellResult = await downsellResponse.json()

    if (!downsellResponse.ok) {
      console.error('Failed to schedule downsell messages:', downsellResult)
      return new Response(
        JSON.stringify({
          message: 'User processed - error scheduling downsell messages',
          error: downsellResult
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    console.log('Successfully scheduled downsell messages:', downsellResult)

    return new Response(
      JSON.stringify({
        message: 'User processed - downsell messages scheduled',
        downsell_result: downsellResult
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Error processing new user webhook:', error)
    return new Response(
      JSON.stringify({
        message: 'Error processing user webhook',
        error: error.message
      }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make sure downsell campaign is active:
     INSERT INTO library.campaigns (id, active) VALUES ('downsell', true);
  3. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/user_handle_new' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "type": "INSERT",
      "table": "users", 
      "record": {
        "id": "test_user_123",
        "name": "Test User",
        "emoji": "😊",
        "platform": "ios"
      },
      "schema": "public"
    }'

  Test with Android user (should skip):
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/user_handle_new' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "type": "INSERT",
      "table": "users",
      "record": {
        "id": "test_user_456", 
        "name": "Android User",
        "emoji": "🤖",
        "platform": "android"
      },
      "schema": "public"
    }'

*/
