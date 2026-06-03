// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

interface TwilioStatusCallback {
  MessageSid: string
  MessageStatus: string
  To: string
  From?: string
  ErrorCode?: string
  ErrorMessage?: string
}

serve(async (req) => {
  try {
    // Create Supabase client
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { 
        auth: {
          persistSession: false
        }
      }
    )

    // Parse the form data from Twilio
    const formData = await req.formData()
    
    const twilioStatus: TwilioStatusCallback = {
      MessageSid: formData.get('MessageSid') as string,
      MessageStatus: formData.get('MessageStatus') as string,
      To: formData.get('To') as string,
      From: formData.get('From') as string,
      ErrorCode: formData.get('ErrorCode') as string,
      ErrorMessage: formData.get('ErrorMessage') as string,
    }

    // First, find the message in our database using the Twilio ID
    const { data: message, error: messageError } = await supabaseClient
      .schema('comms')
      .from('sms_messages')
      .select('id')
      .eq('twilio_id', twilioStatus.MessageSid)
      .single()

    if (messageError || !message) {
      throw new Error(`Message not found for Twilio ID: ${twilioStatus.MessageSid}`)
    }

    // Handle blocked numbers (error code 21610)
    if (twilioStatus.ErrorCode === '21610') {
      // Format phone number to 1XXXXXXXXXX format
      const formattedNumber = twilioStatus.To.replace(/[^0-9]/g, '')
      
      // Insert into blocked numbers table
      const { error: blockError } = await supabaseClient
        .schema('comms')
        .from('sms_blocked')
        .insert({
          phone_number: formattedNumber,
          hard_stop: true
        })

      if (blockError) {
        console.error('Failed to add number to blocked list:', blockError)
      }
    }

    // Insert the status update
    const { error: statusError } = await supabaseClient
      .schema('comms')
      .from('sms_statuses')
      .insert({
        message_id: message.id,
        status: twilioStatus.MessageStatus,
      })

    if (statusError) {
      throw statusError
    }

    // Return a success response to Twilio
    return new Response(
      JSON.stringify({ 
        success: true,
        messageId: message.id,
        status: twilioStatus.MessageStatus 
      }),
      { 
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    console.error('Webhook Error:', error)
    
    // Return error response
    return new Response(
      JSON.stringify({ 
        error: error.message || 'Failed to process status update',
        details: error
      }),
      { 
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      }
    )
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_handle_status' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'

*/
