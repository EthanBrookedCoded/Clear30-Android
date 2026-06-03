// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

// Supabase client setup
const supabaseUrl = Deno.env.get('SUPABASE_URL')
const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
const supabase = createClient(supabaseUrl, supabaseKey)

interface SlackNotificationRecord {
  id: string
  channel_type: string
  title: string
  message: string
  metadata: Record<string, any> | null
  status: string
  created_at: string
  sent_at: string | null
  error_message: string | null
  retry_count: number
  priority: string
}

interface WebhookPayload {
  type: 'INSERT'
  table: string
  record: SlackNotificationRecord
  schema: 'comms'
}

interface SlackChannel {
  channel_type: string
  webhook_url: string
  channel_name: string
  description: string | null
  is_active: boolean
}

async function getChannelConfig(channelType: string): Promise<SlackChannel | null> {
  const { data, error } = await supabase
    .schema('comms')
    .from('slack_channels')
    .select('*')
    .eq('channel_type', channelType)
    .eq('is_active', true)
    .single()

  if (error || !data) {
    console.error(`No active Slack channel found for type: ${channelType}`, error)
    return null
  }

  return data
}

async function createSlackMessage(notification: SlackNotificationRecord, channelConfig: SlackChannel) {
  // If metadata exists and has rich formatting, use it
  if (notification.metadata && notification.metadata.blocks) {
    return {
      channel: channelConfig.channel_name,
      blocks: notification.metadata.blocks
    }
  }

  // Create rich formatting from title, message, and metadata
  const blocks: any[] = [
    {
      type: "header",
      text: {
        type: "plain_text",
        text: notification.title
      }
    },
    {
      type: "section",
      text: {
        type: "mrkdwn",
        text: notification.message
      }
    }
  ]

  // Add metadata fields if they exist
  if (notification.metadata) {
    const fields: Array<{ type: string, text: string }> = []

    // Add common metadata fields
    for (const [key, value] of Object.entries(notification.metadata)) {
      if (key !== 'blocks' && value !== null && value !== undefined) {
        fields.push({
          type: "mrkdwn",
          text: `*${key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}:* ${value}`
        })
      }
    }

    if (fields.length > 0) {
      blocks.push({
        type: "section",
        fields: fields
      })
    }
  }

  // Add priority indicator for high/urgent notifications
  if (notification.priority === 'high' || notification.priority === 'urgent') {
    blocks.unshift({
      type: "section",
      text: {
        type: "mrkdwn",
        text: notification.priority === 'urgent' ? "🚨 *URGENT*" : "⚠️ *HIGH PRIORITY*"
      }
    })
  }

  return {
    channel: channelConfig.channel_name,
    blocks: blocks
  }
}

async function sendSlackMessage(notification: SlackNotificationRecord, channelConfig: SlackChannel) {
  try {
    const slackMessage = await createSlackMessage(notification, channelConfig)

    const response = await fetch(channelConfig.webhook_url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(slackMessage)
    })

    if (!response.ok) {
      throw new Error(`Slack webhook failed with status: ${response.status}`)
    }

    return { success: true }
  } catch (error) {
    console.error('Error sending Slack message:', error)
    return { success: false, error: error.message }
  }
}

async function updateNotificationStatus(notificationId: string, status: string, errorMessage?: string) {
  const updateData: any = {
    status: status
  }

  if (status === 'sent') {
    updateData.sent_at = new Date().toISOString()
  } else if (status === 'failed' && errorMessage) {
    updateData.error_message = errorMessage
  }

  const { error } = await supabase
    .schema('comms')
    .from('slack_notifications')
    .update(updateData)
    .eq('id', notificationId)

  if (error) {
    console.error('Error updating notification status:', error)
  }
}

async function incrementRetryCount(notificationId: string): Promise<number> {
  // Get current retry count
  const { data, error } = await supabase
    .schema('comms')
    .from('slack_notifications')
    .select('retry_count')
    .eq('id', notificationId)
    .single()

  if (error || !data) {
    console.error('Error fetching retry count:', error)
    return 0
  }

  const newRetryCount = data.retry_count + 1

  // Update retry count
  await supabase
    .schema('comms')
    .from('slack_notifications')
    .update({ retry_count: newRetryCount })
    .eq('id', notificationId)

  return newRetryCount
}

serve(async (req) => {
  try {
    const payload: WebhookPayload = await req.json()

    if (payload.type !== 'INSERT') {
      return new Response(JSON.stringify({ message: 'Not an insert event' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    const notification = payload.record
    
    // Guard: Verify this notification row exists in THIS environment's DB.
    // If a local dev insert triggers this production edge function (because the DB webhook
    // has the prod URL hardcoded), the row ID won't exist in prod DB → skip cleanly.
    const { data: rowExists } = await supabase
      .schema('comms')
      .from('slack_notifications')
      .select('id')
      .eq('id', notification.id)
      .maybeSingle()

    if (!rowExists) {
      console.log(`Notification ${notification.id} not found in this DB — cross-environment call, skipping.`)
      return new Response(
        JSON.stringify({ message: 'Cross-environment call, skipping' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Only process pending notifications
    if (notification.status !== 'pending') {
      return new Response(JSON.stringify({ message: 'Notification not in pending status' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // Get channel configuration
    const channelConfig = await getChannelConfig(notification.channel_type)
    if (!channelConfig) {
      await updateNotificationStatus(
        notification.id,
        'failed',
        `No active Slack channel configuration found for type: ${notification.channel_type}`
      )
      return new Response(JSON.stringify({ error: 'Channel configuration not found' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // Send Slack message
    const result = await sendSlackMessage(notification, channelConfig)

    if (result.success) {
      // Update status to sent
      await updateNotificationStatus(notification.id, 'sent')

      return new Response(JSON.stringify({
        message: 'Slack notification sent successfully',
        notification_id: notification.id,
        channel: channelConfig.channel_name
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    } else {
      // Handle failure with retry logic
      const newRetryCount = await incrementRetryCount(notification.id)

      if (newRetryCount >= 3) {
        // Max retries reached, mark as failed
        await updateNotificationStatus(notification.id, 'failed', result.error)

        return new Response(JSON.stringify({
          error: 'Slack notification failed after max retries',
          notification_id: notification.id,
          retry_count: newRetryCount,
          final_error: result.error
        }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        })
      } else {
        // Will be retried - insert new row with pending status for retry
        await supabase
          .schema('comms')
          .from('slack_notifications')
          .insert({
            channel_type: notification.channel_type,
            title: notification.title,
            message: notification.message,
            metadata: notification.metadata,
            status: 'pending',
            priority: notification.priority,
            retry_count: newRetryCount
          })

        return new Response(JSON.stringify({
          message: 'Slack notification failed, retry scheduled',
          notification_id: notification.id,
          retry_count: newRetryCount,
          error: result.error
        }), {
          status: 202,
          headers: { 'Content-Type': 'application/json' }
        })
      }
    }

  } catch (error) {
    console.error('Error processing Slack notification:', error)
    return new Response(JSON.stringify({
      error: error.message || 'Failed to process Slack notification'
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/slack_send_message' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
        "type": "INSERT",
        "table": "slack_notifications",
        "record": {
            "id": "123e4567-e89b-12d3-a456-426614174000",
            "channel_type": "feedback",
            "title": "Test Notification",
            "message": "This is a test Slack notification message",
            "metadata": {"user_id": "test123", "source": "manual_test"},
            "status": "pending",
            "created_at": "2024-01-04T12:00:00Z",
            "sent_at": null,
            "error_message": null,
            "retry_count": 0,
            "priority": "normal"
        },
        "schema": "comms"
    }'

*/
