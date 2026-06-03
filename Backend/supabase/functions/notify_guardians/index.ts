// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { SMTPClient } from "https://deno.land/x/denomailer/mod.ts";

// SMTP Configuration
const SMTP_CONFIG = {
  hostname: "smtp.gmail.com",
  port: 465,
  username: "support@clear30.org",
  password: Deno.env.get("SMTP_PASSWORD")!,
  fromEmail: "support@clear30.org"
}

interface GuardianCode {
  code: string
  guardian_email: string
  creator: string
}

interface User {
  id: string
  name: string
  day_info: any[] | null
}

interface UserWeeklyStats {
  name: string
  soberDays: number
  nonSoberDays: number
  totalDays: number
  hasData: boolean
}

interface GuardianNotification {
  guardianEmail: string
  users: UserWeeklyStats[]
}

function parseUserDayInfo(dayInfo: any[] | null, daysBack: number = 8): UserWeeklyStats {
  if (!dayInfo || !Array.isArray(dayInfo)) {
    return {
      name: '',
      soberDays: 0,
      nonSoberDays: 0,
      totalDays: 0,
      hasData: false
    }
  }

  const today = new Date()
  const cutoffDate = new Date(today)
  cutoffDate.setDate(today.getDate() - daysBack)

  let soberDays = 0
  let nonSoberDays = 0
  let totalDays = 0

  // Parse the array structure: [date, dataObj, date, dataObj, ...]
  for (let i = 0; i < dayInfo.length; i += 2) {
    if (i + 1 >= dayInfo.length) break

    const dateStr = dayInfo[i]
    const dataObj = dayInfo[i + 1]

    if (typeof dateStr !== 'string' || !dataObj) continue

    const dayDate = new Date(dateStr)
    
    // Only count days within our time window
    if (dayDate >= cutoffDate && dayDate <= today) {
      totalDays++
      if (dataObj.sober === true) {
        soberDays++
      } else if (dataObj.sober === false) {
        nonSoberDays++
      }
    }
  }

  return {
    name: '',
    soberDays,
    nonSoberDays,
    totalDays,
    hasData: totalDays > 0
  }
}

function generateWeeklyReportText(guardianNotification: GuardianNotification): string {
  const { users } = guardianNotification
  
  if (users.length === 0) {
    return ''
  }

  let reportText = `📊 Weekly Progress Report\n\n`
  reportText += `Here's your weekly update for ${users.length} user${users.length > 1 ? 's' : ''}:\n\n`

  users.forEach(user => {
    reportText += `👤 ${user.name}:\n`
    
    if (!user.hasData) {
      reportText += `   No check-in data available for the past week\n\n`
    } else {
      reportText += `   ✅ Sober days: ${user.soberDays}\n`
      reportText += `   ❌ Non-sober days: ${user.nonSoberDays}\n`
      reportText += `   📅 Total days tracked: ${user.totalDays}\n\n`
    }
  })

  reportText += `This report covers the last 8 days.`

  return reportText
}

async function sendEmailNotification(guardianEmail: string, reportText: string): Promise<{ recipient: string, success: boolean, error?: string }> {
  const client = new SMTPClient({
    connection: {
      hostname: SMTP_CONFIG.hostname,
      port: SMTP_CONFIG.port,
      tls: true,
      auth: {
        username: SMTP_CONFIG.username,
        password: SMTP_CONFIG.password,
      }
    }
  });

  try {
    await client.send({
      from: SMTP_CONFIG.fromEmail,
      to: guardianEmail,
      subject: "Clear30 Weekly Progress Report",
      content: reportText,
    });
    
    await client.close();
    return { recipient: guardianEmail, success: true };
  } catch (error) {
    console.error('Error sending email to', guardianEmail, error);
    await client.close();
    return { recipient: guardianEmail, success: false, error: error.message };
  }
}

serve(async (req) => {
  try {
    // Initialize Supabase client
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Get all guardian codes
    const { data: guardianCodes, error: codesError } = await supabase
      .schema('guardian')
      .from('codes')
      .select('code, guardian_email, creator')

    if (codesError) {
      throw new Error(`Failed to fetch guardian codes: ${codesError.message}`)
    }

    if (!guardianCodes || guardianCodes.length === 0) {
      return new Response(JSON.stringify({ message: 'No guardian codes found' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    const notifications: GuardianNotification[] = []

    // Process each guardian
    for (const guardianCode of guardianCodes) {
      // Get users associated with this guardian code where notify_guardian = true
      // Order by timestamp desc to get most recent entries first
      const { data: codeUsers, error: codeUsersError } = await supabase
        .schema('guardian')
        .from('code_users')
        .select('user_id, timestamp')
        .eq('code', guardianCode.code)
        .eq('notify_guardian', true)
        .order('timestamp', { ascending: false })

      if (codeUsersError) {
        console.error(`Error fetching users for code ${guardianCode.code}:`, codeUsersError)
        continue
      }

      if (!codeUsers || codeUsers.length === 0) {
        continue // Skip guardians with no users to notify about
      }

      // Deduplicate by user_id, keeping only the most recent entry per user
      const seenUsers = new Set<string>()
      const uniqueCodeUsers = codeUsers.filter(cu => {
        if (seenUsers.has(cu.user_id)) {
          return false
        }
        seenUsers.add(cu.user_id)
        return true
      })

      const userIds = uniqueCodeUsers.map(cu => cu.user_id)

      // Get user details and day_info
      const { data: users, error: usersError } = await supabase
        .from('users')
        .select('id, name, day_info')
        .in('id', userIds)

      if (usersError) {
        console.error(`Error fetching user details for guardian ${guardianCode.guardian_email}:`, usersError)
        continue
      }

      if (!users || users.length === 0) {
        continue
      }

      // Process each user's weekly stats
      const userStats: UserWeeklyStats[] = users.map(user => {
        const stats = parseUserDayInfo(user.day_info)
        stats.name = user.name
        return stats
      })

      // Only create notification if there are users to report on
      if (userStats.length > 0) {
        notifications.push({
          guardianEmail: guardianCode.guardian_email,
          users: userStats
        })
      }
    }

    // Send emails to all guardians with users
    const emailResults = await Promise.all(
      notifications.map(async (notification) => {
        const reportText = generateWeeklyReportText(notification)
        if (reportText) {
          return await sendEmailNotification(notification.guardianEmail, reportText)
        }
        return { recipient: notification.guardianEmail, success: false, error: 'No report content generated' }
      })
    )

    return new Response(
      JSON.stringify({
        message: 'Guardian notifications processed',
        totalGuardians: guardianCodes.length,
        notificationsSent: notifications.length,
        emailResults
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Function error:', error)
    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to process guardian notifications'
      }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/notify_guardians' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{}'
*/
