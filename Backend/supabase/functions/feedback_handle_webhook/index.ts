// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

// Supabase client setup
const supabaseUrl = Deno.env.get('SUPABASE_URL')
const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
const supabase = createClient(supabaseUrl, supabaseKey)

Deno.serve(async (req) => {
  try {
    // Handle CORS for web requests
    if (req.method === 'OPTIONS') {
      return new Response('ok', {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
        }
      })
    }

    // Get the content type to determine how to parse
    const contentType = req.headers.get('content-type') || ''

    let requestData: any = {}

    if (contentType.includes('multipart/form-data')) {
      // Handle form data (JotForm webhook)
      const formData = await req.formData()

      // Convert FormData to regular object
      for (const [key, value] of formData.entries()) {
        requestData[key] = value
      }

      // Parse the rawRequest field if it exists (contains the actual form responses)
      if (requestData.rawRequest) {
        try {
          const rawRequestData = JSON.parse(requestData.rawRequest as string)
          requestData.parsedRawRequest = rawRequestData
        } catch (e) {
          console.log("Could not parse rawRequest as JSON:", e)
        }
      }

    } else if (contentType.includes('application/json')) {
      // Handle JSON data
      requestData = await req.json()
    } else {
      // Handle other content types
      const rawBody = await req.text()
      requestData.rawText = rawBody
    }

    // Parse feedback data from form fields
    let userId: string | null = null
    let feedbackData: any = {}
    let feedbackType = 'form_default'

    if (requestData.pretty && requestData.rawRequest) {
      // Parse the pretty format for human-readable Q&A pairs
      const prettyData = requestData.pretty as string
      const prettyPairs = prettyData.split(', ')
      const humanReadableResponses: Record<string, string> = {}

      for (const pair of prettyPairs) {
        const colonIndex = pair.indexOf(':')
        if (colonIndex === -1) continue

        const key = pair.substring(0, colonIndex).trim()
        const value = pair.substring(colonIndex + 1).trim()

        if (key.toLowerCase() === 'user id') {
          userId = value === '' ? null : value
        } else if (key.toLowerCase() === 'type') {
          feedbackType = value === '' ? 'form_default' : value
        } else {
          humanReadableResponses[key] = value
        }
      }

      // Also parse the raw request data for structured field IDs
      const rawData = requestData.parsedRawRequest || {}

      // Create a comprehensive feedback object
      feedbackData = {
        form_title: requestData.formTitle || 'Feedback Form',
        submission_id: requestData.submissionID,
        responses: humanReadableResponses,
        raw_field_data: {
          // Extract the question fields (q3_, q4_, etc.)
          ...Object.fromEntries(
            Object.entries(rawData).filter(([key]) => key.startsWith('q'))
          )
        },
        submission_metadata: {
          form_id: requestData.formID,
          username: requestData.username,
          ip: requestData.ip,
          time_to_submit: rawData.timeToSubmit,
          submit_date: rawData.submitDate
        }
      }
    }

    // Insert into comms.feedback table
    const { data, error } = await supabase
      .schema('comms')
      .from('feedback')
      .insert([
        {
          user_id: userId,
          feedback: JSON.stringify(feedbackData),
          type: feedbackType
        }
      ])
      .select()

    if (error) {
      console.error('Database insert error:', error)
      throw new Error(`Database insert failed: ${error.message}`)
    }

    // Log event if user_id exists
    if (userId) {
      const { error: eventError } = await supabase
        .from('events')
        .insert([
          {
            user_id: userId,
            event: 'submitted_feedback',
            extra_data: {
              feedback_id: data[0].id,
              feedback_type: feedbackType,
              form_title: feedbackData.form_title || null,
              submission_id: feedbackData.submission_id || null
            }
          }
        ])

      if (eventError) {
        console.error('Failed to log event:', eventError)
        // Don't throw error here - feedback was saved successfully
      }
    }

    // Return success response
    return new Response(
      JSON.stringify({
        success: true,
        message: 'Feedback successfully processed and saved',
        data: {
          inserted_record: data[0],
          parsed_feedback: feedbackData,
          user_id: userId,
          type: feedbackType
        }
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      }
    )

  } catch (error) {
    console.error('Error processing feedback:', error)

    return new Response(
      JSON.stringify({
        success: false,
        error: error.message
      }),
      {
        status: 500,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      }
    )
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/feedback_handle_webhook' \
    --header 'Content-Type: application/json' \
    --data '{
      "userID": "test-user-123",
      "feedback": "This is a test feedback message",
      "type": "bug_report"
    }'

*/
