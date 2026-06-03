/**
 * sms_start_soon_schedule
 *
 * Schedules start-soon countdown SMS for users in their pre-Clear30 period.
 * Triggered by pg_cron daily.
 *
 * Actions:
 * 1. Get users currently in a clear30-start-soon break OR whose Clear30 started today
 * 2. Calculate days until Clear30 starts (or 0 if started today)
 * 3. Match templates from library.sms_start_soon based on assessment responses
 * 4. Schedule SMS if user has checked in
 * 
 * RUNS ON CRON JOB
 */

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'npm:@supabase/supabase-js@2'
import {
  getTodayInTimezone,
  getDaysDifference,
  compareDates,
  getValidTimezone
} from '../shared/utils/dateUtils.ts'
import { ProgramBreak } from '../shared/utils/programUtils.ts'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

interface User {
  id: string
  name: string | null
  phone_number: string
  timezone: string | null
  sms_settings: {
    all: boolean
  }
  program_breaks: ProgramBreak[]
  day_info: unknown[]
}

interface StartSoonTemplate {
  type: 'intro' | 'countdown'
  day: number
  hour: number | null
  question_id: string | null
  question_response: string | null
  text: string
}

interface AssessmentResponses {
  [key: string]: string[]
}

// Calculate the day key (days until Clear30 starts) using timezone-aware dates
function calculateDayKey(
  clear30StartDateStr: string,
  todayStr: string
): number {
  // getDaysDifference returns positive if end > start
  // We want days until Clear30, so if Clear30 is in future, result is positive
  return getDaysDifference(todayStr, clear30StartDateStr.substring(0, 10))
}

// Filter templates based on type, day, and assessment responses
function filterTemplates(
  templates: StartSoonTemplate[],
  messageType: 'intro' | 'countdown',
  dayKey: number,
  userResponses: AssessmentResponses | null
): StartSoonTemplate | null {
  // Step 1: Filter by type and day
  let possible = templates.filter(t => t.type === messageType && t.day === dayKey)

  // Step 2: Filter by question_id/question_response (assessment matching)
  possible = possible.filter(t => {
    if (!t.question_id || !t.question_response) return true
    const userAnswers = userResponses?.[t.question_id] || []
    return userAnswers.includes(t.question_response)
  })

  // Step 3: Prefer templates with question matching over generic ones
  const matched = possible.filter(t => t.question_id && t.question_response)
  if (matched.length > 0) {
    possible = matched
  }

  // Step 4: Pick random template from remaining
  return possible.length > 0
    ? possible[Math.floor(Math.random() * possible.length)]
    : null
}

Deno.serve(async (_req) => {
  const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

  try {
    // Note: We'll calculate "today" per-user using their timezone

    // Get users with SMS enabled, program_breaks, and day_info (for check-in verification)
    const { data: users, error: usersError } = await supabase
      .from('users')
      .select('id, name, phone_number, timezone, sms_settings, program_breaks, day_info')
      .not('phone_number', 'is', null)
      .not('program_breaks', 'is', null)
      .filter('peer_support_migrated', 'eq', true)
      .filter('sms_settings->all', 'eq', true)

    if (usersError) {
      console.error('Error fetching users:', usersError)
      return new Response(
        JSON.stringify({ error: usersError.message }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (!users || users.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No users to process', scheduled: 0 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // Get start soon templates from new simplified table
    const { data: templates, error: templatesError } = await supabase
      .schema('library')
      .from('sms_start_soon')
      .select('type, day, text, hour, question_id, question_response')

    if (templatesError || !templates) {
      console.error('Error fetching start soon templates:', templatesError)
      return new Response(
        JSON.stringify({ error: 'No start soon templates found' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    let scheduled = 0
    let skippedNoCheckIn = 0
    let skippedNoBreak = 0
    let skippedNoTemplate = 0

    for (const user of users as User[]) {
      if (!user.program_breaks || !Array.isArray(user.program_breaks)) {
        skippedNoBreak++
        continue
      }

      // Verify user has checked in (day_info must not be empty)
      if (!user.day_info || !Array.isArray(user.day_info) || user.day_info.length === 0) {
        skippedNoCheckIn++
        continue
      }

      // Get "today" in user's timezone
      const userTimezone = getValidTimezone(user.timezone)
      const todayStr = getTodayInTimezone(userTimezone)

      // Find start-soon break that has started (start_date <= today)
      const startSoonBreak = user.program_breaks.find((pb: ProgramBreak) => {
        if (pb.type !== 'clear30-start-soon') return false
        if (!pb.start_date) return false

        const startDateStr = pb.start_date.substring(0, 10)
        return compareDates(startDateStr, todayStr) <= 0  // start_date <= today
      })

      if (!startSoonBreak) {
        skippedNoBreak++
        continue
      }

      // Find the Clear30 break that comes after this start-soon break
      const clear30Break = user.program_breaks.find((pb: ProgramBreak) => {
        if (pb.type !== 'clear30') return false
        if (!pb.start_date) return false
        const startSoonDateStr = startSoonBreak.start_date.substring(0, 10)
        const clear30DateStr = pb.start_date.substring(0, 10)
        return compareDates(startSoonDateStr, clear30DateStr) < 0
      })

      if (!clear30Break) {
        skippedNoBreak++
        continue
      }

      const clear30StartDateStr = clear30Break.start_date.substring(0, 10)

      // Skip if Clear30 has already started (past day 0)
      if (compareDates(clear30StartDateStr, todayStr) < 0) {
        skippedNoBreak++
        continue
      }

      // Calculate day key (days until Clear30 starts)
      const dayKey = calculateDayKey(clear30StartDateStr, todayStr)

      // Get assessment responses for template matching
      let userResponses: AssessmentResponses | null = null
      const assessmentResponseId = clear30Break.assessment_response_id

      if (assessmentResponseId) {
        const { data: assessmentData } = await supabase
          .schema('programs')
          .from('program_assessment_responses')
          .select('responses')
          .eq('id', assessmentResponseId)
          .single()

        if (assessmentData?.responses) {
          userResponses = assessmentData.responses as AssessmentResponses
        }
      }

      // Determine message type:
      // - 'intro': User just entered start-soon today (start_date === today)
      // - 'countdown': User is in countdown period
      const startSoonStartStr = startSoonBreak.start_date.substring(0, 10)
      const messageType: 'intro' | 'countdown' =
        startSoonStartStr === todayStr ? 'intro' : 'countdown'

      // Find matching template
      const matchingTemplate = filterTemplates(
        templates as StartSoonTemplate[],
        messageType,
        messageType === 'intro' ? 0 : dayKey,  // Intro messages always use day 0
        userResponses
      )

      if (!matchingTemplate) {
        skippedNoTemplate++
        continue
      }

      // Check if blocked
      const { data: blocked } = await supabase
        .schema('comms')
        .from('sms_blocked')
        .select('id')
        .eq('phone_number', user.phone_number)
        .single()

      if (blocked) continue

      // Check if already scheduled today (using user's timezone)
      const todayStartISO = `${todayStr}T00:00:00Z`
      const todayEndISO = `${todayStr}T23:59:59Z`

      const { data: existing } = await supabase
        .schema('comms')
        .from('sms_messages')
        .select('id')
        .eq('user_id', user.id)
        .eq('type', 'start-soon')
        .gte('scheduled_for', todayStartISO)
        .lte('scheduled_for', todayEndISO)
        .limit(1)

      if (existing && existing.length > 0) continue

      // Replace keywords
      const text = matchingTemplate.text.replace(/_CLIENTNAME_/g, user.name || 'there')

      // Schedule based on template hour or default to 4 PM UTC
      // Note: Scheduling time is still in UTC for server-side processing
      const scheduledFor = new Date(`${todayStr}T00:00:00Z`)
      if (matchingTemplate.hour !== null) {
        scheduledFor.setUTCHours(matchingTemplate.hour, 0, 0, 0)
      } else {
        scheduledFor.setUTCHours(16, 0, 0, 0)
      }

      // If scheduled time is in the past, don't send
      if (scheduledFor < new Date()) {
        scheduledFor.setDate(scheduledFor.getDate() + 1)
      }

      const { error: insertError } = await supabase
        .schema('comms')
        .from('sms_messages')
        .insert({
          user_id: user.id,
          phone_number: user.phone_number,
          text: text,
          outbound: true,
          type: 'start-soon',
          scheduled_for: scheduledFor.toISOString()
        })

      if (insertError) {
        console.error(`Error scheduling pre-start SMS for user ${user.id}:`, insertError)
      } else {
        scheduled++
        console.log(`Scheduled ${messageType} SMS (day ${dayKey}) for user ${user.id}`)
      }
    }

    console.log(`[sms_start_soon_schedule] success | users=${users.length} scheduled=${scheduled} skippedNoCheckIn=${skippedNoCheckIn} skippedNoBreak=${skippedNoBreak} skippedNoTemplate=${skippedNoTemplate}`)

    return new Response(
      JSON.stringify({
        message: 'Pre-start scheduling complete',
        scheduled,
        skipped: {
          noCheckIn: skippedNoCheckIn,
          noBreak: skippedNoBreak,
          noTemplate: skippedNoTemplate
        }
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error(`[sms_start_soon_schedule] error |`, error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : String(error) }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

/* To invoke locally:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_start_soon_schedule' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json'

*/
