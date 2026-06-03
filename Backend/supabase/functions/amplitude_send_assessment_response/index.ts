// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
)

const AMPLITUDE_API_KEY = Deno.env.get('AMPLITUDE_API_KEY') ?? ''
const EXCLUDED_USERS = [
  '9142467144',
  '7C4C513AA8994A308250C8E64261F80F',
  '9178640066',
  '9175320623'
];

interface AssessmentPayload {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  schema: string
  record: {
    id: number
    user_id: string
    assessment: string
    responses: Record<string, string | string[]>
    timestamp: string
  }
  old_record: null | Record<string, unknown>
}

serve(async (req) => {
  try {
    const payload: AssessmentPayload = await req.json()

    // Only process INSERT events on the program_assessment_responses table
    if (payload.type !== 'INSERT' ||
      payload.schema !== 'programs' ||
      payload.table !== 'program_assessment_responses') {
      return new Response(JSON.stringify({
        success: false,
        error: 'Not a relevant event'
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    const userID = payload.record.user_id

    // Exclude specific users
    if (EXCLUDED_USERS.includes(userID)) {

      console.log("User excluded", userID);

      return new Response(JSON.stringify({
        success: false,
        error: 'User excluded'
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    // Transform responses into property with composite key
    const userProperties = {
      [`assessment_${payload.record.assessment}`]: payload.record.responses
    }

    // Get logging id and appstack attribution from public.users table
    const { data } = await supabase
      .from('users')
      .select('logging_id, appstack_attribution')
      .eq('id', userID)
      .single()

    // Check if logging id is an array and has at least one element
    if (!data || !data.logging_id || data.logging_id.length === 0) {

      console.log("No logging id found for user", userID);

      return new Response(JSON.stringify({
        success: false,
        error: 'No logging id found'
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    // Get logging ID
    const loggingIds = data.logging_id as string[]
    const loggingId = loggingIds[loggingIds.length - 1]

    // Add appstack attribution if available
    if (data.appstack_attribution) {
      const attribution = data.appstack_attribution as Record<string, string>
      if (attribution.appstack_ad) userProperties['appstack_ad'] = attribution.appstack_ad
      if (attribution.appstack_adset) userProperties['appstack_adset'] = attribution.appstack_adset
      if (attribution.appstack_campaign) userProperties['appstack_campaign'] = attribution.appstack_campaign
      if (attribution.appstack_adnetwork) userProperties['appstack_adnetwork'] = attribution.appstack_adnetwork
    }

    // Send to Amplitude as a user property update
    // We'll use the Amplitude Identify API for this
    const amplitudeIdentify = {
      api_key: AMPLITUDE_API_KEY,
      identification: [{
        user_id: loggingId,
        user_properties: {
          "$set": userProperties
        }
      }]
    }

    // Convert to URL encoded format
    const urlEncodedData = new URLSearchParams({
      'api_key': AMPLITUDE_API_KEY,
      'identification': JSON.stringify(amplitudeIdentify.identification)
    }).toString()

    const response = await fetch('https://api2.amplitude.com/identify', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Accept': '*/*'
      },
      body: urlEncodedData
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Amplitude API error: ${response.status} ${errorText}`)
    }

    return new Response(JSON.stringify({
      success: true,
      amplitudeStatus: response.status
    }), {
      headers: { 'Content-Type': 'application/json' }
    })
  } catch (error) {

    console.error('Error processing assessment:', error)

    return new Response(JSON.stringify({
      success: false,
      error: error.message
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500
    })
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

    curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/amplitude_send_assessment_response' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{
    "type": "INSERT",
    "table": "program_assessment_responses",
    "schema": "programs",
    "record": {
      "id": 1234,
      "user_id": "9142467144",
      "assessment": "clear30",
      "responses": {
        "LO-Age": ["18-20"]
      },
      "timestamp": "2024-01-20T12:34:56Z"
    },
    "old_record": null
  }'

*/
