// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

const accountSid = Deno.env.get('TWILIO_ACCOUNT_SID')
const authToken = Deno.env.get('TWILIO_AUTH_TOKEN')

const hardStopMessage = 'You have been unsubscribed from Clear30 text messages.'
const softStopMessage = 'You have been paused from Clear30 text messages.'

// Supported country codes for SMS messaging
const supportedCountryCodes = [
  '+1',      // US and Canada
  '+1264',   // Anguilla
  '+1268',   // Antigua and Barbuda
  '+1242',   // Bahamas
  '+1246',   // Barbados
  '+1345',   // Cayman Islands
  '+1767',   // Dominica
  '+1441',   // Bermuda
  '+1849',   // Dominican Republic
  '+1809',   // Dominican Republic
  '+1473',   // Grenada
  '+1876',   // Jamaica
  '+1658',   // Jamaica
  '+1664',   // Montserrat
  '+1869',   // Saint Kitts and Nevis
  '+1787',   // Puerto Rico
  '+1758',   // Saint Lucia
  '+1784',   // Saint Vincent and the Grenadines
  '+1868',   // Trinidad and Tobago
  '+1649',   // Turks and Caicos Islands
  '+1340',   // US Virgin Islands
  '+1284',   // British Virgin Islands
];

Deno.serve(async (req) => {
  // 1. Create a Supabase client
  const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

  try {
    // 2. Get messages
    const messages = await getMessagesToSend(supabase)

    // 3. If no messages, return
    if (!messages || messages.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No messages to process' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // 4. Send messages
    const results = await sendMessages(supabase, messages)

    // 5. Return results
    return new Response(
      JSON.stringify({
        processed: results.length,
        results
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    console.error('Error:', error)
    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to process messages',
        details: error
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      }
    )
  }
})

async function getMessagesToSend(supabaseClient: ReturnType<typeof createClient>) {

  // Get time frame
  const now = new Date()
  const fiveSecondsFromNow = new Date(now.getTime() + 5000)

  // Fetch messages to send
  const { data: messagesToSend, error: fetchError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .select('*')
    .eq('outbound', true)
    .is('sent_at', null)
    .is('canceled', false)
    .lte('scheduled_for', fiveSecondsFromNow.toISOString())
    .order('scheduled_for', { ascending: true })
    .limit(9)

  if (fetchError) {
    throw fetchError
  }

  return messagesToSend
}

async function filterMessages(supabaseClient: ReturnType<typeof createClient>, messages: any[]) {
  const longMessages = messages.filter(message => message.text.length > 1500)

  if (longMessages.length > 0) {
    const { error } = await supabaseClient
      .schema('comms')
      .from('sms_messages')
      .update({ canceled: true })
      .in('id', longMessages.map(message => message.id))

    if (error) {
      throw error
    }
  }

  return messages.filter(message => message.text.length <= 1500)
}

async function sendMessages(supabaseClient: ReturnType<typeof createClient>, messages: any[]) {
  // Check credentials
  if (!accountSid || !authToken) {
    throw new Error('Missing Twilio credentials')
  }

  // Set up form defaults
  const basicFormData = new URLSearchParams()

  // SMS callback
  const statusFunction = "sms_handle_status"
  const statusCallbackUrl = `${Deno.env.get('SUPABASE_URL')}/functions/v1/${statusFunction}`
  basicFormData.append('StatusCallback', statusCallbackUrl)
  basicFormData.append('StatusCallbackEvent', ['delivered', 'undelivered', 'failed'].join(' '))

  // Set from messaging service
  const messagingServiceSid = Deno.env.get('TWILIO_MESSAGING_SERVICE_SID')
  if (messagingServiceSid) {
    basicFormData.append('MessagingServiceSid', messagingServiceSid as string)
  }

  // Set from number
  const fromNumber = Deno.env.get('TWILIO_FROM_NUMBER')
  if (fromNumber) {
    basicFormData.append('From', fromNumber as string)
  }

  // Make sure either messaging service or from number is set
  if (!messagingServiceSid && !fromNumber) {
    throw new Error('Either TWILIO_MESSAGING_SERVICE_SID or TWILIO_FROM_NUMBER must be set')
  }

  // Process messages sequentially with rate limiting
  const results: Array<{
    messageId: any;
    success: boolean;
    twilioId?: any;
    status?: any;
    error?: string;
  }> = []

  for (let i = 0; i < messages.length; i++) {
    const message = messages[i]

    try {
      // Get phone number
      const phoneNumber = message.phone_number

      // Validate phone number format and check if it's supported
      let formattedPhoneNumber = phoneNumber

      // If phone number doesn't start with +, add it for validation and Twilio
      if (!phoneNumber.startsWith('+')) {
        formattedPhoneNumber = '+' + phoneNumber
      }

      // Check if the country code is supported and get the matching country code
      const countryCode = supportedCountryCodes.find(code =>
        formattedPhoneNumber.startsWith(code)
      )

      if (!countryCode) {
        await setCanceled(supabaseClient, message)
        throw new Error(`Phone number country code not supported: ${phoneNumber}`)
      }

      // Remove country code and count remaining digits
      const digitsAfterCountryCode = formattedPhoneNumber.substring(countryCode.length).replace(/\D/g, '')

      if (digitsAfterCountryCode.length !== 10) {
        await setCanceled(supabaseClient, message)
        throw new Error(`Invalid phone number: ${phoneNumber} - expected 10 digits after country code, got ${digitsAfterCountryCode.length}`)
      }

      // Make sure the phone number is not blocked
      const { data: blocked, error: blockedError } = await supabaseClient
        .schema('comms')
        .from('sms_blocked')
        .select('id')
        .eq('phone_number', phoneNumber)
        .single()

      if (blocked) {
        await setCanceledAllForPhone(supabaseClient, phoneNumber)
        throw new Error(`Phone number is blocked: ${phoneNumber}`)
      }

      // Make sure the message is under 1600 characters
      if (message.text.length > 1600) {
        await setCanceled(supabaseClient, message)
        throw new Error(`Message is too long: ${message.text}`)
      }

      // Set form data
      const formData = new URLSearchParams(basicFormData)
      formData.append('To', phoneNumber)
      formData.append('Body', message.text)
      formData.append('SendAsMms', (message.text.length > 300).toString())

      // Create Basic Auth token
      const authString = btoa(`${accountSid}:${authToken}`)

      // Send request to Twilio API
      const response = await fetch(
        `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Messages.json`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Basic ${authString}`,
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: formData.toString()
        }
      )

      const result = await response.json()

      if (!response.ok) {
        throw new Error(result.message || 'Failed to send message')
      }

      // Update the message with sent_at timestamp and twilio_id
      await updateMessageStatus(supabaseClient, message, result)

      // Log the SMS event after successful send
      await logSmsEvent(supabaseClient, message)

      results.push({
        messageId: message.id,
        success: true,
        twilioId: result.sid,
        status: result.status
      })

      // Add 1-second delay between messages (except for the last message)
      if (i < messages.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000))
      }

    } catch (error) {
      await setCanceled(supabaseClient, message)
      results.push({
        messageId: message.id,
        success: false,
        error: error.message
      })

      // Still wait 1 second even if there was an error to maintain rate limiting
      if (i < messages.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
    }
  }

  return results
}

async function updateMessageStatus(supabaseClient: ReturnType<typeof createClient>, message: any, twilioResponse: any) {
  // Set sent at field
  const now = new Date()
  const { error: updateError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .update({
      sent_at: now.toISOString(),
      twilio_id: twilioResponse.sid
    })
    .eq('id', message.id)

  if (updateError) {
    throw updateError
  }

  // Insert initial status
  await supabaseClient
    .schema('comms')
    .from('sms_statuses')
    .insert({
      message_id: message.id,
      status: twilioResponse.status
    })

  // If message.text is unsubscribe, block the phone number (hard stop -> never re enable, soft stop -> re enable after signup)
  if (message.text === softStopMessage || message.text === hardStopMessage) {
    const { error: updateError } = await supabaseClient
      .schema('comms')
      .from('sms_blocked')
      .insert({ phone_number: message.phone_number, hard_stop: message.text === hardStopMessage })

    if (updateError) {
      console.error(`Error blocking phone number ${message.phone_number}:`, updateError)
    }
  }
}

function calculateSegments(text: string): { segments: number, encoding: string } {
  // Check if message contains non-GSM characters (emojis, smart quotes, etc.)
  const gsmRegex = /^[@£$¥èéùìòÇ\n\rØø\fÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !"#¤%&'()*+,\-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà]*$/

  const isGSM = gsmRegex.test(text)

  if (isGSM) {
    // GSM 03.38 encoding (7-bit)
    const singleSegmentLimit = 160
    const multiSegmentLimit = 153 // Due to concatenation header

    if (text.length <= singleSegmentLimit) {
      return { segments: 1, encoding: 'GSM' }
    } else {
      return {
        segments: Math.ceil(text.length / multiSegmentLimit),
        encoding: 'GSM'
      }
    }
  } else {
    // UCS-2 encoding (16-bit) for emojis, accented chars, etc.
    const singleSegmentLimit = 70
    const multiSegmentLimit = 67 // Due to concatenation header

    if (text.length <= singleSegmentLimit) {
      return { segments: 1, encoding: 'UCS-2' }
    } else {
      return {
        segments: Math.ceil(text.length / multiSegmentLimit),
        encoding: 'UCS-2'
      }
    }
  }
}

async function logSmsEvent(supabaseClient: ReturnType<typeof createClient>, message: any) {
  try {
    // Get the user's logging_id array from public.users
    const { data: userData, error: userError } = await supabaseClient
      .from('users')
      .select('logging_id')
      .eq('id', message.user_id)
      .single()

    if (userError) {
      console.error('Error fetching user logging_id:', userError)
      return
    }

    // Determine the user_id to use for events table
    let eventUserId = message.user_id // Default to actual user_id

    if (userData?.logging_id && Array.isArray(userData.logging_id) && userData.logging_id.length > 0) {
      // Use the last entry from the logging_id array
      eventUserId = userData.logging_id[userData.logging_id.length - 1]
    }

    // Calculate segments and determine message type
    const isMms = message.text.length > 300
    const segmentInfo = isMms ? { segments: 1, encoding: 'MMS' } : calculateSegments(message.text)

    // Prepare extra_data with type and message preview
    const extraData: any = {
      sms_message_id: message.id,
      message_preview: message.text.substring(0, 20),
      message_type: isMms ? 'mms' : 'sms',
      segments: segmentInfo.segments,
      encoding: segmentInfo.encoding
    }

    // Add type if it exists
    if (message.type) {
      extraData.type = message.type
    }

    // Log the event to public.events
    const { error: eventError } = await supabaseClient
      .from('events')
      .insert({
        user_id: eventUserId,
        event: 'received_sms_message',
        extra_data: extraData
      })

    if (eventError) {
      console.error('Error logging SMS event:', eventError)
    }

  } catch (error) {
    console.error('Unexpected error in logSmsEvent:', error)
    // Don't throw - logging failures shouldn't affect SMS sending
  }
}

async function setCanceled(supabaseClient: ReturnType<typeof createClient>, message: any) {
  const { error: updateError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .update({ canceled: true })
    .eq('id', message.id)
}

async function setCanceledAllForPhone(supabaseClient: ReturnType<typeof createClient>, phoneNumber: string) {
  const { error: updateError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .update({ canceled: true })
    .eq('phone_number', phoneNumber)
    .eq('outbound', true)
    .is('sent_at', null)
    .is('canceled', false)
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_send' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0'
*/
