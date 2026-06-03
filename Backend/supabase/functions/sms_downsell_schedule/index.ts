import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

// Constants
const HEADERS = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
}

interface ResponseData {
  status: number;
  message: string;
  data?: any;
}

const createResponse = (data: ResponseData, status: number): Response => {
  return new Response(
    JSON.stringify(data),
    {
      status,
      headers: HEADERS
    }
  )
}

const handler = async (req: Request): Promise<Response> => {
  try {
    // Handle CORS preflight requests
    if (req.method === 'OPTIONS') {
      return new Response(null, { status: 200, headers: HEADERS })
    }

    if (req.method !== 'POST') {
      return createResponse({
        status: 405,
        message: 'Method not allowed'
      }, 405)
    }

    // Parse request body to get user_id
    const { user_id } = await req.json()

    if (!user_id || typeof user_id !== 'string') {
      return createResponse({
        status: 400,
        message: 'user_id is required and must be a string'
      }, 400)
    }

    console.log(`Scheduling downsell messages for user: ${user_id}`)

    const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

    // 1. Check if downsell campaign exists and is active
    const { data: campaign, error: campaignError } = await supabase
      .schema('library')
      .from('campaigns')
      .select('active')
      .eq('id', 'downsell')
      .single()

    if (campaignError || !campaign) {
      console.log('Downsell campaign not found - skipping')
      return createResponse({
        status: 200,
        message: 'Downsell campaign not found - skipping'
      }, 200)
    }

    if (!campaign.active) {
      console.log('Downsell campaign is inactive - skipping')
      return createResponse({
        status: 200,
        message: 'Downsell campaign is inactive - skipping'
      }, 200)
    }

    // 2. Get user details (including name and platform for placeholder replacement and filtering)
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('name, platform, logging_id, phone_number')
      .eq('id', user_id)
      .single()

    if (userError || !userData) {
      console.error('Error fetching user data:', userError)
      return createResponse({
        status: 404,
        message: 'User not found'
      }, 404)
    }

    // 3. Check if user has a phone number
    if (!userData.phone_number) {
      console.log(`Skipping downsell messages for user ${user_id} - no phone number`)
      return createResponse({
        status: 200,
        message: 'Downsell messages not scheduled - user has no phone number'
      }, 200)
    }

    // 4. Check if user is on iOS platform - only schedule downsell messages for iOS users
    if (userData.platform !== 'ios') {
      console.log(`Skipping downsell messages for user ${user_id} - platform: ${userData.platform}`)
      return createResponse({
        status: 200,
        message: `Downsell messages not scheduled - user is on ${userData.platform} platform`,
        data: {
          scheduled_count: 0,
          reason: 'platform_not_ios',
          user_platform: userData.platform
        }
      }, 200)
    }

    // 5. Get all downsell messages from library.sms_downsell
    const { data: downsellMessages, error: messagesError } = await supabase
      .schema('library')
      .from('sms_downsell')
      .select('*')
      .eq('active', true)
      .order('day_offset', { ascending: true })

    if (messagesError) {
      console.error('Error fetching downsell messages:', messagesError)
      return createResponse({
        status: 500,
        message: 'Failed to fetch downsell messages'
      }, 500)
    }

    if (!downsellMessages || downsellMessages.length === 0) {
      console.log('No downsell messages found to schedule')
      return createResponse({
        status: 200,
        message: 'No downsell messages to schedule',
        data: { scheduled_count: 0 }
      }, 200)
    }

    // 6. Clear existing downsell messages first using type-based clearing
    console.log('Clearing existing downsell messages...')
    const { error: clearError } = await supabase.rpc('sms_clear_by_type', {
      user_id: user_id,
      message_type: 'downsell'
    })

    if (clearError) {
      console.warn('Warning: Failed to clear existing downsell messages:', clearError)
      // Continue with scheduling even if clearing fails
    }

    // 7. Schedule all downsell messages
    let scheduledCount = 0
    const scheduledMessages: any[] = []

    for (const template of downsellMessages) {
      // Replace _CLIENTNAME_ with actual user name
      const personalizedMessage = template.message.replace(/_CLIENTNAME_/g, userData.name)

      // Calculate offset minutes: 24 hours padding + day_offset days
      const offsetMinutes = (24 * 60) + (template.day_offset * 24 * 60) + (template.min_offset * 60) // 24 hours + day_offset in minutes + min_offset in minutes

      // Schedule the message using the sms_schedule_user RPC with downsell type
      const { error: scheduleError } = await supabase.rpc('sms_schedule_user', {
        sms_data: {
          message: personalizedMessage,
          offset_minutes: offsetMinutes,
          type: `downsell_${template.type}`
        },
        user_id: user_id
      })

      if (scheduleError) {
        console.error(`Failed to schedule message "${template.type}":`, scheduleError)
        // Continue with other messages even if one fails
      } else {
        scheduledCount++
        scheduledMessages.push({
          type: template.type,
          day_offset: template.day_offset,
          min_offset: template.min_offset,
          scheduled_for_minutes: offsetMinutes,
          message_preview: personalizedMessage.substring(0, 50) + (personalizedMessage.length > 50 ? '...' : '')
        })

        console.log(`Scheduled message "${template.type}" for ${template.day_offset} days + ${template.min_offset} minutes + 1 day padding`)
      }
    }

    console.log(`Successfully scheduled ${scheduledCount} out of ${downsellMessages.length} downsell messages`)

    // Log scheudled_downsell_messages to events table for user
    if (userData.logging_id && userData.logging_id.length > 0) {

      console.log(`Logging scheduled_downsell_messages to events table for user: ${userData.logging_id[userData.logging_id.length - 1]}`)

      const { error: eventError } = await supabase
        .from('events')
        .insert({
          user_id: userData.logging_id[userData.logging_id.length - 1],
          event: 'scheduled_downsell_messages',
          extra_data: { scheduled_count: scheduledCount }
        })

      if (eventError) {
        console.error('Failed to log event:', eventError)
      }
    }

    return createResponse({
      status: 200,
      message: `Successfully scheduled ${scheduledCount} downsell messages`,
      data: {
        scheduled_count: scheduledCount,
        total_messages: downsellMessages.length,
        scheduled_messages: scheduledMessages
      }
    }, 200)

  } catch (error) {
    console.error('Error in sms_downsell_schedule:', error)
    return createResponse({
      status: 500,
      message: 'Internal server error'
    }, 500)
  }
}

Deno.serve(handler)

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_downsell_schedule' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"user_id":"1"}'

*/
