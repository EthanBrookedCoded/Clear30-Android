//
// peer_day_schedule
// Runs daily via pg_cron to send scheduled peer support messages
// Checks library.peer_day templates and inserts matching messages into comms.peer_messages
// RUNS ON CRON JOB
//

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { getTodayInTimezone, getTodayStartISO, getValidTimezone, parseDateYYYYMMDD } from '../shared/utils/dateUtils.ts'
import { getCurrentProgramInfo, ProgramBreak } from '../shared/utils/programUtils.ts'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

type User = {
  id: string
  name: string
  timezone: string | null
  program_breaks: ProgramBreak[]
  day_info: (string | DayData)[]
  created_at: string  // Account creation date for Life program day calculation
}

type DayData = {
  sober: boolean
  loggedCheckIns?: { id: string; completion: boolean }[]
}

type PeerDayTemplate = {
  id: number
  day: number
  program: string
  text: string
  question_id: string | null
  question_response: string | null
  variant_id: string | null
  variant_conditions: VariantConditions | null
}

type RangeCondition = {
  min: number
  max?: number
}

type VariantConditions = {
  check_in_days?: RangeCondition
  sober_days?: RangeCondition
}

type CheckInStats = {
  checkInDays: number
  soberDays: number
}

Deno.serve(async (req) => {
  const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

  try {
    // Get all users with peer support enabled (migrated OR new users on new app)
    // The flag is set by:
    // - Existing users: when they accept the migration popup
    // - New users: automatically by the new app
    const { data: users, error: usersError } = await supabase
      .from('users')
      .select('id, name, timezone, program_breaks, day_info, created_at')
      .eq('peer_support_migrated', true)

    if (usersError) {
      console.error('Error fetching users:', usersError)
      return new Response(JSON.stringify({ error: usersError.message }), { status: 500 })
    }

    if (!users || users.length === 0) {
      return new Response(JSON.stringify({ message: 'No users with peer support enabled', processed: 0 }), { status: 200 })
    }

    console.log(`Processing ${users.length} users with peer support`)

    let processed = 0
    let sent = 0
    let skippedNoCheckins = 0

    for (const user of users as User[]) {
      processed++

      // Skip users who haven't checked in yet (day_info is empty)
      if (!user.day_info || !Array.isArray(user.day_info) || user.day_info.length === 0) {
        skippedNoCheckins++
        continue
      }

      // Get current program info (Clear30 or Life) using user's timezone
      const userTimezone = getValidTimezone(user.timezone)
      const programInfo = getCurrentProgramInfo(user.program_breaks, user.created_at, userTimezone)
      if (!programInfo) {
        continue
      }

      const { type: programType, day: currentDay } = programInfo

      if (currentDay < 0) {
        // User hasn't started yet
        continue
      }

      // Get matching templates for this day
      const { data: templates, error: templatesError } = await supabase
        .schema('library')
        .from('peer_day')
        .select('*')
        .eq('day', currentDay)
        .eq('program', programType)
        .eq('active', true)

      if (templatesError) {
        console.error(`Error fetching templates for user ${user.id}, ${programType} day ${currentDay}:`, templatesError)
        continue
      }

      if (!templates || templates.length === 0) {
        // No templates for this day
        continue
      }

      // Check if we already sent a message for this day (in user's timezone)
      const todayStartISO = getTodayStartISO(userTimezone)

      const { data: existingMessages } = await supabase
        .schema('comms')
        .from('peer_messages')
        .select('id')
        .eq('user_id', user.id)
        .eq('type', 'automated')
        .gte('created_at', todayStartISO)
        .limit(1)

      if (existingMessages && existingMessages.length > 0) {
        // Already sent today
        continue
      }

      // Fetch user's assessment responses for template matching
      const { data: assessmentData } = await supabase
        .schema('programs')
        .from('program_assessment_responses')
        .select('responses')
        .eq('user_id', user.id)
        .order('timestamp', { ascending: false })
        .limit(1)
        .single()

      const userResponses: Record<string, any> = assessmentData?.responses || {}

      // Compute check-in stats if any templates use variant_conditions
      const hasVariantConditions = (templates as PeerDayTemplate[]).some(t => t.variant_conditions)
      let checkInStats: CheckInStats | null = null
      if (hasVariantConditions) {
        const todayStr = getTodayInTimezone(userTimezone)
        checkInStats = countCheckIns(user.day_info, todayStr, currentDay)
      }

      const template = selectVariant(templates as PeerDayTemplate[], userResponses, checkInStats)
      if (!template) {
        continue
      }

      // Replace placeholders in template text
      const text = replaceKeywords(template.text, user)

      // Insert into peer_messages (triggers push notification via trigger)
      const { error: insertError } = await supabase
        .schema('comms')
        .from('peer_messages')
        .insert({
          user_id: user.id,
          text: text,
          outbound: true,
          type: 'automated',
          template_id: template.id
        })

      if (insertError) {
        // Unique constraint violation means message was already sent today
        if (insertError.code === '23505') {
          console.log(`Skipping ${programType} day ${currentDay} - duplicate prevented by constraint for user ${user.id}`)
          continue
        }
        console.error(`Error inserting message for user ${user.id}:`, insertError)
        continue
      }

      sent++
      console.log(`Sent ${programType} day ${currentDay} message to user ${user.id}`)
    }

    console.log(`[peer_day_schedule] success | users=${users.length} sent=${sent} skippedNoCheckins=${skippedNoCheckins}`)

    return new Response(
      JSON.stringify({
        message: 'Peer day schedule complete',
        processed,
        sent,
        skippedNoCheckins
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error(`[peer_day_schedule] error |`, error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : String(error) }),
      { status: 500 }
    )
  }
})

function selectVariant(
  templates: PeerDayTemplate[],
  userResponses: Record<string, any>,
  checkInStats: CheckInStats | null
): PeerDayTemplate | null {
  // Split into variant_conditions-based, question-based, and fallback templates
  const variantConditionTemplates = templates
    .filter(t => t.variant_conditions)
    .sort((a, b) => (a.variant_id || '').localeCompare(b.variant_id || ''))
  const questionTemplates = templates.filter(t => !t.variant_conditions && t.question_id && t.question_response)
  const fallbackTemplates = templates.filter(t => !t.variant_conditions && (!t.question_id || !t.question_response))

  // 1. Try variant_conditions matching (sorted by variant_id for priority ordering)
  if (variantConditionTemplates.length > 0 && checkInStats) {
    for (const template of variantConditionTemplates) {
      if (matchesVariantConditions(template.variant_conditions!, checkInStats)) {
        return template
      }
    }
  }

  // 2. Try question_id/question_response matching from assessment data
  for (const template of questionTemplates) {
    const userAnswer = userResponses[template.question_id!]
    if (matchesResponse(userAnswer, template.question_response!)) {
      return template
    }
  }

  // 3. Fallback
  return fallbackTemplates[0] || null
}

function matchesVariantConditions(conditions: VariantConditions, stats: CheckInStats): boolean {
  if (conditions.check_in_days) {
    const { min, max } = conditions.check_in_days
    if (stats.checkInDays < min) return false
    if (max !== undefined && stats.checkInDays > max) return false
  }

  if (conditions.sober_days) {
    const { min, max } = conditions.sober_days
    if (stats.soberDays < min) return false
    if (max !== undefined && stats.soberDays > max) return false
  }

  return true
}

function matchesResponse(userAnswer: any, expectedResponse: string): boolean {
  if (userAnswer === undefined || userAnswer === null) return false

  if (Array.isArray(userAnswer)) {
    return userAnswer.includes(expectedResponse)
  }

  return userAnswer === expectedResponse
}

/**
 * Count check-in days and sober days within the first N days of a program.
 * day_info is structured as [dateStr, DayData, dateStr, DayData, ...]
 */
function countCheckIns(
  dayInfo: (string | DayData)[],
  todayStr: string,
  currentDay: number
): CheckInStats {
  // Calculate the program start date: today minus currentDay days
  const todayDate = parseDateYYYYMMDD(todayStr)
  const startDate = new Date(todayDate.getTime() - currentDay * 24 * 60 * 60 * 1000)

  // Build set of dates from day 0 to day (currentDay - 1)
  const programDates = new Set<string>()
  for (let d = 0; d < currentDay; d++) {
    const date = new Date(startDate.getTime() + d * 24 * 60 * 60 * 1000)
    programDates.add(date.toISOString().slice(0, 10))
  }

  let checkInDays = 0
  let soberDays = 0

  // Walk through day_info pairs
  for (let i = 0; i < dayInfo.length; i += 2) {
    const dateStr = dayInfo[i] as string
    if (!programDates.has(dateStr)) continue
    if (i + 1 >= dayInfo.length) continue

    const data = dayInfo[i + 1] as DayData
    if (data.sober !== undefined) {
      checkInDays++
      if (data.sober) soberDays++
    }
  }

  return { checkInDays, soberDays }
}

function replaceKeywords(text: string, user: User): string {
  let result = text

  // Replace _CLIENTNAME_ with user's name
  result = result.replace(/_CLIENTNAME_/g, user.name || 'there')

  return result
}

/* To invoke locally:

  1. Run `supabase start`
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/peer_day_schedule' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json'

*/
