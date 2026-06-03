
import { createClient } from 'npm:@supabase/supabase-js@2'
import { JWT } from 'npm:google-auth-library@9'

// FIREBASE_SERVICE_ACCOUNT_JSON_B64_ENC: base64 encoded service account JSON
const serviceAccountBase64 = Deno.env.get('FIREBASE_SERVICE_ACCOUNT_JSON_B64_ENC')!
const serviceAccount = JSON.parse(
  new TextDecoder().decode(
    Uint8Array.from(atob(serviceAccountBase64), (c) => c.charCodeAt(0))
  )
)

interface Notification {
  id: number
  user_id: string
  title: string
  body: string
  metadata?: Record<string, any>
  silent?: boolean
  timestamp: string
  status: string

}

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
)

Deno.serve(async (req) => {
  try {
    // Query for pending notifications that are due
    const { data: notifications, error: queryError } = await supabase
      .schema('comms')
      .from('notifications')
      .select('*')
      .eq('status', 'pending')
      .lte('timestamp', new Date().toISOString())
      .limit(100) // Process 100 notifications per run

    if (queryError) {
      console.error('Error querying notifications:', queryError)
      return new Response(JSON.stringify({ error: queryError.message }), { status: 500 })
    }

    if (!notifications || notifications.length === 0) {
      console.log('No pending notifications found')
      return new Response(JSON.stringify({
        message: 'No pending notifications to process',
        processed: 0
      }), {
        headers: { 'Content-Type': 'application/json' },
      })
    }

    console.log(`Found ${notifications.length} pending notification(s) to process`)

    const accessToken = await getAccessToken({
      clientEmail: serviceAccount.client_email,
      privateKey: serviceAccount.private_key,
    })

    const results = {
      processed: 0,
      sent: 0,
      failed: 0,
      errors: [] as any[]
    }

    // Process each notification
    for (const notification of notifications) {
      results.processed++

      try {
        console.log(`Processing notification ${notification.id} for user ${notification.user_id}`)
        await sendNotification(notification, accessToken)

        // Mark as sent
        await supabase
          .schema('comms')
          .from('notifications')
          .update({
            status: 'sent',
            sent_at: new Date().toISOString()
          })
          .eq('id', notification.id)

        // Log the notification event after successful send
        await logNotificationEvent(notification)

        results.sent++
        console.log(`✓ Notification ${notification.id} sent successfully`)
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : String(error)
        console.error(`✗ Notification ${notification.id} failed:`, errorMsg)

        // Mark as failed
        await supabase
          .schema('comms')
          .from('notifications')
          .update({
            status: 'failed',
            error_message: errorMsg
          })
          .eq('id', notification.id)

        results.failed++
        results.errors.push({
          notification_id: notification.id,
          error: errorMsg
        })
      }
    }

    console.log(`Summary: ${results.sent} sent, ${results.failed} failed out of ${results.processed} processed`)

    return new Response(JSON.stringify(results), {
      headers: { 'Content-Type': 'application/json' },
    })
  } catch (error) {
    console.error('Fatal error in notification_send:', error)
    return new Response(JSON.stringify({
      error: error instanceof Error ? error.message : String(error)
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    })
  }
})

async function sendNotification(notification: Notification, accessToken: string) {
  // Get user's FCM token
  const { data: userData } = await supabase
    .from('users')
    .select('fcm_token')
    .eq('id', notification.user_id)
    .single()

  if (!userData || !userData.fcm_token) {
    throw new Error(`FCM token not found for user ${notification.user_id}`)
  }

  const fcmToken = userData.fcm_token as string

  // Convert metadata to FCM-compatible format (all values must be strings)
  const fcmData: Record<string, string> = {}
  if (notification.metadata) {
    for (const [key, value] of Object.entries(notification.metadata)) {
      // Convert all values to strings (FCM requirement)
      if (typeof value === 'object' && value !== null) {
        fcmData[key] = JSON.stringify(value)
      } else {
        fcmData[key] = String(value)
      }
    }
  }

  // Setup basic payload with fcm token and metadata
  const messagePayload: any = {
    token: fcmToken,
    data: fcmData,
  }

  // Silent APS payload
  if (notification.silent) {
    messagePayload.apns = {
      payload: {
        aps: {
          'content-available': 1,
          'apns-priority': 5,
        },
      },
    }
  }
  // Regular notification payload
  else {
    messagePayload.notification = {
      title: notification.title,
      body: notification.body
    }

    messagePayload.apns = {
      payload: {
        aps: {
          sound: 'default',
        },
      },
    }
  }

  // Send request
  console.log(`Sending FCM message to user ${notification.user_id} (silent: ${notification.silent || false})`)
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        message: messagePayload,
      }),
    }
  )

  const resData = await res.json()
  if (res.status < 200 || res.status > 299) {
    console.error(`FCM API error (${res.status}):`, resData)
    throw new Error(JSON.stringify(resData))
  }

  return resData
}

async function logNotificationEvent(notification: Notification) {
  try {
    // Get the user's logging_id array from public.users
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('logging_id')
      .eq('id', notification.user_id)
      .single()

    if (userError) {
      console.error('Error fetching user logging_id:', userError)
      return
    }

    // Determine the user_id to use for events table
    let eventUserId = notification.user_id // Default to actual user_id

    if (userData?.logging_id && Array.isArray(userData.logging_id) && userData.logging_id.length > 0) {
      // Use the last entry from the logging_id array
      eventUserId = userData.logging_id[userData.logging_id.length - 1]
    }

    // Prepare extra_data with notification details
    const extraData: any = {
      notification_id: notification.id,
      title: notification.title,
      body_preview: notification.body.substring(0, 50),
      silent: notification.silent || false
    }

    // Add metadata if it exists
    if (notification.metadata) {
      // Include specific metadata fields that might be useful for tracking
      if (notification.metadata.type) {
        extraData.type = notification.metadata.type
      }
      // Add any other relevant metadata
      extraData.metadata = notification.metadata
    }

    // Log the event to public.events
    const { error: eventError } = await supabase
      .from('events')
      .insert({
        user_id: eventUserId,
        event: 'received_notification',
        extra_data: extraData
      })

    if (eventError) {
      console.error('Error logging notification event:', eventError)
    }

  } catch (error) {
    console.error('Unexpected error in logNotificationEvent:', error)
    // Don't throw - logging failures shouldn't affect notification sending
  }
}

const getAccessToken = ({
  clientEmail,
  privateKey,
}: {
  clientEmail: string
  privateKey: string
}): Promise<string> => {
  return new Promise((resolve, reject) => {
    const jwtClient = new JWT({
      email: clientEmail,
      key: privateKey,
      scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
    })
    jwtClient.authorize((err, tokens) => {
      if (err) {
        reject(err)
        return
      }
      resolve(tokens!.access_token!)
    })
  })
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/notification_send' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{
      "type": "INSERT",
      "table": "notifications",
      "record": {
          "id": "notification-id",
          "user_id": "9142467144",
          "title": "Test Notification",
          "body": "This is a test notification"
      },
      "schema": "public"
  }'


  CHECK IN

 curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/notification_send' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{
      "type": "INSERT",
      "table": "notifications",
      "record": {
          "id": "1",
          "user_id": "9142467144",
          "title": "Time to Check In",
          "body": "Remember to complete your daily check-in",
          "metadata": {
              "type": "checkIn"
          }
      },
      "schema": "comms"
  }'


  SILENT

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/notification_send' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{
      "type": "INSERT",
      "table": "notifications",
      "record": {
          "id": "1",
          "user_id": "9142467144",
          "title": "Silent Notification",
          "body": "This is a silent notification",
          "silent": true,
          "metadata": {
            "type": "popInRequest"
          }
      },
      "schema": "comms"
  }'

*/
