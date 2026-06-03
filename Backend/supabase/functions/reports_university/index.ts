// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const supabase = createClient(supabaseUrl, supabaseServiceKey)

interface RequestBody {
  domain: string
  quarter?: string // Q1, Q2, Q3, Q4
  year?: number // Optional year, defaults to current year
}

interface QuarterDates {
  startDate: string
  endDate: string
  quarter: string
  year: number
}


// Calculate quarter dates
function getQuarterDates(quarter?: string, year?: number): QuarterDates {
  const now = new Date()
  const currentYear = year || now.getFullYear()
  const currentMonth = now.getMonth() + 1 // 1-12

  let targetQuarter: string
  let targetYear = currentYear

  if (quarter) {
    targetQuarter = quarter.toUpperCase()
  } else {
    // Determine current quarter
    if (currentMonth >= 1 && currentMonth <= 3) {
      targetQuarter = 'Q1'
    } else if (currentMonth >= 4 && currentMonth <= 6) {
      targetQuarter = 'Q2'
    } else if (currentMonth >= 7 && currentMonth <= 9) {
      targetQuarter = 'Q3'
    } else {
      targetQuarter = 'Q4'
    }
  }

  let startMonth: number, endMonth: number

  switch (targetQuarter) {
    case 'Q1':
      startMonth = 1
      endMonth = 3
      break
    case 'Q2':
      startMonth = 4
      endMonth = 6
      break
    case 'Q3':
      startMonth = 7
      endMonth = 9
      break
    case 'Q4':
      startMonth = 10
      endMonth = 12
      break
    default:
      throw new Error(`Invalid quarter: ${targetQuarter}. Must be Q1, Q2, Q3, or Q4`)
  }

  const startDate = new Date(targetYear, startMonth - 1, 1)
  const endDate = new Date(targetYear, endMonth, 0, 23, 59, 59, 999) // Last day of month

  return {
    startDate: startDate.toISOString(),
    endDate: endDate.toISOString(),
    quarter: targetQuarter,
    year: targetYear
  }
}

// Check if user is admin
async function checkAdmin(authId: string): Promise<boolean> {
  const { data, error } = await supabase
    .schema('platform')
    .from('admins')
    .select('id')
    .eq('auth_id', authId)
    .single()

  if (error || !data) {
    return false
  }

  return true
}

// Convert days-using range to numeric average
function daysUsingToNumber(daysUsing: string | string[] | null): number | null {
  if (!daysUsing) return null

  // Handle array format (JSONB arrays from assessment responses)
  let daysUsingStr: string
  if (Array.isArray(daysUsing)) {
    if (daysUsing.length === 0) return null
    daysUsingStr = String(daysUsing[0])
  } else {
    daysUsingStr = String(daysUsing)
  }

  // Handle ranges like "6-7 days a week" or "3-4 days a week"
  const rangeMatch = daysUsingStr.match(/(\d+)-(\d+)/)
  if (rangeMatch) {
    const min = parseInt(rangeMatch[1])
    const max = parseInt(rangeMatch[2])
    return (min + max) / 2
  }

  // Handle single numbers like "0" or "7"
  const singleMatch = daysUsingStr.match(/(\d+)/)
  if (singleMatch) {
    return parseInt(singleMatch[1])
  }

  return null
}

// Parse day_info JSONB to count check-ins and calculate sober stats
function parseDayInfo(dayInfo: any): {
  totalCheckIns: number
  soberDays: number
  longestStreak: number
} {
  if (!dayInfo || !Array.isArray(dayInfo)) {
    return { totalCheckIns: 0, soberDays: 0, longestStreak: 0 }
  }

  let totalCheckIns = 0
  let soberDays = 0
  let currentStreak = 0
  let longestStreak = 0

  for (let i = 0; i < dayInfo.length; i++) {
    const item = dayInfo[i]

    // Check if it's an object with sober field (not a date string)
    if (typeof item === 'object' && item !== null && 'sober' in item) {
      totalCheckIns++

      if (item.sober === true) {
        soberDays++
        currentStreak++
        longestStreak = Math.max(longestStreak, currentStreak)
      } else {
        currentStreak = 0
      }
    } else {
      // Reset streak on date strings
      currentStreak = 0
    }
  }

  return { totalCheckIns, soberDays, longestStreak }
}

// Calculate days using per week from check-ins
// Returns days per week based on check-in frequency
function calculateDaysUsingFromCheckIns(dayInfo: any): number | null {
  if (!dayInfo || !Array.isArray(dayInfo)) {
    return null
  }

  let totalCheckIns = 0
  let daysUsed = 0

  for (let i = 0; i < dayInfo.length; i++) {
    const item = dayInfo[i]

    // Check if it's an object with sober field (not a date string)
    if (typeof item === 'object' && item !== null && 'sober' in item) {
      totalCheckIns++
      if (item.sober === false) {
        daysUsed++
      }
    }
  }

  if (totalCheckIns === 0) {
    return null
  }

  // Calculate days per week: (days used / total check-ins) * 7
  // This normalizes the frequency to a weekly basis
  const daysPerWeek = (daysUsed / totalCheckIns) * 7
  return daysPerWeek
}

// CORS headers for all responses
const corsHeaders = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*'
}

serve(async (req) => {
  try {
    // Handle CORS
    if (req.method === 'OPTIONS') {
      return new Response(null, {
        status: 200,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
        },
      })
    }

    // Check authentication
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: 'No authorization header provided' }),
        { status: 401, headers: corsHeaders }
      )
    }

    const token = authHeader.replace('Bearer ', '').trim()
    const { data: authData, error: authError } = await supabase.auth.getUser(token)

    if (authError || !authData?.user) {
      return new Response(
        JSON.stringify({ error: 'Invalid or expired token' }),
        { status: 401, headers: corsHeaders }
      )
    }

    // Check if user is admin
    const isAdmin = await checkAdmin(authData.user.id)
    if (!isAdmin) {
      return new Response(
        JSON.stringify({ error: 'Admin access required' }),
        { status: 403, headers: corsHeaders }
      )
    }

    // Parse request body
    const body: RequestBody = await req.json()

    if (!body.domain) {
      return new Response(
        JSON.stringify({ error: 'Domain is required' }),
        { status: 400, headers: corsHeaders }
      )
    }

    // Calculate quarter dates
    const quarterDates = getQuarterDates(body.quarter, body.year)

    // Get users with email ending in the domain
    // Query users where email ends with @domain
    const emailDomain = `@${body.domain}`
    const { data: domainUsers, error: usersError } = await supabase
      .from('users')
      .select('id, auth_id, email, created_at')
      .like('email', `%${emailDomain}`)

    if (usersError) {
      throw new Error(`Failed to get domain users: ${usersError.message}`)
    }

    if (!domainUsers || domainUsers.length === 0) {
      return new Response(
        JSON.stringify({ error: `No users found for domain: ${body.domain}` }),
        { status: 404, headers: corsHeaders }
      )
    }

    // Extract user IDs
    const userIds = domainUsers.map(u => u.id)

    // Get university name
    const { data: domainData, error: domainError } = await supabase
      .schema('payment')
      .from('domain_allowlist')
      .select('org')
      .eq('domain', body.domain)
      .single()

    if (domainError) {
      throw new Error(`Failed to get domain info: ${domainError.message}`)
    }

    const universityName = domainData?.org || body.domain

    // ===== 1. USER SIGNUP & DEMOGRAPHICS =====

    // 1.1 Total users signed up (quarter + cumulative)
    // Filter domainUsers by quarter date range
    const quarterUsers = domainUsers.filter(user => {
      if (!user.created_at) return false
      const createdAt = new Date(user.created_at)
      const startDate = new Date(quarterDates.startDate)
      const endDate = new Date(quarterDates.endDate)
      return createdAt >= startDate && createdAt <= endDate
    })

    const totalUsersQuarter = quarterUsers.length
    const totalUsersCumulative = domainUsers.length

    // 1.2 Signup dates (already have from quarterUsers)
    const signupDates = quarterUsers.map(u => u.created_at)

    // 1.3 University name (already have)

    // 1.5 Initial Goals (Top 5)
    let clear30Assessments: any[] = []
    if (userIds.length > 0) {
      const { data, error: clear30Error } = await supabase
        .schema('programs')
        .from('program_assessment_responses')
        .select('user_id, responses')
        .eq('assessment', 'clear30')
        .in('user_id', userIds)

      if (clear30Error) {
        throw new Error(`Failed to get clear30 assessments: ${clear30Error.message}`)
      }
      clear30Assessments = data || []
    }

    const goalCounts: Record<string, number> = {}
    const triggerCounts: Record<string, number> = {}
    const breakReasonCounts: Record<string, number> = {}
    const moneySpentValues: number[] = []
    const daysUsingValues: string[] = []

    clear30Assessments?.forEach(assessment => {
      const responses = assessment.responses as any

      // Goals
      if (responses.Goal30 && Array.isArray(responses.Goal30)) {
        responses.Goal30.forEach((goal: string) => {
          goalCounts[goal] = (goalCounts[goal] || 0) + 1
        })
      }

      // Triggers
      if (responses.Trigger && Array.isArray(responses.Trigger)) {
        responses.Trigger.forEach((trigger: string) => {
          triggerCounts[trigger] = (triggerCounts[trigger] || 0) + 1
        })
      }

      // Break reasons
      if (responses['Break-Reason'] && Array.isArray(responses['Break-Reason'])) {
        responses['Break-Reason'].forEach((reason: string) => {
          breakReasonCounts[reason] = (breakReasonCounts[reason] || 0) + 1
        })
      }

      // Money spent
      if (responses['Money-Spent']) {
        const moneyStr = String(responses['Money-Spent'])
        const moneyNum = parseFloat(moneyStr)
        if (!isNaN(moneyNum)) {
          moneySpentValues.push(moneyNum)
        }
      }

      // Days using - handle array format
      if (responses['Days-Using']) {
        const daysUsing = responses['Days-Using']
        if (Array.isArray(daysUsing)) {
          daysUsing.forEach((val: string) => daysUsingValues.push(String(val)))
        } else {
          daysUsingValues.push(String(daysUsing))
        }
      }
    })

    // Count unique days using values
    const daysUsingCounts: Record<string, number> = {}
    daysUsingValues.forEach(val => {
      daysUsingCounts[val] = (daysUsingCounts[val] || 0) + 1
    })
    const daysUsedPerWeekWithCounts = Object.entries(daysUsingCounts)
      .sort(([, a], [, b]) => b - a)
      .map(([value, count]) => ({ value, count }))

    const top5Goals = Object.entries(goalCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 5)
      .map(([goal, count]) => ({ name: goal, count: count }))

    const top5Triggers = Object.entries(triggerCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 5)
      .map(([trigger, count]) => ({ name: trigger, count: count }))

    const top5BreakReasons = Object.entries(breakReasonCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 5)
      .map(([reason, count]) => ({ name: reason, count: count }))

    const avgMoneySpent = moneySpentValues.length > 0
      ? moneySpentValues.reduce((a, b) => a + b, 0) / moneySpentValues.length
      : null

    // ===== 2. ENGAGEMENT & ACTIVITY DATA =====

    // 2.1 Check-ins
    let usersWithDayInfo: any[] = []
    if (userIds.length > 0) {
      const { data, error: dayInfoError } = await supabase
        .from('users')
        .select('id, day_info')
        .in('id', userIds)

      if (dayInfoError) {
        throw new Error(`Failed to get day_info: ${dayInfoError.message}`)
      }
      usersWithDayInfo = data || []
    }

    let totalCheckIns = 0
    let totalSoberDays = 0
    const streakLengths: number[] = []

    usersWithDayInfo.forEach(user => {
      const stats = parseDayInfo(user.day_info)
      totalCheckIns += stats.totalCheckIns
      totalSoberDays += stats.soberDays
      streakLengths.push(stats.longestStreak)
    })

    const avgCheckIns = userIds.length > 0 ? totalCheckIns / userIds.length : 0
    const avgSoberStreak = streakLengths.length > 0
      ? streakLengths.reduce((a, b) => a + b, 0) / streakLengths.length
      : 0
    const percentDaysSober = totalCheckIns > 0 ? (totalSoberDays / totalCheckIns) * 100 : 0
    const percentDaysSoberBreakdown = {
      percentage: percentDaysSober,
      numerator: totalSoberDays,
      denominator: totalCheckIns,
      display: `${totalSoberDays}/${totalCheckIns} (${percentDaysSober.toFixed(1)}%)`
    }

    // 2.3 Community Engagement
    let posts: any[] = []
    let comments: any[] = []
    let reactions: any[] = []

    if (userIds.length > 0) {
      const [{ data: postsData, error: postsError }, { data: commentsData, error: commentsError }, { data: reactionsData, error: reactionsError }] = await Promise.all([
        supabase.schema('community').from('posts').select('id, user_id').in('user_id', userIds).eq('is_hidden', false),
        supabase.schema('community').from('comments').select('id, user_id').in('user_id', userIds),
        supabase.schema('community').from('reactions').select('id, user_id').in('user_id', userIds)
      ])

      if (postsError) {
        throw new Error(`Failed to get posts: ${postsError.message}`)
      }
      if (commentsError) {
        throw new Error(`Failed to get comments: ${commentsError.message}`)
      }
      if (reactionsError) {
        throw new Error(`Failed to get reactions: ${reactionsError.message}`)
      }

      posts = postsData || []
      comments = commentsData || []
      reactions = reactionsData || []
    }

    const totalPosts = posts.length
    const totalComments = comments.length
    const totalReactions = reactions.length

    const communityUserIds = new Set([
      ...posts.map(p => p.user_id),
      ...comments.map(c => c.user_id)
    ])
    const percentUsersPostedOrCommented = userIds.length > 0
      ? (communityUserIds.size / userIds.length) * 100
      : 0
    const percentUsersPostedOrCommentedBreakdown = {
      percentage: percentUsersPostedOrCommented,
      numerator: communityUserIds.size,
      denominator: userIds.length,
      display: `${communityUserIds.size}/${userIds.length} (${percentUsersPostedOrCommented.toFixed(1)}%)`
    }

    // 2.4 Group Participation
    let groupMembers: any[] = []
    let groupMessages: any[] = []
    let groupActivities: any[] = []

    if (userIds.length > 0) {
      const [{ data: groupMembersData, error: groupMembersError }, { data: groupMessagesData, error: groupMessagesError }, { data: groupActivitiesData, error: groupActivitiesError }] = await Promise.all([
        supabase.schema('groups').from('group_members').select('user_id').in('user_id', userIds),
        supabase.schema('groups').from('group_messages').select('id, user_id').in('user_id', userIds).eq('is_deleted', false),
        supabase.schema('groups').from('group_activity').select('id, user_id, activity').in('user_id', userIds).in('activity', ['sober', 'message'])
      ])

      if (groupMembersError) {
        throw new Error(`Failed to get group members: ${groupMembersError.message}`)
      }
      if (groupMessagesError) {
        throw new Error(`Failed to get group messages: ${groupMessagesError.message}`)
      }
      if (groupActivitiesError) {
        throw new Error(`Failed to get group activities: ${groupActivitiesError.message}`)
      }

      groupMembers = groupMembersData || []
      groupMessages = groupMessagesData || []
      groupActivities = groupActivitiesData || []
    }

    const groupUserIds = new Set(groupMembers.map(gm => gm.user_id))
    const percentUsersInGroups = userIds.length > 0
      ? (groupUserIds.size / userIds.length) * 100
      : 0
    const percentUsersInGroupsBreakdown = {
      percentage: percentUsersInGroups,
      numerator: groupUserIds.size,
      denominator: userIds.length,
      display: `${groupUserIds.size}/${userIds.length} (${percentUsersInGroups.toFixed(1)}%)`
    }

    const totalGroupMessages = groupMessages.length
    const totalGroupActivities = groupActivities.length
    const totalGroupInteractions = totalGroupMessages + totalGroupActivities
    const avgGroupInteractionsPerUser = groupUserIds.size > 0
      ? totalGroupInteractions / groupUserIds.size
      : 0

    // ===== 3. IN-APP SUPPORT INTERACTION =====

    // 3.1 Claire AI
    let claireMessages: any[] = []
    if (userIds.length > 0) {
      const { data, error: claireError } = await supabase
        .schema('claire')
        .from('messages')
        .select('id, user_id')
        .in('user_id', userIds)

      if (claireError) {
        throw new Error(`Failed to get Claire messages: ${claireError.message}`)
      }
      claireMessages = data || []
    }

    const totalClaireInteractions = claireMessages.length
    const claireUserIds = new Set(claireMessages.map(m => m.user_id))
    const avgClairePerUser = claireUserIds.size > 0
      ? totalClaireInteractions / claireUserIds.size
      : 0
    const percentUsersUsedClaire = userIds.length > 0
      ? (claireUserIds.size / userIds.length) * 100
      : 0
    const percentUsersUsedClaireBreakdown = {
      percentage: percentUsersUsedClaire,
      numerator: claireUserIds.size,
      denominator: userIds.length,
      display: `${claireUserIds.size}/${userIds.length} (${percentUsersUsedClaire.toFixed(1)}%)`
    }

    // 3.2 Dr. Fred
    let drFredMessages: any[] = []
    if (userIds.length > 0) {
      const { data, error: drFredError } = await supabase
        .schema('comms')
        .from('dr_fred')
        .select('id, user_id')
        .in('user_id', userIds)
        .eq('outbound', false)

      if (drFredError) {
        throw new Error(`Failed to get Dr. Fred messages: ${drFredError.message}`)
      }
      drFredMessages = data || []
    }

    const totalDrFredMessages = drFredMessages.length
    const drFredUserIds = new Set(drFredMessages.map(m => m.user_id))
    const avgDrFredPerUser = drFredUserIds.size > 0
      ? totalDrFredMessages / drFredUserIds.size
      : 0
    const percentUsersUsedDrFred = userIds.length > 0
      ? (drFredUserIds.size / userIds.length) * 100
      : 0
    const percentUsersUsedDrFredBreakdown = {
      percentage: percentUsersUsedDrFred,
      numerator: drFredUserIds.size,
      denominator: userIds.length,
      display: `${drFredUserIds.size}/${userIds.length} (${percentUsersUsedDrFred.toFixed(1)}%)`
    }

    // 3.3 Groups (already calculated above)

    // ===== 4. BEHAVIORAL OUTCOMES =====

    // 4.1 Goals Reached
    let lifeAssessments: any[] = []
    if (userIds.length > 0) {
      const { data, error: lifeError } = await supabase
        .schema('programs')
        .from('program_assessment_responses')
        .select('user_id, responses')
        .eq('assessment', 'life')
        .in('user_id', userIds)

      if (lifeError) {
        throw new Error(`Failed to get life assessments: ${lifeError.message}`)
      }
      lifeAssessments = data || []
    }

    let usersWithAtLeastOneGoal = 0
    let usersWithAllGoals = 0

    lifeAssessments.forEach(assessment => {
      const responses = assessment.responses as any
      const metKeys = Object.keys(responses).filter(k => k.endsWith('-Met'))

      if (metKeys.length === 0) return

      let hasAtLeastOne = false
      let hasAll = true

      metKeys.forEach(key => {
        const value = responses[key]
        if (Array.isArray(value) && (value.includes('Yes') || value.includes('Somewhat'))) {
          hasAtLeastOne = true
        } else {
          hasAll = false
        }
      })

      if (hasAtLeastOne) usersWithAtLeastOneGoal++
      if (hasAll) usersWithAllGoals++
    })

    const percentUsersAchievedOneGoal = lifeAssessments && lifeAssessments.length > 0
      ? (usersWithAtLeastOneGoal / lifeAssessments.length) * 100
      : 0
    const percentUsersAchievedOneGoalBreakdown = {
      percentage: percentUsersAchievedOneGoal,
      numerator: usersWithAtLeastOneGoal,
      denominator: lifeAssessments.length,
      display: `${usersWithAtLeastOneGoal}/${lifeAssessments.length} (${percentUsersAchievedOneGoal.toFixed(1)}%)`
    }
    const percentUsersAchievedAllGoals = lifeAssessments && lifeAssessments.length > 0
      ? (usersWithAllGoals / lifeAssessments.length) * 100
      : 0
    const percentUsersAchievedAllGoalsBreakdown = {
      percentage: percentUsersAchievedAllGoals,
      numerator: usersWithAllGoals,
      denominator: lifeAssessments.length,
      display: `${usersWithAllGoals}/${lifeAssessments.length} (${percentUsersAchievedAllGoals.toFixed(1)}%)`
    }

    // ===== 5. POST-ASSESSMENT =====

    // 5.1 Completion Rate
    const usersWith30CheckIns = usersWithDayInfo.filter(user => {
      const stats = parseDayInfo(user.day_info)
      return stats.totalCheckIns >= 30
    }).length

    const completionRate = userIds.length > 0
      ? (usersWith30CheckIns / userIds.length) * 100
      : 0
    const completionRateBreakdown = {
      percentage: completionRate,
      numerator: usersWith30CheckIns,
      denominator: userIds.length,
      display: `${usersWith30CheckIns}/${userIds.length} (${completionRate.toFixed(1)}%)`
    }

    // 5.2 Post-Assessment Metrics
    const postDaysUsingValues: string[] = []

    // Match clear30 and life assessments for money saved calculation
    const baselineMap = new Map<string, { moneySpent: number | null, daysUsing: number | null }>()
    const postMap = new Map<string, number | null>()

    clear30Assessments.forEach(assessment => {
      const responses = assessment.responses as any
      const moneySpent = responses['Money-Spent']
        ? parseFloat(String(responses['Money-Spent']))
        : null
      const daysUsing = daysUsingToNumber(responses['Days-Using'])
      baselineMap.set(assessment.user_id, { moneySpent, daysUsing })
    })

    lifeAssessments?.forEach(assessment => {
      const responses = assessment.responses as any
      if (responses['Days-Using']) {
        postDaysUsingValues.push(String(responses['Days-Using']))
        postMap.set(assessment.user_id, daysUsingToNumber(responses['Days-Using']))
      }
      if (responses['Mental-Health'] && responses['Mental-Health'] !== 'Not at all' && responses['Mental-Health'] !== null) {
        // Counted in mentalHealthImprovements below
      }
    })

    // Count mental health improvements
    let mentalHealthCount = 0

    lifeAssessments.forEach(assessment => {
      const responses = assessment.responses as any
      if (responses['Mental-Health'] && responses['Mental-Health'] !== 'Not at all' && responses['Mental-Health'] !== null) {
        mentalHealthCount++
      }
    })

    const percentMentalHealthImprovement = lifeAssessments && lifeAssessments.length > 0
      ? (mentalHealthCount / lifeAssessments.length) * 100
      : 0
    const percentMentalHealthImprovementBreakdown = {
      percentage: percentMentalHealthImprovement,
      numerator: mentalHealthCount,
      denominator: lifeAssessments.length,
      display: `${mentalHealthCount}/${lifeAssessments.length} (${percentMentalHealthImprovement.toFixed(1)}%)`
    }

    // Calculate money saved
    // Note: Baseline is days per week, post-assessment is days over 30 days
    // Need to normalize post-assessment to days per week: (days over 30) * (7/30)
    const moneySavedValues: number[] = []

    baselineMap.forEach((baseline, userId) => {
      const postDaysOver30 = postMap.get(userId)

      if (baseline.moneySpent && baseline.daysUsing !== null && postDaysOver30 !== null && postDaysOver30 !== undefined) {
        // Convert post-assessment from days over 30 to days per week
        const postDaysPerWeek = postDaysOver30 * (7 / 30)
        if (baseline.daysUsing > 0 && postDaysPerWeek < baseline.daysUsing) {
          const moneySaved = ((baseline.daysUsing - postDaysPerWeek) / baseline.daysUsing) * baseline.moneySpent * 4
          moneySavedValues.push(moneySaved)
        } else {
          moneySavedValues.push(0)
        }
      }
    })

    const avgMoneySaved = moneySavedValues.length > 0
      ? moneySavedValues.reduce((a, b) => a + b, 0) / moneySavedValues.length
      : 0

    // ===== 5.3 Decrease in Use Calculations =====

    // 1. Decrease in use from post-assessment (for users who completed post-assessment)
    // Note: Baseline is days per week, post-assessment is days over 30 days
    // Need to normalize post-assessment to days per week: (days over 30) * (7/30)
    const postAssessmentDecreaseValues: number[] = []
    
    baselineMap.forEach((baseline, userId) => {
      const postDaysOver30 = postMap.get(userId)
      
      // Only calculate if we have both baseline and post-assessment data
      if (baseline.daysUsing !== null && baseline.daysUsing !== undefined && 
          postDaysOver30 !== null && postDaysOver30 !== undefined && 
          baseline.daysUsing > 0) {
        // Convert post-assessment from days over 30 to days per week
        const postDaysPerWeek = postDaysOver30 * (7 / 30)
        const decrease = ((baseline.daysUsing - postDaysPerWeek) / baseline.daysUsing) * 100
        postAssessmentDecreaseValues.push(decrease)
      }
    })

    const avgPostAssessmentDecrease = postAssessmentDecreaseValues.length > 0
      ? postAssessmentDecreaseValues.reduce((a, b) => a + b, 0) / postAssessmentDecreaseValues.length
      : null
    const postAssessmentDecreaseBreakdown = avgPostAssessmentDecrease !== null ? {
      averagePercentage: avgPostAssessmentDecrease,
      userCount: postAssessmentDecreaseValues.length,
      display: `Average decrease: ${avgPostAssessmentDecrease.toFixed(1)}% (based on ${postAssessmentDecreaseValues.length} users)`
    } : null

    // 2. Decrease in use from check-ins (for all users with baseline assessment and check-ins)
    const checkInDecreaseValues: number[] = []
    
    // Create a map of user_id to check-in based days using
    const checkInDaysUsingMap = new Map<string, number | null>()
    usersWithDayInfo.forEach(user => {
      const daysUsingFromCheckIns = calculateDaysUsingFromCheckIns(user.day_info)
      if (daysUsingFromCheckIns !== null) {
        checkInDaysUsingMap.set(user.id, daysUsingFromCheckIns)
      }
    })

    // Compare baseline with check-in frequency
    baselineMap.forEach((baseline, userId) => {
      const checkInDays = checkInDaysUsingMap.get(userId)
      
      // Only calculate if we have both baseline and check-in data
      if (baseline.daysUsing !== null && baseline.daysUsing !== undefined && 
          checkInDays !== null && checkInDays !== undefined && 
          baseline.daysUsing > 0) {
        const decrease = ((baseline.daysUsing - checkInDays) / baseline.daysUsing) * 100
        checkInDecreaseValues.push(decrease)
      }
    })

    const avgCheckInDecrease = checkInDecreaseValues.length > 0
      ? checkInDecreaseValues.reduce((a, b) => a + b, 0) / checkInDecreaseValues.length
      : null
    const checkInDecreaseBreakdown = avgCheckInDecrease !== null ? {
      averagePercentage: avgCheckInDecrease,
      userCount: checkInDecreaseValues.length,
      display: `Average decrease: ${avgCheckInDecrease.toFixed(1)}% (based on ${checkInDecreaseValues.length} users)`
    } : null

    // ===== 6. AGGREGATE SUMMARY =====

    // Active users (at least one check-in per week during quarter)
    // For simplicity, we'll count users with at least one check-in during the quarter
    const activeUserIds = new Set(
      usersWithDayInfo.filter(user => {
        const stats = parseDayInfo(user.day_info)
        return stats.totalCheckIns > 0
      }).map(u => u.id)
    )
    const percentActiveUsers = userIds.length > 0
      ? (activeUserIds.size / userIds.length) * 100
      : 0
    const percentActiveUsersBreakdown = {
      percentage: percentActiveUsers,
      numerator: activeUserIds.size,
      denominator: userIds.length,
      display: `${activeUserIds.size}/${userIds.length} (${percentActiveUsers.toFixed(1)}%)`
    }

    // Build response
    const report = {
      domain: body.domain,
      universityName,
      quarter: quarterDates.quarter,
      year: quarterDates.year,
      period: {
        startDate: quarterDates.startDate,
        endDate: quarterDates.endDate
      },

      // 1. User Signup & Demographics
      userSignup: {
        info: "User signup and demographic information from the initial assessment (clear30)",
        totalUsersQuarter,
        totalUsersCumulative,
        signupDates,
        universityName,
        initialGoals: {
          info: "Top 5 most common goals selected by users at signup. Count represents number of users who selected this goal.",
          goals: top5Goals
        },
        baselineCannabisUse: {
          info: "Baseline cannabis use data from initial assessment",
          daysUsedPerWeek: {
            info: "Unique days used per week responses with count of how many users selected each option",
            values: daysUsedPerWeekWithCounts
          },
          averageMoneySpentPerWeek: avgMoneySpent,
          triggers: {
            info: "Top 5 most common triggers for cannabis use. Count represents number of users who selected this trigger.",
            triggers: top5Triggers
          },
          reasonsForBreak: {
            info: "Top 5 most common reasons for taking a break. Count represents number of users who selected this reason.",
            reasons: top5BreakReasons
          }
        }
      },

      // 2. Engagement & Activity Data
      engagement: {
        info: "User engagement metrics including check-ins, community participation, and group activity",
        checkInsCompleted: {
          info: "Total number of check-ins completed by all users and average per user",
          total: totalCheckIns,
          average: avgCheckIns
        },
        soberQuantification: {
          info: "Percentage of days users reported being sober out of total check-ins. Average sober streak is the average of each user's longest consecutive sober period.",
          percentDaysSober: percentDaysSoberBreakdown,
          averageSoberStreakLength: avgSoberStreak
        },
        communityEngagement: {
          info: "Community engagement metrics including posts, comments, and reactions",
          totalPosts: totalPosts,
          totalComments: totalComments,
          totalReactions: totalReactions,
          percentUsersPostedOrCommented: percentUsersPostedOrCommentedBreakdown
        },
        groupParticipation: {
          info: "Group participation metrics. Average interactions includes both messages and check-ins.",
          percentUsersJoinedGroup: percentUsersInGroupsBreakdown,
          averageGroupInteractionsPerUser: avgGroupInteractionsPerUser
        }
      },

      // 3. In-App Support Interaction
      supportInteraction: {
        info: "Metrics for in-app support features including Claire AI, Dr. Fred, and group interactions",
        claire: {
          info: "Claire AI chat interactions. Total includes both user and assistant messages.",
          totalInteractions: totalClaireInteractions,
          averagePerUser: avgClairePerUser,
          percentActiveUsersUsed: percentUsersUsedClaireBreakdown
        },
        drFred: {
          info: "Dr. Fred message interactions. Only counts user-initiated messages (outbound=false).",
          totalMessages: totalDrFredMessages,
          averagePerUser: avgDrFredPerUser,
          percentUsersUsed: percentUsersUsedDrFredBreakdown
        },
        groups: {
          info: "Group interactions including messages and check-ins",
          totalInteractions: totalGroupInteractions,
          averagePerActiveUser: avgGroupInteractionsPerUser
        }
      },

      // 4. Behavioral Outcomes
      behavioralOutcomes: {
        info: "Goal achievement metrics from post-assessment (life assessment). Goals are marked as achieved if response is 'Yes' or 'Somewhat'.",
        percentUsersAchievedOneGoal: percentUsersAchievedOneGoalBreakdown,
        percentUsersAchievedAllGoals: percentUsersAchievedAllGoalsBreakdown
      },

      // 5. Post-Assessment
      postAssessment: {
        info: "Post-assessment metrics from the life assessment. Completion rate is based on users with 30+ check-ins.",
        completionRate: completionRateBreakdown,
        selfReportedUse: {
          info: "Self-reported days using per week from post-assessment",
          values: postDaysUsingValues // Keep as text for display
        },
        decreaseInUse: {
          info: "Decrease in use calculated from baseline (clear30 assessment) and post-assessment (life assessment) days-using values. Only includes users who completed both assessments.",
          fromPostAssessment: postAssessmentDecreaseBreakdown
        },
        decreaseInUseFromCheckIns: {
          info: "Decrease in use calculated from baseline (clear30 assessment) and actual check-in frequency. Calculates days using per week from check-ins and compares to baseline.",
          fromCheckIns: checkInDecreaseBreakdown
        },
        averageMoneySaved: {
          info: "Average money saved calculated from baseline and post-assessment data. Based on decrease in days using and initial money spent over 4 weeks (30 days).",
          value: avgMoneySaved
        },
        percentMentalHealthImprovement: percentMentalHealthImprovementBreakdown,
        percentUsersMetGoals: percentUsersAchievedOneGoalBreakdown // Same as behavioral outcomes
      },

      // 6. Aggregate Summary
      summary: {
        info: "High-level aggregate metrics for the quarter",
        totalUsersOnboarded: totalUsersQuarter,
        percentActiveUsers: percentActiveUsersBreakdown,
        completionRate: completionRateBreakdown,
        percentDecreaseInUse: {
          info: "Decrease in use metrics from two sources: post-assessment (self-reported) and check-ins (actual behavior)",
          fromPostAssessment: postAssessmentDecreaseBreakdown,
          fromCheckIns: checkInDecreaseBreakdown
        },
        percentMentalHealthImprovement: percentMentalHealthImprovementBreakdown,
        percentMotivationImprovement: null, // Not in data
        averageMoneySaved: avgMoneySaved,
        percentUsersMetOneGoal: percentUsersAchievedOneGoalBreakdown,
        percentUsersMetAllGoals: percentUsersAchievedAllGoalsBreakdown,
        top5Triggers: {
          info: "Top 5 triggers with response counts",
          triggers: top5Triggers
        },
        top5ReasonsForUse: {
          info: "Top 5 reasons for use (same as triggers) with response counts",
          reasons: top5Triggers // Same as triggers
        },
        totalClaireInteractions: totalClaireInteractions,
        totalDrFredInteractions: totalDrFredMessages,
        totalCommunityPosts: totalPosts,
        totalCommunityComments: totalComments
      }
    }

    return new Response(
      JSON.stringify(report, null, 2),
      { status: 200, headers: corsHeaders }
    )

  } catch (error) {
    console.error('Error generating report:', error)
    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to generate report'
      }),
      { status: 500, headers: corsHeaders }
    )
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/reports_university' \
    --header 'Authorization: Bearer ADMIN_AUTH_JWT' \
    --header 'Content-Type: application/json' \
    --data '{
      "domain": "dartmouth.edu",
      "quarter": "Q2",
      "year": 2025
    }'

*/
