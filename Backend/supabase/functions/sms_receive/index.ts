// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

// Twilio specific opt out keywords
// Once received, we can never send messages to the user again, they will throw error
const stopKeywordsHard = ['CANCEL', 'END', 'QUIT', 'UNSUBSCRIBE', 'STOP', 'STOPALL']
const hardStopMessage = 'You have been unsubscribed from Clear30 text messages.'

// Keywords that we have defined, we can still send messages to the user after
// So we unblock when going through signup process again
const stopKeywordsSoft = ['PAUSE', 'ERASE', 'DELETE']
const softStopMessage = 'You have been paused from Clear30 text messages.'

interface TwilioIncomingMessage {
  MessageSid: string
  From: string
  To: string
  Body: string
}

Deno.serve(async (req) => {
  try {
    const supabaseClient = createClient(
      supabaseURL,
      supabaseServiceRoleKey,
      {
        auth: {
          persistSession: false
        }
      }
    )

    // 1. Create new request with the correct content type and the saved body
    const bodyContent = await req.text();
    const newRequest = new Request(req.url, {
      method: req.method,
      headers: {
        'content-type': 'application/x-www-form-urlencoded'
      },
      body: bodyContent
    });

    // 2. Parse the form data from the new request
    const formData = await newRequest.formData();
    const incomingMessage: TwilioIncomingMessage = {
      MessageSid: formData.get('MessageSid') as string,
      From: formData.get('From') as string,
      To: formData.get('To') as string,
      Body: formData.get('Body') as string
    }

    // 3. Get user from phone number

    // Make digits only
    let finalPhoneNumber = incomingMessage.From.replace(/\D/g, '')

    // Get user from digits only phone number
    let { data: user, error: userError } = await supabaseClient
      .from('users')
      .select('id, name, logging_id')
      .eq('phone_number', finalPhoneNumber)
      .single()

    // If no user found, try with '+' in front
    if (userError && userError.code === 'PGRST116') { // PGRST116 is "not found" error
      // Add '+' to phone number
      const plusPrefixedPhoneNumber = `+${finalPhoneNumber}`
      const result = await supabaseClient
        .from('users')
        .select('id, name, logging_id')
        .eq('phone_number', plusPrefixedPhoneNumber)
        .single()

      // Set user and user error
      user = result.data
      userError = result.error

      // If we found user, update final phone number to plus prefixed phone number
      // We do this for consistent logging
      if (result.data) {
        finalPhoneNumber = plusPrefixedPhoneNumber
      }
    }

    if (userError && userError.code !== 'PGRST116') {
      console.error('Error looking up user:', userError)
    }

    // 4. Store the incoming message in the database
    const { data: message, error: insertError } = await supabaseClient
      .schema('comms')
      .from('sms_messages')
      .insert({
        phone_number: finalPhoneNumber,
        text: incomingMessage.Body,
        outbound: false,
        scheduled_for: new Date().toISOString(),
        twilio_id: incomingMessage.MessageSid,
        sent_at: new Date().toISOString(),
        user_id: user?.id || null
      })
      .select()
      .single()

    if (insertError) {
      throw insertError
    }

    // 5. Insert received status
    const { error: statusError } = await supabaseClient
      .schema('comms')
      .from('sms_statuses')
      .insert({
        message_id: message.id,
        status: 'received'
      })

    if (statusError) {
      throw statusError
    }

    // 6. Log the SMS event after successful receive
    await logSmsEvent(supabaseClient, message, user)

    // 7. Check for stop messages
    let trimmedBody = incomingMessage.Body.toUpperCase().trim()
    const isStopMessage = stopKeywordsHard.includes(trimmedBody) || stopKeywordsSoft.includes(trimmedBody)

    // 8. Send Slack notification (skip if it's a stop message)
    if (!isStopMessage) {
      await createSlackNotification(supabaseClient, message, user)
    }

    // 9. Handle stop messages
    if (stopKeywordsHard.includes(trimmedBody)) {
      await handleStop(supabaseClient, finalPhoneNumber, user?.id, true);
    } else if (stopKeywordsSoft.includes(trimmedBody)) {
      await handleStop(supabaseClient, finalPhoneNumber, user?.id, false);
    }

    // 10. Return empty TwiML response to Twilio
    return new Response(
      '<?xml version="1.0" encoding="UTF-8"?><Response></Response>',
      {
        status: 200,
        headers: {
          'Content-Type': 'text/xml'
        }
      }
    )

  } catch (error) {
    console.error('Webhook Error:', error)

    return new Response(
      '<?xml version="1.0" encoding="UTF-8"?><Response></Response>',
      {
        status: 500,
        headers: {
          'Content-Type': 'text/xml'
        }
      }
    )
  }
})

async function createSlackNotification(supabaseClient: ReturnType<typeof createClient>, message: any, user: any) {
  try {
    // Create metadata with SMS message details
    const timestamp = new Date(message.sent_at).toLocaleString()
    const metadata = {
      message_id: message.id,
      user_id: user?.id || 'Unknown',
      phone_number: message.phone_number,
      received_at: timestamp,
      blocks: [
        {
          type: "header",
          text: {
            type: "plain_text",
            text: "📱 New SMS Received"
          }
        },
        {
          type: "section",
          fields: [
            {
              type: "mrkdwn",
              text: `*User:* ${user?.name || 'Unknown'}`
            },
            {
              type: "mrkdwn",
              text: `*User ID:* ${user?.id || 'Unknown'}`
            },
            {
              type: "mrkdwn",
              text: `*Received at:* ${timestamp}`
            }
          ]
        },
        {
          type: "section",
          text: {
            type: "mrkdwn",
            text: `*Message:*\n${message.text}`
          }
        },
        {
          type: "actions",
          elements: [
            {
              type: "button",
              text: {
                type: "plain_text",
                text: "View in Panel"
              },
              url: "https://clear30.org/panel"
            }
          ]
        }
      ]
    }

    // Insert into slack_notifications table
    const { data, error } = await supabaseClient
      .schema('comms')
      .from('slack_notifications')
      .insert({
        channel_type: 'sms',
        title: 'New SMS Received',
        message: message.text,
        metadata: metadata,
        status: 'pending',
        priority: 'normal'
      })
      .select()
      .single()

    if (error) {
      console.error('Failed to create Slack notification:', error.message)
      return {
        success: false,
        error: error.message
      }
    }

    return {
      success: true,
      notification_id: data.id
    }
  } catch (error) {
    console.error('Error creating Slack notification:', error)
    return {
      success: false,
      error: error.message
    }
  }
}

async function handleStop(supabaseClient, finalPhoneNumber, userID, hardStop = false) {

  // Cancel all future messages
  const { error: stopError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .update({ canceled: true })
    .eq('phone_number', finalPhoneNumber)
    .gt('scheduled_for', new Date().toISOString())

  if (stopError) {
    throw stopError
  }

  // Send confirmation message
  // In sms send function, once message is sent, we set as blocked
  const { error: messageError } = await supabaseClient
    .schema('comms')
    .from('sms_messages')
    .insert({
      phone_number: finalPhoneNumber,
      text: hardStop ? hardStopMessage : softStopMessage,
      outbound: true,
      scheduled_for: new Date().toISOString(),
      user_id: userID
    })

  if (messageError) {
    throw messageError
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

async function logSmsEvent(supabaseClient: ReturnType<typeof createClient>, message: any, userData: any) {
  try {
    // Skip logging if no user data (user not found)
    if (!userData) {
      return
    }

    // Determine the user_id to use for events table
    let eventUserId = userData.id

    if (userData?.logging_id && Array.isArray(userData.logging_id) && userData.logging_id.length > 0) {
      // Use the last entry from the logging_id array
      eventUserId = userData.logging_id[userData.logging_id.length - 1]
    }

    // Calculate segments for incoming message
    const segmentInfo = calculateSegments(message.text)

    // Prepare extra_data with message preview and segment info
    const extraData: any = {
      sms_message_id: message.id,
      message_preview: message.text.substring(0, 20),
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
        event: 'sent_sms_message',
        extra_data: extraData
      })

    if (eventError) {
      console.error('Error logging SMS event:', eventError)
    }

  } catch (error) {
    console.error('Unexpected error in logSmsEvent:', error)
    // Don't throw - logging failures shouldn't affect SMS receiving
  }
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_receive' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"name":"Functions"}'


  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_receive' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'MessageSid=MGXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX' \
    --data-urlencode 'From=+9142467144' \
    --data-urlencode 'To=+18449723883' \
    --data-urlencode 'Body=Test message'

*/
