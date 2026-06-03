/**
 * sms_accountability_schedule
 *
 * Schedules slip-up, get-back, and check-in reminder SMS.
 * Triggered by database webhook on users table UPDATE.
 * Only runs if day_info has changed between old and new record.
 *
 * Actions:
 * 1. Check if day_info changed (early return if not)
 * 2. Extract latest check-in from day_info
 * 3. Calculate consecutive sober/non-sober days
 * 4. Determine appropriate message type (slip-up, get-back)
 * 5. Cancel existing pending accountability messages
 * 6. Schedule new accountability message
 * 7. Schedule check-in reminders
 * 
 * RUNS ON USERS UPDATE WEBHOOK
 */

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'npm:@supabase/supabase-js@2'
// Note: User timezone is available in record but scheduling currently uses UTC
// To use user's local time for 4 PM scheduling, import and use:
// import { getValidTimezone } from '../shared/utils/dateUtils.ts'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

interface SMSSettings {
  all: boolean
  options?: {
    'Accountability Texts'?: boolean
  }
}

interface DayData {
  sober: boolean
}

type User = {
  id: string
  name: string | null
  phone_number: string | null
  timezone: string | null
  day_info: (string | DayData)[] | null
  sms_settings: SMSSettings | null
  peer_support_migrated: boolean | null
}

type WebhookPayload = {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  schema: string
  record: User
  old_record: User
}

Deno.serve(async (req) => {
  const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

  try {
    const payload: WebhookPayload = await req.json()

    // Only handle UPDATE events
    if (payload.type !== 'UPDATE') {
      return new Response(
        JSON.stringify({ message: 'Ignoring non-UPDATE event' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const oldUser = payload.old_record
    const newUser = payload.record

    // Check if day_info has changed
    const oldDayInfo = JSON.stringify(oldUser.day_info)
    const newDayInfo = JSON.stringify(newUser.day_info)

    if (oldDayInfo === newDayInfo) {
      return new Response(
        JSON.stringify({ message: 'day_info unchanged, skipping' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Check if SMS is enabled
    if (!newUser.phone_number) {
      return new Response(
        JSON.stringify({ message: 'No phone number' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const smsSettings = newUser.sms_settings as SMSSettings
    if (!smsSettings?.all) {
      return new Response(
        JSON.stringify({ message: 'SMS disabled' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (!smsSettings.options?.['Accountability Texts']) {
      return new Response(
        JSON.stringify({ message: 'Accountability texts disabled' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Make sure they've migrated to peer support
    if (!newUser.peer_support_migrated) {
      return new Response(
        JSON.stringify({ message: 'User not migrated to in app peer support' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Check if blocked
    const { data: blocked } = await supabase
      .schema('comms')
      .from('sms_blocked')
      .select('id')
      .eq('phone_number', newUser.phone_number)
      .single()

    if (blocked) {
      return new Response(
        JSON.stringify({ message: 'Phone number blocked' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Extract latest check-in from day_info
    const latestCheckIn = extractLatestCheckIn(newUser.day_info)
    if (!latestCheckIn) {
      return new Response(
        JSON.stringify({ message: 'No valid check-in found in day_info' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Calculate consecutive days from day_info
    const streaks = calculateStreaks(newUser.day_info)

    // Determine which message type to send
    let messageType: 'slip_up' | 'get_back' | null = null
    let dayKey: number = 0

    if (latestCheckIn.sober === false) {
      messageType = 'slip_up'
      dayKey = streaks.consecutiveNotSober
    } else if (streaks.hasSlipHistory) {
      messageType = 'get_back'
      dayKey = streaks.consecutiveSober
    } else {
      // Perfect record, no accountability message needed
      console.log(`User ${newUser.id} has perfect record, no accountability SMS needed`)
    }

    // Get and schedule message if applicable
    if (messageType && dayKey > 0) {
      const tableName = messageType === 'slip_up' ? 'sms_slip_up' : 'sms_get_back'

      const { data: messages, error: messagesError } = await supabase
        .schema('library')
        .from(tableName)
        .select('message')
        .eq('day', dayKey)

      if (messagesError) {
        console.error(`Error fetching ${messageType} messages for user ${newUser.id}:`, messagesError)
      } else if (messages && messages.length > 0) {
        // Pick random message
        const template = messages[Math.floor(Math.random() * messages.length)]
        const text = template.message.replace(/_CLIENTNAME_/g, newUser.name || 'there')

        // Schedule for 4 PM today or +60 minutes if past 4 PM
        const now = new Date()
        const fourPM = new Date(now)
        fourPM.setUTCHours(16, 0, 0, 0)
        const scheduledFor = now < fourPM ? fourPM : new Date(now.getTime() + 60 * 60 * 1000)

        // Cancel existing accountability messages
        await supabase
          .schema('comms')
          .from('sms_messages')
          .update({ canceled: true })
          .eq('user_id', newUser.id)
          .is('sent_at', null)
          .in('type', ['slip_up', 'get_back', 'check_in'])

        // Insert new message
        const { error: insertError } = await supabase
          .schema('comms')
          .from('sms_messages')
          .insert({
            user_id: newUser.id,
            phone_number: newUser.phone_number,
            text: text,
            outbound: true,
            type: messageType,
            scheduled_for: scheduledFor.toISOString()
          })

        if (insertError) {
          console.error(`Error scheduling SMS for user ${newUser.id}:`, insertError)
        } else {
          console.log(`Scheduled ${messageType} SMS for user ${newUser.id}`)
        }
      }
    }

    // Schedule check-in reminders
    await scheduleCheckInReminders(supabase, newUser as { id: string; name: string | null; phone_number: string })

    console.log(`[sms_accountability_schedule] success | userId=${newUser.id} messageType=${messageType ?? 'none'} sober=${latestCheckIn.sober}`)

    return new Response(
      JSON.stringify({ success: true, message_type: messageType, sober: latestCheckIn.sober }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error(`[sms_accountability_schedule] error |`, error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : String(error) }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

/**
 * Extract the latest check-in from day_info array
 * day_info format: ["YYYY-MM-DD", {sober: boolean, ...}, "YYYY-MM-DD", {sober: boolean, ...}, ...]
 */
function extractLatestCheckIn(dayInfo: (string | DayData)[] | null): { date: string; sober: boolean } | null {
  if (!dayInfo || !Array.isArray(dayInfo) || dayInfo.length < 2) {
    return null
  }

  // Parse all entries and sort by date (most recent first)
  const entries: { date: string; sober: boolean }[] = []

  for (let i = 0; i < dayInfo.length; i += 2) {
    const dateStr = dayInfo[i]
    const data = dayInfo[i + 1]

    if (
      typeof dateStr === 'string' &&
      dateStr.match(/^\d{4}-\d{2}-\d{2}$/) &&
      data &&
      typeof data === 'object' &&
      'sober' in data &&
      typeof (data as DayData).sober === 'boolean'
    ) {
      entries.push({ date: dateStr, sober: (data as DayData).sober })
    }
  }

  if (entries.length === 0) {
    return null
  }

  // Sort by date descending (most recent first)
  entries.sort((a, b) => b.date.localeCompare(a.date))

  return entries[0]
}

function calculateStreaks(dayInfo: (string | DayData)[] | null): {
  consecutiveSober: number
  consecutiveNotSober: number
  hasSlipHistory: boolean
} {
  if (!dayInfo || !Array.isArray(dayInfo)) {
    return { consecutiveSober: 0, consecutiveNotSober: 0, hasSlipHistory: false }
  }

  // Sort entries by date (most recent first)
  const entries: { date: string; sober: boolean }[] = []

  for (let i = 0; i < dayInfo.length; i += 2) {
    const dateStr = dayInfo[i]
    const data = dayInfo[i + 1]

    if (
      typeof dateStr === 'string' &&
      dateStr.match(/^\d{4}-\d{2}-\d{2}$/) &&
      data &&
      typeof data === 'object' &&
      'sober' in data &&
      typeof (data as DayData).sober === 'boolean'
    ) {
      entries.push({ date: dateStr, sober: (data as DayData).sober })
    }
  }

  entries.sort((a, b) => b.date.localeCompare(a.date))

  let consecutiveSober = 0
  let consecutiveNotSober = 0
  let hasSlipHistory = false

  // Count from most recent
  for (const entry of entries) {
    if (entry.sober) {
      if (consecutiveNotSober === 0) consecutiveSober++
      else break
    } else {
      hasSlipHistory = true
      if (consecutiveSober === 0) consecutiveNotSober++
      else break
    }
  }

  return { consecutiveSober, consecutiveNotSober, hasSlipHistory }
}

async function scheduleCheckInReminders(
  supabase: ReturnType<typeof createClient>,
  user: { id: string; name: string | null; phone_number: string }
) {
  // Get check-in message templates
  const { data: templates, error: templatesError } = await supabase
    .schema('library')
    .from('sms_check_in')
    .select('day, message')

  if (templatesError || !templates) {
    console.error(`Error fetching check-in templates for user ${user.id}:`, templatesError)
    return
  }

  // Cancel existing check-in reminders
  await supabase
    .schema('comms')
    .from('sms_messages')
    .update({ canceled: true })
    .eq('user_id', user.id)
    .is('sent_at', null)
    .eq('type', 'check_in')

  // Group templates by day and pick one random message per day
  const templatesByDay = new Map<number, { day: number; message: string }[]>()
  for (const template of templates) {
    if (!templatesByDay.has(template.day)) {
      templatesByDay.set(template.day, [])
    }
    templatesByDay.get(template.day)!.push(template)
  }

  // Schedule one message per unique day
  const scheduledMessages: { day: number; scheduled_for: string }[] = []
  for (const [day, dayTemplates] of templatesByDay) {
    // Pick random message for this day
    const template = dayTemplates[Math.floor(Math.random() * dayTemplates.length)]

    const scheduledFor = new Date()
    scheduledFor.setDate(scheduledFor.getDate() + day)
    scheduledFor.setUTCHours(16, 0, 0, 0) // 4 PM UTC

    const text = template.message.replace(/_CLIENTNAME_/g, user.name || 'there')

    await supabase
      .schema('comms')
      .from('sms_messages')
      .insert({
        user_id: user.id,
        phone_number: user.phone_number,
        text: text,
        outbound: true,
        type: 'check_in',
        scheduled_for: scheduledFor.toISOString()
      })

    scheduledMessages.push({ day, scheduled_for: scheduledFor.toISOString() })
  }

  console.log(`Scheduled ${scheduledMessages.length} check-in reminders for user ${user.id}:`, scheduledMessages)
}

/* To invoke locally:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_accountability_schedule' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "type": "UPDATE",
      "table": "users",
      "schema": "public",
      "record": {
        "id": "test-user-id",
        "name": "Test",
        "phone_number": "+1234567890",
        "day_info": ["2026-02-04", {"sober": true}],
        "sms_settings": {"all": true, "options": {"Accountability Texts": true}},
        "peer_support_migrated": true
      },
      "old_record": {
        "id": "test-user-id",
        "name": "Test",
        "phone_number": "+1234567890",
        "day_info": null,
        "sms_settings": {"all": true, "options": {"Accountability Texts": true}},
        "peer_support_migrated": true
      }
    }'

*/
