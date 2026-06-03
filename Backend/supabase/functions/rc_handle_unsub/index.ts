// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
)

interface RevenueCatWebhookEvent {
  api_version: string
  event: {
    id: string
    type: 'CANCELLATION' | 'BILLING_ISSUE' | string
    event_timestamp_ms: number
    app_user_id: string
    aliases: string[]
    original_app_user_id: string
    product_id: string
    period_type: 'TRIAL' | 'NORMAL' | string
    purchased_at_ms: number
    expiration_at_ms: number
    environment: 'SANDBOX' | 'PRODUCTION'
    entitlement_id?: string
    entitlement_ids?: string[]
    is_family_share?: boolean
    country_code?: string
    app_id: string
    offer_code?: string
    currency?: string
    price?: number
    price_in_purchased_currency?: number
    takehome_percentage?: number
    commission_percentage?: number
    tax_percentage?: number
    transaction_id?: string
    original_transaction_id?: string
    auto_resume_at_ms?: number
    grace_period_expiration_at_ms?: number
    presented_offering_id?: string
    store?: 'APP_STORE' | 'PLAY_STORE' | 'AMAZON' | 'STRIPE' | 'PROMOTIONAL'
    subscriber_attributes?: Record<string, any>
  }
}

interface AssessmentResponse {
  id: number
  user_id: string
  assessment: string
  responses: Record<string, string | string[]>
  timestamp: string
}

// TRIAL CANCELLATION MESSAGES

function generateTrialCancellationMessageWithRandomness(userName: string, assessmentData?: AssessmentResponse, userId?: string): string[] {
  const randomValue = Math.random()
  if (false) {
    return generateInterviewTrialCancellationMessages(userName, assessmentData)
  } else if (randomValue < 0.5) {
    return generateFreeSurveyTrialCancellationMessages(userName, assessmentData, userId)
  } else {
    return generateTrialCancellationMessages(userName, assessmentData)
  }
}

// Message generation functions
function generateTrialCancellationMessages(userName: string, assessmentData?: AssessmentResponse): string[] {
  const name = userName || 'there'

  return [
    `Hey ${name}, this is Julian — saw you cancelled your Clear30 trial.\n\nTotally respect that. Just wanted to say I’m really glad you made it this far, and I hope you got something meaningful out of the program.\n\nIf it’s a money thing (which is what we hear most), I’m sending you a one-time offer — it gives you a full year of access for the price of two months. Basically 33% off, and just something we do to make sure cost isn’t the reason someone misses out.\n\nHere’s the link if you want it:\nclear30://offer?placement=33off`,
    // `Ok ok ok one last thing\n\nI get you don't wanna do Clear30 - but I don't want to just throw you out to the wolves\n\nHere's a link to a free tracker you can print out and put on your wall.\n\n🖼️ Poster: LINK`
  ]
}

function generateInterviewTrialCancellationMessages(userName: string, assessmentData?: AssessmentResponse): string[] {
  const name = userName || 'there'

  return [
    `Hey ${name}, this is Ju - I just got notified you cancelled your trial. I get it if you don't think it'll be right for you... we would love to hear thoughts tho!\n\nIf you're interested, we'll pay you $25 to hop on a quick call\n\n Here's the link: https://calendly.com/asher-clear30/30min-trial`
  ]
}

function generateFreeSurveyTrialCancellationMessages(userName: string, assessmentData?: AssessmentResponse, userId?: string): string[] {
  const name = userName || 'there'
  const id = userId || ''
  return [
    `Hey ${name}, this is Ju - I just got notified you cancelled your trial. I get it if you don't think it'll be right for you... we would love to hear thoughts tho!\n\nIf you're interested, we'll give you a **Free Clear30 Subscription** to fill out a quick survey.\n\nHere's the link: https://clear30.org/redir/?des=survey&user_id=${id}&type=free_sub_form_trial_sms`
  ]
}

/**
 * Schedules downsell messages for a user who cancelled their trial
 */
async function scheduleDownsellMessages(appUserId: string) {
  console.log(`Trial cancellation detected - scheduling downsell messages for user ${appUserId}`)

  try {
    const downsellResponse = await fetch(`${Deno.env.get('SUPABASE_URL')}/functions/v1/sms_downsell_schedule`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ user_id: appUserId })
    })

    const downsellResult = await downsellResponse.json()

    if (!downsellResponse.ok) {
      console.error('Failed to schedule downsell messages:', downsellResult)
      return { success: false, error: downsellResult }
    } else {
      console.log('Successfully scheduled downsell messages for trial cancellation:', downsellResult)
      return { success: true, result: downsellResult }
    }
  } catch (error) {
    console.error('Error calling downsell schedule function:', error)
    return { success: false, error: error.message }
  }
}

/**
 * Updates the subscription cache table for unsubscriptions/cancellations
 */
async function updateSubscriptionCache(appUserId: string, webhook: RevenueCatWebhookEvent) {
  const event = webhook.event
  
  console.log(`Updating subscription cache for user ${appUserId} due to unsub: ${event.type}`)

  try {
    // Check if current event is newer than what's in cache
    const { data: existing } = await supabase
      .schema('payment')
      .from('subscription_cache')
      .select('last_event_ms')
      .eq('user_id', appUserId)
      .single()

    if (existing && Number(existing.last_event_ms) >= event.event_timestamp_ms) {
      console.log(`Skipping cache update: existing event (${existing.last_event_ms}) is newer than current (${event.event_timestamp_ms})`)
      return
    }

    // Map RevenueCat event types to our cache status
    let status = 'active'
    if (event.type === 'CANCELLATION') {
      status = 'cancelled'
    } else if (event.type === 'EXPIRATION') {
      status = 'expired'
    } else if (event.type === 'BILLING_ISSUE') {
      // Keep existing status or handle as needed
      return
    }

    const { error: upsertError } = await supabase
      .schema('payment')
      .from('subscription_cache')
      .upsert({
        user_id: appUserId,
        status: status,
        purchased_at: new Date(event.purchased_at_ms).toISOString(),
        expires_at: event.expiration_at_ms ? new Date(event.expiration_at_ms).toISOString() : null,
        last_event_ms: event.event_timestamp_ms
        // Note: we don't reset trial_2h_sent here, as if it's already sent, it stays sent.
        // If it's not sent, the cron will skip it because the status is no longer 'trialing'.
      })

    if (upsertError) {
      console.error('Failed to update subscription cache during unsub:', upsertError)
    } else {
      console.log('Successfully updated subscription cache during unsub')
    }
  } catch (err) {
    console.error('Failed to update subscription cache during unsub:', err)
  }
}

// SUBSCRIPTION CANCELLATION MESSAGES

function generateSubscriptionCancellationMessages(userName: string, assessmentData?: AssessmentResponse): string[] {
  const name = userName || 'there'

  // Check if user completed the program (assessment id contains "life")
  const completedProgram = assessmentData?.id && assessmentData.id.toString().includes('life')

  if (completedProgram) {
    return [
      `Hey I got an alert that you cancelled Clear30.\n\nI just wanted to say I'm really glad you made it through the program and wish you nothing but the best moving forward\n\nI can totally understand if it feels like there's no point to the app once you've completed the first 30 days.\n\nWe're actually building some After30 tools right now -  stuff to help with habits beyond weed. Hopefully it'll give you a good reason to come back soon :)`,
      // `Also btw - if you wanna build other habits past weed - here's a cool tracker you can put on your wall\n\n🖼️ Poster: LINK`
    ]
  }

  // Check "Then-What" response
  const thenWhat = assessmentData?.responses?.['Then-What']
  const wantsToQuitEntirely = thenWhat === 'I want to stop cannabis/weed use entirely'

  if (wantsToQuitEntirely) {
    return [
      `Hey, ${name}, this is Julian from peer support!\nI just noticed you canceled Clear30.\n\nTotally respect that. Just wanted to say I’m really glad you made it this far, and I hope the program gave you something meaningful along the way.\n\nIf cost had anything to do with it, I’d love to help. I can unlock a special option that gives you a full year for the price of two months — just want to make sure money isn’t the thing that keeps you from building on the progress you’ve already made.\n\nHere’s the link if you want it:\nclear30://one_time_offer`,
      // `Ahhh I got an alert that you cancelled Clear30.\n\nI really hope we helped you reach your goals, if not - feel free to text me back why Clear30 sucks, and I'll pass it on to the CEO.\n\nAnd if quitting's still on your mind down the line, let me know - I can hook you up with a one time offer to make it easier to jump back in.`,
      // `btw - if you simply just don't wanna pay for the program (I get it)\n\nhere's a free poster you can print out and follow along with\n\n🖼️ Poster: LINK`
    ]
  }

  // Any other "Then-What" response
  return [
    `Hey, ${name}, this is Julian from peer support!\nI just noticed you canceled Clear30.\n\nTotally respect that. Just wanted to say I’m really glad you made it this far, and I hope the program gave you something meaningful along the way.\n\nIf cost had anything to do with it, I’d love to help. I can unlock a special option that gives you a full year for the price of two months — just want to make sure money isn’t the thing that keeps you from building on the progress you’ve already made.\n\nHere’s the link if you want it:\nclear30://one_time_offer`,
    // `Ahhh I got an alert that you cancelled Clear30.\n\nI really hope we helped you reach your goals, if not - feel free to text me back why Clear30 sucks, and I'll pass it on to the CEO.\n\nAlso - lmk if you want a one time offer code for if you ever wanna start a break again.`,
    // `btw - if you simply just don't wanna pay for the program (I get it)\n\nhere's a free poster you can print out and follow along with\n\n🖼️ Poster: LINK`
  ]
}

// BILLING ISSUE MESSAGES

function generateBillingIssueMessages(userName: string, assessmentData?: AssessmentResponse): string[] {
  const name = userName || 'there'

  return [
    `${name} I just noticed there was a billing issue with your Clear30 subscription.\n\nThe good news is that this happens literally all the time and the fix is super easy.\n\nYou can update your payment method here to get everything sorted:\n\n👉 https://apps.apple.com/account/subscriptions\n\nLet me know if you need help with anything - I'd be happy to walk you through it.`
  ]
}

serve(async (req) => {
  try {
    const webhook: RevenueCatWebhookEvent = await req.json()

    console.log('Received RevenueCat webhook:', {
      eventType: webhook.event.type,
      appUserId: webhook.event.app_user_id,
      periodType: webhook.event.period_type,
      environment: webhook.event.environment
    })

    // Only process specific event types
    const validEventTypes = ['CANCELLATION', 'BILLING_ISSUE']
    if (!validEventTypes.includes(webhook.event.type)) {
      return new Response(JSON.stringify({
        success: false,
        error: `Event type ${webhook.event.type} not handled`
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    const appUserId = webhook.event.app_user_id

    // Update the subscription cache (non-blocking)
    updateSubscriptionCache(appUserId, webhook)

    // Get user data from our database using the RevenueCat app_user_id
    // Assuming app_user_id maps to our user id field
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('id, name, phone_number')
      .eq('id', appUserId)
      .single()

    if (userError || !userData) {
      console.error('User not found:', userError)
      return new Response(JSON.stringify({
        success: false,
        error: 'User not found'
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 404
      })
    }

    if (!userData.phone_number) {
      console.log('User has no phone number, skipping SMS')
      return new Response(JSON.stringify({
        success: true,
        message: 'User has no phone number, SMS skipped'
      }), {
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // Get most recent assessment data
    const { data: assessmentData, error: assessmentError } = await supabase
      .schema('programs')
      .from('program_assessment_responses')
      .select('*')
      .eq('user_id', appUserId)
      .order('timestamp', { ascending: false })
      .limit(1)
      .single()

    if (assessmentError) {
      console.log('No assessment data found:', assessmentError)
    }

    // Determine SMS message(s) based on event type
    let smsMessages: string[] = []
    let downsellResult: any = null
    const userName = userData.name || 'there'

    switch (webhook.event.type) {
      case 'CANCELLATION':
        if (webhook.event.period_type === 'TRIAL') {
          // Use new function to determine which message to send
          smsMessages = generateTrialCancellationMessageWithRandomness(userName, assessmentData, appUserId)

          // For trial cancellations, also schedule downsell messages
          const downsellScheduleResult = await scheduleDownsellMessages(appUserId)
          if (downsellScheduleResult.success) {
            downsellResult = downsellScheduleResult.result
          }
          // Continue with trial cancellation SMS even if downsell scheduling fails
        } else {
          // smsMessages = generateSubscriptionCancellationMessages(userName, assessmentData)
          // Dont send SMS for subscription cancellations
        }
        break
      case 'BILLING_ISSUE':
        // smsMessages = generateBillingIssueMessages(userName, assessmentData)
        // Dont send SMS for billing issues (stripe vs apple is tough)
        break
      default:
        // Don't send SMS for other event types
        return new Response(JSON.stringify({
          success: true,
          message: `Event type ${webhook.event.type} processed but no SMS sent`,
          eventType: webhook.event.type,
          userId: appUserId
        }), {
          headers: { 'Content-Type': 'application/json' }
        })
    }

    // If no SMS messages, return
    if (smsMessages.length === 0) {
      return new Response(JSON.stringify({
        success: true,
        message: 'No SMS messages to send'
      }), {
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // Determine the type prefix based on event type
    let typePrefix = ''
    switch (webhook.event.type) {
      case 'CANCELLATION':
        typePrefix = webhook.event.period_type === 'TRIAL' ? 'trial_cancellation' : 'subscription_cancellation'
        break
      case 'BILLING_ISSUE':
        typePrefix = 'billing_issue'
        break
      default:
        typePrefix = 'rc_event'
    }

    // Add SMS messages to the queue with 1-minute spacing
    const baseTime = new Date()
    const smsInserts = smsMessages.map((message, index) => {
      const scheduledTime = new Date(baseTime.getTime() + (index * 60 * 1000)) // 1 minute = 60 * 1000 ms
      return {
        user_id: appUserId,
        phone_number: userData.phone_number,
        text: message,
        outbound: true,
        scheduled_for: scheduledTime.toISOString(),
        created_at: new Date().toISOString(),
        canceled: false,
        type: `${typePrefix}_${index + 1}`
      }
    })

    const { error: smsError } = await supabase
      .schema('comms')
      .from('sms_messages')
      .insert(smsInserts)

    if (smsError) {
      console.error('Failed to insert SMS messages:', smsError)
      return new Response(JSON.stringify({
        success: false,
        error: 'Failed to schedule SMS messages'
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 500
      })
    }

    console.log('SMS messages scheduled successfully', {
      userId: appUserId,
      eventType: webhook.event.type,
      messageCount: smsMessages.length,
      phoneNumber: userData.phone_number?.replace(/\d(?=\d{4})/g, '*'), // Mask phone number in logs
      downsellScheduled: !!downsellResult
    })

    const responseData: any = {
      success: true,
      message: downsellResult
        ? `Webhook processed, ${smsMessages.length} SMS message(s) scheduled, and downsell campaign started`
        : `Webhook processed and ${smsMessages.length} SMS message(s) scheduled`,
      eventType: webhook.event.type,
      userId: appUserId,
      messageCount: smsMessages.length,
      hasAssessmentData: !!assessmentData
    }

    // Include downsell result if it was a trial cancellation
    if (downsellResult) {
      responseData.downsellScheduled = true
      responseData.downsellResult = downsellResult
    }

    return new Response(JSON.stringify(responseData), {
      headers: { 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('Error processing RevenueCat webhook:', error)
    return new Response(JSON.stringify({
      success: false,
      error: error.message
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500
    })
  }
})

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_unsub' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "api_version": "1.0",
      "event": {
        "id": "12345678-1234-1234-1234-123456789012",
        "type": "EXPIRATION",
        "event_timestamp_ms": 1703980800000,
        "app_user_id": "test-user-123",
        "aliases": [],
        "original_app_user_id": "test-user-123",
        "product_id": "clear30_trial",
        "period_type": "TRIAL",
        "purchased_at_ms": 1703980800000,
        "expiration_at_ms": 1703980800000,
        "environment": "SANDBOX",
        "app_id": "1234567890"
      }
    }'

*/
