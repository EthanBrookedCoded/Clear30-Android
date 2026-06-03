// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const ADJUST_APP_TOKEN = Deno.env.get('ADJUST_APP_TOKEN') || 'PLACEHOLDER_APP_TOKEN'
const ADJUST_EVENT_TOKEN_2H = Deno.env.get('ADJUST_EVENT_TOKEN_2H') || 'PLACEHOLDER_EVENT_TOKEN'

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
)

serve(async (req) => {
  try {
    console.log('Running 2-hour trial check cron...')

    // 1. Find users who started a trial > 2 hours ago and haven't had the event sent
    // We look for status = 'trialing' to ensure they haven't cancelled
    const { data: pendingTrials, error: fetchError } = await supabase
      .schema('payment')
      .from('subscription_cache')
      .select('*')
      .eq('status', 'trialing')
      .eq('trial_2h_sent', false)
      .lt('purchased_at', new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString())

    if (fetchError) {
      throw new Error(`Failed to fetch pending trials: ${fetchError.message}`)
    }

    console.log(`Found ${pendingTrials?.length || 0} pending trials to process`)

    interface ProcessResult {
      user_id: string
      success: boolean
      error?: string
    }

    const results: ProcessResult[] = []

    for (const trial of pendingTrials || []) {
      const { user_id, adjust_id, purchased_at } = trial

      try {
        // 2. Send to Adjust if we have an adjust_id
        if (adjust_id) {
          console.log(`Sending Adjust S2S event for user ${user_id} (adid: ${adjust_id})`)

          const adjustParams = new URLSearchParams({
            s2s: '1',
            app_token: ADJUST_APP_TOKEN,
            event_token: ADJUST_EVENT_TOKEN_2H,
            adid: adjust_id,
            created_at_unix: Math.floor(new Date(purchased_at).getTime() / 1000 + 2 * 60 * 60).toString()
          })

          const adjustResponse = await fetch(`https://s2s.adjust.com/event?${adjustParams.toString()}`, {
            method: 'POST'
          })

          if (!adjustResponse.ok) {
            const errorText = await adjustResponse.text()
            console.error(`Adjust API error for user ${user_id}: ${errorText}`)
          } else {
            console.log(`Adjust event sent successfully for user ${user_id}`)
          }
        } else {
          console.warn(`No Adjust ID found for user ${user_id}, skipping Adjust event`)
        }

        // 3. Log to public.events (Amplitude)
        // We set the timestamp to exactly 2 hours after purchase
        const eventTimestamp = new Date(new Date(purchased_at).getTime() + 2 * 60 * 60 * 1000).toISOString()

        const { error: eventError } = await supabase
          .from('events')
          .insert({
            user_id: user_id,
            event: 'trial_active_2h',
            timestamp: eventTimestamp,
            extra_data: {
              source: 'trial_2h_cron',
              adjust_id: adjust_id || null,
              original_purchased_at: purchased_at
            }
          })

        if (eventError) {
          console.error(`Failed to log event for user ${user_id}:`, eventError)
        }

        // 4. Mark as sent in cache
        const { error: updateError } = await supabase
          .schema('payment')
          .from('subscription_cache')
          .update({ trial_2h_sent: true })
          .eq('user_id', user_id)

        if (updateError) {
          console.error(`Failed to update cache for user ${user_id}:`, updateError)
        }

        results.push({ user_id, success: true })
      } catch (itemError) {
        console.error(`Error processing user ${user_id}:`, itemError)
        results.push({ user_id, success: false, error: itemError.message })
      }
    }

    return new Response(JSON.stringify({
      success: true,
      processed: results.length,
      details: results
    }), {
      headers: { 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('Error in trial_2h_cron:', error)
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


  Sandbox:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_unsub' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0'

  Production:
  
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_unsub' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0'

*/
