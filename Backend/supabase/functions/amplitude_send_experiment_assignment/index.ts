// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const AMPLITUDE_API_KEY = Deno.env.get('AMPLITUDE_API_KEY') ?? ''

interface AssignmentPayload {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  schema: string
  record: {
    user_id: string        // This IS the logging_id (iOS passes loggingID as p_user_id)
    experiment_id: string
    variant: string
    payload: Record<string, unknown> | null
    assigned_at: string
  }
  old_record: null | Record<string, unknown>
}

serve(async (req) => {
  try {
    const payload: AssignmentPayload = await req.json()

    // Only process INSERT events on user_assignments
    if (payload.type !== 'INSERT' ||
        payload.schema !== 'experiments' ||
        payload.table !== 'user_assignments') {
      return new Response(JSON.stringify({ success: false, error: 'Not relevant' }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    // Skip "none" assignments (user not enrolled in experiment)
    if (payload.record.variant === 'none') {
      return new Response(JSON.stringify({ success: true, skipped: true }), {
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // user_id in experiments.user_assignments IS the logging_id
    const loggingId = payload.record.user_id

    // Set user property: experiment_<experiment_id> = <variant>
    const userProperties = {
      [`experiment_${payload.record.experiment_id}`]: payload.record.variant
    }

    const amplitudeIdentify = {
      api_key: AMPLITUDE_API_KEY,
      identification: [{
        user_id: loggingId,
        user_properties: { "$set": userProperties }
      }]
    }

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

    return new Response(JSON.stringify({ success: true }), {
      headers: { 'Content-Type': 'application/json' }
    })
  } catch (error) {
    console.error('Error processing experiment assignment:', error)
    return new Response(JSON.stringify({ success: false, error: error.message }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500
    })
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/amplitude_send_experiment_assignment' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "type": "INSERT",
      "table": "user_assignments",
      "schema": "experiments",
      "record": {
        "user_id": "test-logging-id",
        "experiment_id": "video-testimonial-welcome",
        "variant": "show",
        "payload": null,
        "assigned_at": "2024-01-20T12:34:56Z"
      },
      "old_record": null
    }'

*/
