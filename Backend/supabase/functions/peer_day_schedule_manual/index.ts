//
// peer_day_schedule_manual
// Triggered by database webhook on public.users UPDATE
// Detects break restarts, break ends, first check-ins, and Day 1 check-ins to send peer messages
// RUNS ON USERS UPDATE WEBHOOK
//

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import {
  getTodayInTimezone,
  getYesterdayInTimezone,
  getTodayStartISO,
  getDaysDifference,
  getValidTimezone
} from '../shared/utils/dateUtils.ts'
import { getActiveClear30Break, ProgramBreak } from '../shared/utils/programUtils.ts'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

// Types
type DayData = {
  sober: boolean
  loggedCheckIns?: { id: string; completion: boolean; timestamp: string }[]
}

type User = {
  id: string
  name: string
  timezone: string | null
  program_breaks: ProgramBreak[] | null
  day_info: (string | DayData)[] | null
  peer_support_migrated: boolean | null
  created_at: string | null  // Account creation date for Life program Day 1 calculation
}

type WebhookPayload = {
  type: 'INSERT' | 'UPDATE' | 'DELETE'
  table: string
  schema: string
  record: User
  old_record: User
}

type MessageToSend = {
  additionalId: string
  program: string
  day: number
  messageType: 'automated-event' | 'follow-up'  // 'automated-event' for restart/end, 'follow-up' for sober/smoked
  scheduledFor?: Date
}

// Hardcoded message templates (from library.sms_day)
const MESSAGE_TEMPLATES: Record<string, string> = {
  // Welcome message (first check-in, clear30)
  'welcome:clear30': `Hey _CLIENTNAME_ – congrats on starting your Clear30 journey! My name's Gerad. I'm 22 and used to smoke every day, but I was able to stop — I haven't smoked in over nine months now.
I'm here if you have any questions or just want to talk things through. I'll only reply when you message me first, and while it might not be right away, I'll always get back to you as soon as I can.`,

  // Welcome message (first check-in, life)
  'welcome:life': `Hey _CLIENTNAME_, this is Gerad from Clear30, congrats on starting your weed tracking journey! Just wanted to reach out and let you know that I'm here if you need anything / have any questions!`,

  // Restart message (day 1, clear30)
  'restart:clear30': `Hi again _CLIENTNAME_!

It's awesome you're starting a new Clear30 :)

Its Gerad again, lmk if you have any questions with your new break - I'm here to help.`,

  // End break message (day 0, life)
  'end:life': `🎉 Welcome to Clear30 Life!

You're now in the ongoing version of Clear30 - less focused on a strict break, and more about daily support and helpful strategies to level up your life:

⚡ Productivity
🤝 Relationships
😊 Daily happiness

You'll still be able to track your smoking and get support anytime, but now there's even more to explore.

If you have questions or wanna chat, just text back - I'm always here!`,

  // Sober follow-up (day 1, both programs - same text)
  'sober-follow-up:clear30': `✅ You didn't smoke today _CLIENTNAME_,
😤🙏 We're so proud of you!!

You're off to an amazing start,
lets keep that momentum going!!`,

  'sober-follow-up:life': `✅ You didn't smoke today _CLIENTNAME_,
😤🙏 We're so proud of you!!

You're off to an amazing start,
lets keep that momentum going!!`,

  // Smoked follow-up (day 1, both programs - same text)
  'smoked-follow-up:clear30': `💯 Congrats on your first check in _CLIENTNAME_!
😤🙏 & we're just getting started!!

You're one day closer to making this an automatic habit.
Let's make it to 30! `,

  'smoked-follow-up:life': `💯 Congrats on your first check in _CLIENTNAME_!
😤🙏 & we're just getting started!!

You're one day closer to making this an automatic habit.
Let's make it to 30! `,
}

Deno.serve(async (req) => {
  const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

  try {
    const payload: WebhookPayload = await req.json()

    // Only handle UPDATE events
    if (payload.type !== 'UPDATE') {
      return new Response(JSON.stringify({ message: 'Ignoring non-UPDATE event' }), { status: 200 })
    }

    const oldUser = payload.old_record
    const newUser = payload.record

    // Check if user has peer support enabled
    if (!newUser.peer_support_migrated) {
      return new Response(JSON.stringify({ message: 'User not migrated to peer support' }), { status: 200 })
    }

    const messagesToSend: MessageToSend[] = []

    // Get user's timezone for all date calculations
    const userTimezone = getValidTimezone(newUser.timezone)

    // 1. Detect break restart
    const restartedBreak = detectBreakRestart(oldUser.program_breaks, newUser.program_breaks, userTimezone)
    if (restartedBreak) {
      console.log(`Detected break restart for user ${newUser.id}`)
      messagesToSend.push({ additionalId: 'restart', program: 'clear30', day: 1, messageType: 'automated-event' })
    }

    // 2. Detect break end
    const endedBreak = detectBreakEnd(oldUser.program_breaks, newUser.program_breaks)
    if (endedBreak) {
      console.log(`Detected break end for user ${newUser.id}`)
      messagesToSend.push({ additionalId: 'end', program: 'life', day: 0, messageType: 'automated-event' })
    }

    // 3. Detect first-ever check-in (day_info empty → non-empty)
    // Sends the welcome message from Gerad, scheduled 15 min out
    const isFirstCheckIn = detectFirstCheckIn(oldUser.day_info, newUser.day_info)
    if (isFirstCheckIn) {
      const currentProgram = getCurrentProgram(newUser.program_breaks, userTimezone)
      console.log(`Detected first check-in for user ${newUser.id} (${currentProgram})`)
      messagesToSend.push({
        additionalId: 'welcome',
        program: currentProgram,
        day: 0,
        messageType: 'automated-event',
        scheduledFor: new Date(Date.now() + 15 * 60 * 1000)
      })
    }

    // 4. Detect Day 1 check-in (today only)
    // Use 'follow-up' type to avoid conflicting with peer_day_schedule's daily 'automated-event' messages
    // Works for both Clear30 (day 1 since break start) and Life (day 1 since account creation)
    const day1CheckIn = detectDay1CheckIn(oldUser.day_info, newUser.day_info, newUser.program_breaks, newUser.created_at, userTimezone)
    if (day1CheckIn) {
      const additionalId = day1CheckIn.sober ? 'sober-follow-up' : 'smoked-follow-up'
      const currentProgram = getCurrentProgram(newUser.program_breaks, userTimezone)
      console.log(`Detected Day 1 ${day1CheckIn.sober ? 'sober' : 'smoked'} check-in for user ${newUser.id}`)
      messagesToSend.push({
        additionalId,
        program: currentProgram,
        day: 1,
        messageType: 'follow-up',
        scheduledFor: new Date(Date.now() + 30 * 60 * 1000) // 30 min from now
      })
    }

    if (messagesToSend.length === 0) {
      console.log(`[peer_day_schedule_manual] no-op | userId=${newUser.id} reason=no_triggers_detected`)
      return new Response(JSON.stringify({ message: 'No triggers detected' }), { status: 200 })
    }

    // Fetch and send messages
    let sent = 0
    for (const msg of messagesToSend) {
      const success = await sendMessage(supabase, newUser, msg, userTimezone)
      if (success) sent++
    }

    console.log(`[peer_day_schedule_manual] success | userId=${newUser.id} triggered=[${messagesToSend.map(m => m.additionalId).join(',')}] sent=${sent}`)

    return new Response(
      JSON.stringify({
        message: 'Peer day schedule manual complete',
        userId: newUser.id,
        triggered: messagesToSend.map(m => m.additionalId),
        sent
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error(`[peer_day_schedule_manual] error |`, error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : String(error) }),
      { status: 500 }
    )
  }
})

/**
 * Detect if a break was restarted (start_date changed to yesterday)
 * When user restarts, ProgramTimelineHandler shifts start_date forward so today becomes Day 1
 */
function detectBreakRestart(
  oldBreaks: ProgramBreak[] | null,
  newBreaks: ProgramBreak[] | null,
  timezone: string
): ProgramBreak | null {
  if (!oldBreaks || !newBreaks || !Array.isArray(oldBreaks) || !Array.isArray(newBreaks)) {
    return null
  }

  const todayStr = getTodayInTimezone(timezone)
  const yesterdayStr = getYesterdayInTimezone(timezone)

  // Find the active (no end_date_override) clear30 break in old and new
  const oldActive = oldBreaks.find(ob => ob.type === 'clear30' && !ob.end_date_override)
  const newActive = newBreaks.find(nb => nb.type === 'clear30' && !nb.end_date_override)

  // A restart means the active break's start_date changed to yesterday
  if (oldActive && newActive && oldActive.start_date !== newActive.start_date) {
    const oldStartDateStr = oldActive.start_date.substring(0, 10)
    const newStartDateStr = newActive.start_date.substring(0, 10)

    if (newStartDateStr === yesterdayStr && oldStartDateStr < todayStr) {
      return newActive
    }
  }

  return null
}

/**
 * Detect if a break was ended (end_date_override was null, now set)
 */
function detectBreakEnd(
  oldBreaks: ProgramBreak[] | null,
  newBreaks: ProgramBreak[] | null
): ProgramBreak | null {
  if (!oldBreaks || !newBreaks || !Array.isArray(oldBreaks) || !Array.isArray(newBreaks)) {
    return null
  }

  for (const newBreak of newBreaks) {
    if (newBreak.type !== 'clear30') continue

    // Check if end_date_override is now set
    if (!newBreak.end_date_override) continue

    // Find matching old break by start_date (unique per break, unlike name)
    const oldBreak = oldBreaks.find(ob => ob.type === 'clear30' && ob.start_date === newBreak.start_date)
    if (!oldBreak) continue

    // Check if end_date_override changed from null to a value
    if (!oldBreak.end_date_override && newBreak.end_date_override) {
      return newBreak
    }
  }

  return null
}

/**
 * Detect if this is the user's very first check-in (day_info went from empty to non-empty)
 */
function detectFirstCheckIn(
  oldDayInfo: (string | DayData)[] | null,
  newDayInfo: (string | DayData)[] | null
): boolean {
  const wasEmpty = !oldDayInfo || !Array.isArray(oldDayInfo) || oldDayInfo.length === 0
  const nowHasData = !!newDayInfo && Array.isArray(newDayInfo) && newDayInfo.length > 0
  return wasEmpty && nowHasData
}

/**
 * Detect if user just checked in for Day 1 (today only, not backdated)
 * Works for both Clear30 and Life program users
 */
function detectDay1CheckIn(
  oldDayInfo: (string | DayData)[] | null,
  newDayInfo: (string | DayData)[] | null,
  programBreaks: ProgramBreak[] | null,
  createdAt: string | null,
  timezone: string
): { sober: boolean } | null {
  if (!newDayInfo || !Array.isArray(newDayInfo)) {
    return null
  }

  const todayStr = getTodayInTimezone(timezone)

  let startDateStr: string | null = null

  // Check if user is in active Clear30
  const activeClear30 = programBreaks ? getActiveClear30Break(programBreaks, timezone) : null

  if (activeClear30) {
    // Clear30: Day 1 is second day since break start
    startDateStr = activeClear30.start_date.substring(0, 10)
  } else if (createdAt) {
    // Life: Day 1 is second day since account creation
    startDateStr = createdAt.substring(0, 10)
  }

  if (!startDateStr) return null

  // Calculate what day the user is on
  const currentDay = getDaysDifference(startDateStr, todayStr)

  // Only trigger for Day 1
  if (currentDay !== 1) return null

  // Find today's entry in new day_info
  let newTodayData: DayData | null = null
  for (let i = 0; i < newDayInfo.length; i += 2) {
    const dateStr = newDayInfo[i] as string
    if (dateStr === todayStr && i + 1 < newDayInfo.length) {
      newTodayData = newDayInfo[i + 1] as DayData
      break
    }
  }

  if (!newTodayData) return null

  // Check if this entry existed before with same sober value
  if (oldDayInfo && Array.isArray(oldDayInfo)) {
    for (let i = 0; i < oldDayInfo.length; i += 2) {
      const dateStr = oldDayInfo[i] as string
      if (dateStr === todayStr && i + 1 < oldDayInfo.length) {
        const oldTodayData = oldDayInfo[i + 1] as DayData
        // Entry already existed with same sober status - no trigger
        if (oldTodayData.sober === newTodayData.sober) {
          return null
        }
        // Sober status changed - this counts as a new check-in trigger
        break
      }
    }
  }

  return { sober: newTodayData.sober }
}

/**
 * Determine current program type ('clear30' or 'life')
 */
function getCurrentProgram(programBreaks: ProgramBreak[] | null, timezone: string): string {
  if (!programBreaks || !Array.isArray(programBreaks)) {
    return 'life'
  }

  const activeClear30 = getActiveClear30Break(programBreaks, timezone)
  return activeClear30 ? 'clear30' : 'life'
}

/**
 * Send a peer message using hardcoded templates and inserting into comms.peer_messages
 */
async function sendMessage(
  supabase: ReturnType<typeof createClient>,
  user: User,
  msg: MessageToSend,
  timezone: string
): Promise<boolean> {
  try {
    // Check for duplicate messages (prevent sending same type multiple times today)
    const todayStartISO = getTodayStartISO(timezone)

    const { data: existingMessages } = await supabase
      .schema('comms')
      .from('peer_messages')
      .select('id')
      .eq('user_id', user.id)
      .eq('type', msg.messageType)
      .gte('created_at', todayStartISO)
      .limit(1)

    if (existingMessages && existingMessages.length > 0) {
      console.log(`Skipping ${msg.additionalId} - already sent ${msg.messageType} message today for user ${user.id}`)
      return false
    }

    // Get hardcoded template
    const templateKey = `${msg.additionalId}:${msg.program}`
    const template = MESSAGE_TEMPLATES[templateKey]

    if (!template) {
      console.error(`No template found for key: ${templateKey} for user ${user.id}`)
      return false
    }

    // Replace placeholders
    const text = template.replace(/_CLIENTNAME_/g, user.name || 'there')

    // Insert into peer_messages
    const insertData: Record<string, unknown> = {
      user_id: user.id,
      text: text,
      outbound: true,
      type: msg.messageType,  // 'automated-event' for restart/end, 'follow-up' for sober/smoked
    }

    // Add scheduled_for for delayed messages
    if (msg.scheduledFor) {
      insertData.scheduled_for = msg.scheduledFor.toISOString()
    }

    const { error: insertError } = await supabase
      .schema('comms')
      .from('peer_messages')
      .insert(insertData)

    if (insertError) {
      // Unique constraint violation means a concurrent invocation already inserted this message
      if (insertError.code === '23505') {
        console.log(`Skipping ${msg.additionalId} - duplicate prevented by constraint for user ${user.id}`)
        return false
      }
      console.error(`Error inserting message for user ${user.id}:`, insertError)
      return false
    }

    console.log(`Sent ${msg.additionalId} message to user ${user.id}${msg.scheduledFor ? ' (scheduled)' : ''}`)
    return true

  } catch (error) {
    console.error(`Error sending message for user ${user.id}:`, error)
    return false
  }
}

/* To invoke locally:

  1. Run `supabase start`
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/peer_day_schedule_manual' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "type": "UPDATE",
      "table": "users",
      "schema": "public",
      "record": {
        "id": "test-user-id",
        "name": "Test User",
        "peer_support_migrated": true,
        "program_breaks": [{"name": "Clear30", "type": "clear30", "start_date": "2026-02-03T05:00:00.000"}],
        "day_info": ["2026-02-04", {"sober": true}]
      },
      "old_record": {
        "id": "test-user-id",
        "name": "Test User",
        "peer_support_migrated": true,
        "program_breaks": [{"name": "Clear30", "type": "clear30", "start_date": "2026-02-01T05:00:00.000"}],
        "day_info": []
      }
    }'

*/
