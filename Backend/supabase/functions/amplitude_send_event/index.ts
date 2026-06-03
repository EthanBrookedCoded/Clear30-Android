// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const AMPLITUDE_API_KEY = Deno.env.get('AMPLITUDE_API_KEY') ?? ''

interface EventPayload {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  schema: string
  record: {
    id: number
    user_id: string
    timestamp: string
    event: string
    extra_data: Record<string, unknown> | null
  }
  old_record: null | Record<string, unknown>
}

serve(async (req) => {
  try {
    const payload: EventPayload = await req.json()
    
    // Only process INSERT events on the events table
    if (payload.type !== 'INSERT' || payload.schema !== 'public' || payload.table !== 'events') {
      return new Response(JSON.stringify({ 
        success: false, 
        error: 'Not a relevant event' 
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    // Transform the event into Amplitude format
    const amplitudeEvent = {
      api_key: AMPLITUDE_API_KEY,
      events: [{
        user_id: payload.record.user_id,
        event_type: payload.record.event,
        time: new Date(payload.record.timestamp).getTime(),
        event_properties: payload.record.extra_data || {}
      }]
    }

    // Send to Amplitude
    const response = await fetch('https://api2.amplitude.com/2/httpapi', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': '*/*'
      },
      body: JSON.stringify(amplitudeEvent)
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
    console.error('Error processing event:', error)
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

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/amplitude_send_event' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'

*/
