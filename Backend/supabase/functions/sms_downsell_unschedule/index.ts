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

interface DownsellMessage {
  id: number;
  day_offset: number;
  desc: string;
  message: string;
  created_at: string;
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

    console.log(`Unscheduling downsell messages for user: ${user_id}`)

    const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

    // 1. Unschedule all downsell messages for this user
    const { data, error } = await supabase.rpc('sms_clear_by_type', {
      user_id: user_id,
      message_type: 'downsell'
    })

    if (error) {
      console.error('Failed to unschedule downsell messages:', error)
      return createResponse({
        status: 500,
        message: 'Failed to unschedule downsell messages',
        data: { error: error.message }
      }, 500)
    } else {
      console.log(`Unscheduled all downsell messages for user ${user_id}`, data)
    }

    console.log(`Successfully unscheduled downsell messages`)

    // Log unscheduled_downsell_messages to events table for user

    // Get user data
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('logging_id')
      .eq('id', user_id)
      .single()

    if (userError) {
      console.error('Failed to get user data:', userError)
    }

    if (userData && userData.logging_id && userData.logging_id.length > 0) {
      const { error: eventError } = await supabase
        .from('events')
        .insert({
          user_id: userData.logging_id[userData.logging_id.length - 1],
          event: 'unscheduled_downsell_messages'
        })

      if (eventError) {
        console.error('Failed to log event:', eventError)
      }
    }

    return createResponse({
      status: 200,
      message: `Successfully unscheduled downsell messages`,
      data: {
        method: 'type_based_clearing'
      }
    }, 200)

  } catch (error) {
    console.error('Error in sms_downsell_unschedule:', error)
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

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/sms_downsell_unschedule' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"user_id":"1"}'

*/
