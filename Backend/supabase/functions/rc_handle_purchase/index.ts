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

const ADJUST_APP_TOKEN = Deno.env.get('ADJUST_APP_TOKEN') || 'PLACEHOLDER_APP_TOKEN'
const ADJUST_EVENT_TOKEN_TRIAL_CONVERSION = Deno.env.get('ADJUST_EVENT_TOKEN_TRIAL_CONVERSION') || 'PLACEHOLDER_TRIAL_CONVERSION_TOKEN'

// Trial reminder notification content
function getTrialReminder(trialDurationDays: number) {
  return {
    title: `${trialDurationDays - 1} days in - trial ends tomorrow`,
    body: "See your progress and what's next for your Clear30"
  }
}

interface RevenueCatPurchaseWebhookEvent {
  api_version: string
  event: {
    id: string
    type: 'INITIAL_PURCHASE' | 'NON_RENEWING_PURCHASE' | 'RENEWAL' | string
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
    is_trial_conversion?: boolean
  }
}

/**
 * Schedules a trial reminder notification for the user
 */
async function scheduleTrialReminderNotification(appUserId: string, webhook: RevenueCatPurchaseWebhookEvent) {
  console.log(`Scheduling trial reminder notification for user ${appUserId}`)

  // Calculate trial duration and reminder time
  const trialStartTime = webhook.event.purchased_at_ms
  const trialEndTime = webhook.event.expiration_at_ms
  const trialDurationMs = trialEndTime - trialStartTime
  const trialDurationDays = Math.floor(trialDurationMs / (1000 * 60 * 60 * 24))

  // Schedule notification for 1 day before trial ends
  const reminderTimeMs = trialEndTime - (24 * 60 * 60 * 1000) // 1 day before expiration
  const reminderTime = new Date(reminderTimeMs)

  // Get trial reminder content
  const trialReminder = getTrialReminder(trialDurationDays)

  // Only schedule if the reminder time is in the future
  if (reminderTime <= new Date()) {
    console.log('Trial reminder time is in the past, skipping notification')
    return {
      success: true,
      skipped: true,
      message: 'Trial reminder time is in the past, no notification scheduled',
      trialDurationDays,
      reminderTime: reminderTime.toISOString()
    }
  }

  // Prepare notification data with selected variant
  const notificationData = {
    user_id: appUserId,
    title: trialReminder.title,
    body: trialReminder.body,
    timestamp: reminderTime.toISOString(),
    metadata: {
      type: "trialReminder",
      trial_start_time: new Date(trialStartTime).toISOString(),
      trial_end_time: new Date(trialEndTime).toISOString(),
      trial_duration_days: trialDurationDays,
      product_id: webhook.event.product_id,
      transaction_id: webhook.event.transaction_id,
      environment: webhook.event.environment
    },
    silent: false
  }

  try {
    // Insert notification into comms.notifications table
    const { error: notificationError } = await supabase
      .schema('comms')
      .from('notifications')
      .insert(notificationData)

    if (notificationError) {
      console.error('Failed to insert trial reminder notification:', notificationError)
      return {
        success: false,
        error: 'Failed to schedule trial reminder notification'
      }
    }

    console.log('Trial reminder notification scheduled successfully', {
      userId: appUserId,
      trialDurationDays,
      reminderTime: reminderTime.toISOString(),
      productId: webhook.event.product_id
    })

    return {
      success: true,
      message: 'Trial reminder notification scheduled successfully',
      trialDurationDays,
      reminderTime: reminderTime.toISOString(),
      productId: webhook.event.product_id
    }
  } catch (error) {
    console.error('Error scheduling trial reminder notification:', error)
    return {
      success: false,
      error: error.message
    }
  }
}

/**
 * Unschedules any downsell messages for the given user
 */
async function unscheduleDownsellMessages(appUserId: string) {
  console.log(`Unscheduling downsell messages for user ${appUserId} due to purchase`)

  try {
    const downsellResponse = await fetch(`${Deno.env.get('SUPABASE_URL')}/functions/v1/sms_downsell_unschedule`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ user_id: appUserId })
    })

    const downsellResult = await downsellResponse.json()

    if (!downsellResponse.ok) {
      console.error('Failed to unschedule downsell messages:', downsellResult)
      return { success: false, error: 'Failed to unschedule downsell messages', details: downsellResult }
    } else {
      console.log('Successfully unscheduled downsell messages:', downsellResult)
      return { success: true, result: downsellResult }
    }
  } catch (error) {
    console.error('Error unscheduling downsell messages:', error)
    return { success: false, error: error.message }
  }
}

/**
 * Sends an event to Adjust S2S API
 */
async function sendAdjustEvent(adjustId: string, eventToken: string, purchasedAtMs: number) {
  console.log(`Sending Adjust S2S event (token: ${eventToken}) for adid: ${adjustId}`)

  try {
    const adjustParams = new URLSearchParams({
      s2s: '1',
      app_token: ADJUST_APP_TOKEN,
      event_token: eventToken,
      adid: adjustId,
      created_at_unix: Math.floor(purchasedAtMs / 1000).toString()
    })

    const response = await fetch(`https://s2s.adjust.com/event?${adjustParams.toString()}`, {
      method: 'POST'
    })

    if (!response.ok) {
      const errorText = await response.text()
      console.error(`Adjust API error: ${errorText}`)
      return { success: false, error: errorText }
    }

    console.log(`Adjust event sent successfully`)
    return { success: true }
  } catch (error) {
    console.error(`Error sending Adjust event:`, error)
    return { success: false, error: error.message }
  }
}

/**
 * Updates the subscription cache table
 */
async function updateSubscriptionCache(appUserId: string, webhook: RevenueCatPurchaseWebhookEvent) {
  const event = webhook.event
  const adjustId = event.subscriber_attributes?.["$adjustId"]?.value || event.subscriber_attributes?.adjustId?.value

  console.log(`Updating subscription cache for user ${appUserId}, event type: ${event.type}`)

  try {
    // We use a manual upsert with a where clause to handle out-of-order events
    // This is cleaner as a single query
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

    const { error: upsertError } = await supabase
      .schema('payment')
      .from('subscription_cache')
      .upsert({
        user_id: appUserId,
        status: event.period_type === 'TRIAL' ? 'trialing' : 'active',
        purchased_at: new Date(event.purchased_at_ms).toISOString(),
        expires_at: event.expiration_at_ms ? new Date(event.expiration_at_ms).toISOString() : null,
        adjust_id: adjustId || null,
        last_event_ms: event.event_timestamp_ms,
        // Reset trial_2h_sent if it's a new trial
        ...(event.period_type === 'TRIAL' ? { trial_2h_sent: false } : {})
      })

    if (upsertError) {
      console.error('Failed to upsert subscription cache:', upsertError)
    } else {
      console.log('Successfully updated subscription cache')
    }
  } catch (err) {
    console.error('Failed to update subscription cache:', err)
  }
}

serve(async (req) => {
  try {
    const webhook: RevenueCatPurchaseWebhookEvent = await req.json()

    console.log('Received RevenueCat purchase webhook:', {
      eventType: webhook.event.type,
      appUserId: webhook.event.app_user_id,
      productId: webhook.event.product_id,
      periodType: webhook.event.period_type,
      environment: webhook.event.environment,
      store: webhook.event.store,
      isTrialConversion: webhook.event.is_trial_conversion
    })

    // Only process INITIAL_PURCHASE, NON_RENEWING_PURCHASE, and RENEWAL events
    const allowedEvents = ['INITIAL_PURCHASE', 'NON_RENEWING_PURCHASE', 'RENEWAL']
    if (!allowedEvents.includes(webhook.event.type)) {
      return new Response(JSON.stringify({
        success: false,
        error: `Event type ${webhook.event.type} not handled`
      }), {
        headers: { 'Content-Type': 'application/json' },
        status: 400
      })
    }

    const appUserId = webhook.event.app_user_id

    // Update the subscription cache
    await updateSubscriptionCache(appUserId, webhook)

    // Handle Trial Conversion event for Adjust
    if (webhook.event.type === 'RENEWAL' && webhook.event.is_trial_conversion) {
      const adjustId = webhook.event.subscriber_attributes?.["$adjustId"]?.value || webhook.event.subscriber_attributes?.adjustId?.value
      if (adjustId) {
        console.log(`Trial conversion detected for user ${appUserId} - sending Adjust event`)
        await sendAdjustEvent(adjustId, ADJUST_EVENT_TOKEN_TRIAL_CONVERSION, webhook.event.purchased_at_ms)
      } else {
        console.warn(`Trial conversion detected but no Adjust ID found for user ${appUserId}`)
      }
    }

    // Unschedule any downsell messages for this user (non-blocking)
    const downsellUnscheduleResult = await unscheduleDownsellMessages(appUserId)
    let downsellResult = null

    if (downsellUnscheduleResult.success) {
      downsellResult = downsellUnscheduleResult.result
    } else {
      console.warn('Downsell unscheduling failed, but continuing with purchase processing:', downsellUnscheduleResult.error)
      // Continue with purchase processing even if downsell unscheduling fails
    }

    // Check if this is a trial purchase - if so, also schedule trial reminder
    const isTrialPurchase = webhook.event.type === 'INITIAL_PURCHASE' && webhook.event.period_type === 'TRIAL'

    if (isTrialPurchase) {
      console.log('INITIAL_PURCHASE with TRIAL detected - scheduling trial reminder notification')

      const notificationResult = await scheduleTrialReminderNotification(appUserId, webhook)

      if (!notificationResult.success) {
        return new Response(JSON.stringify({
          success: false,
          error: notificationResult.error,
          downsellResult: downsellResult
        }), {
          headers: { 'Content-Type': 'application/json' },
          status: 500
        })
      }

      // Handle case where notification was skipped (reminder time in the past)
      if (notificationResult.skipped) {
        const downsellMessage = downsellResult ? 'downsell messages unscheduled' : 'downsell unscheduling failed'
        return new Response(JSON.stringify({
          success: true,
          message: `Trial purchase processed, ${downsellMessage}, trial reminder skipped (time in past)`,
          userId: appUserId,
          eventType: webhook.event.type,
          productId: webhook.event.product_id,
          trialDurationDays: notificationResult.trialDurationDays,
          reminderTime: notificationResult.reminderTime,
          downsellResult: downsellResult,
          downsellSuccess: !!downsellResult
        }), {
          headers: { 'Content-Type': 'application/json' }
        })
      }

      // Success case for trial
      const downsellMessage = downsellResult ? 'downsell messages unscheduled' : 'downsell unscheduling failed'
      return new Response(JSON.stringify({
        success: true,
        message: `Trial purchase processed, ${downsellMessage}, and trial reminder scheduled`,
        userId: appUserId,
        eventType: webhook.event.type,
        productId: webhook.event.product_id,
        trialDurationDays: notificationResult.trialDurationDays,
        reminderTime: notificationResult.reminderTime,
        downsellResult: downsellResult,
        downsellSuccess: !!downsellResult
      }), {
        headers: { 'Content-Type': 'application/json' }
      })
    }

    // Non-trial purchase (regular subscription or non-renewing purchase)
    const downsellMessage = downsellResult ? 'downsell messages unscheduled' : 'downsell unscheduling failed'
    return new Response(JSON.stringify({
      success: true,
      message: `Purchase processed and ${downsellMessage}`,
      userId: appUserId,
      eventType: webhook.event.type,
      productId: webhook.event.product_id,
      downsellResult: downsellResult,
      downsellSuccess: !!downsellResult
    }), {
      headers: { 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('Error processing RevenueCat purchase webhook:', error)
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

  Test with INITIAL_PURCHASE (trial - will unschedule downsell + schedule trial reminder):
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_purchase' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "event": {
        "event_timestamp_ms": 1758135196847,
        "product_id": "com.subscription.yearly",
        "period_type": "TRIAL",
        "purchased_at_ms": 1758135196847,
        "expiration_at_ms": 1758394396851,
        "environment": "PRODUCTION",
        "entitlement_id": null,
        "entitlement_ids": ["pro"],
        "presented_offering_id": "standard",
        "transaction_id": "123456789012345",
        "original_transaction_id": "123456789012345",
        "is_family_share": false,
        "country_code": "US",
        "app_user_id": "1",
        "aliases": [],
        "original_app_user_id": "1",
        "currency": "USD",
        "price": 0,
        "price_in_purchased_currency": 0,
        "store": "APP_STORE",
        "takehome_percentage": 0.85,
        "tax_percentage": 0.0,
        "commission_percentage": 0.15,
        "type": "INITIAL_PURCHASE",
        "id": "12345678-1234-1234-1234-123456789012",
        "app_id": "1234567890"
      },
      "api_version": "1.0"
    }'

  Test with INITIAL_PURCHASE (regular subscription - will only unschedule downsell):
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_purchase' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "event": {
        "event_timestamp_ms": 1758135196847,
        "product_id": "com.subscription.yearly",
        "period_type": "NORMAL",
        "purchased_at_ms": 1758135196847,
        "expiration_at_ms": 1758394396851,
        "environment": "PRODUCTION",
        "entitlement_id": null,
        "entitlement_ids": ["pro"],
        "presented_offering_id": "standard",
        "transaction_id": "123456789012345",
        "original_transaction_id": "123456789012345",
        "is_family_share": false,
        "country_code": "US",
        "app_user_id": "1",
        "aliases": [],
        "original_app_user_id": "1",
        "currency": "USD",
        "price": 49.99,
        "price_in_purchased_currency": 49.99,
        "store": "APP_STORE",
        "takehome_percentage": 0.85,
        "tax_percentage": 0.0,
        "commission_percentage": 0.15,
        "type": "INITIAL_PURCHASE",
        "id": "12345678-1234-1234-1234-123456789012",
        "app_id": "1234567890"
      },
      "api_version": "1.0"
    }'

  Test with NON_RENEWING_PURCHASE (one-time purchase):
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/rc_handle_purchase' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{
      "event": {
        "event_timestamp_ms": 1758135196847,
        "product_id": "com.lifetime.purchase",
        "period_type": "NORMAL",
        "purchased_at_ms": 1758135196847,
        "expiration_at_ms": null,
        "environment": "PRODUCTION",
        "entitlement_id": null,
        "entitlement_ids": ["pro"],
        "transaction_id": "987654321012345",
        "original_transaction_id": "987654321012345",
        "is_family_share": false,
        "country_code": "US",
        "app_user_id": "1",
        "aliases": [],
        "original_app_user_id": "1",
        "currency": "USD",
        "price": 199.99,
        "price_in_purchased_currency": 199.99,
        "store": "APP_STORE",
        "takehome_percentage": 0.85,
        "tax_percentage": 0.0,
        "commission_percentage": 0.15,
        "type": "NON_RENEWING_PURCHASE",
        "id": "87654321-4321-4321-4321-210987654321",
        "app_id": "1234567890"
      },
      "api_version": "1.0"
    }'

*/
