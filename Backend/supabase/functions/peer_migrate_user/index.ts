/**
 * peer_migrate_user
 *
 * Migrates a user from SMS-based peer support to in-app messaging.
 * Called from iOS app when user opens the updated version.
 *
 * Actions:
 * 1. Check if already migrated
 * 2. Import SMS conversation history into peer_messages
 * 3. Cancel pending SMS messages (all)
 * 4. Set migration flag on user
 */

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'npm:@supabase/supabase-js@2'
import { withAuth } from '../shared/middleware/auth.ts'
import { User } from '../shared/types/types.ts'
import { isValidTimezone, DEFAULT_TIMEZONE } from '../shared/utils/dateUtils.ts'

const supabaseURL = Deno.env.get('SUPABASE_URL') ?? ''
const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

interface SMSMessage {
  id: number
  user_id: string
  text: string
  outbound: boolean
  sent_at: string
  type?: string
}

const supabase = createClient(supabaseURL, supabaseServiceRoleKey)

const handler = async (req: Request, user?: User): Promise<Response> => {
  try {
    // Check request method
    if (req.method !== 'POST') {
      return new Response(
        JSON.stringify({ error: 'Method not allowed. Use POST.' }),
        { status: 405, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // User ID comes from JWT token via auth middleware
    if (!user?.id) {
      return new Response(
        JSON.stringify({ error: 'User not authenticated' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const user_id = user.id

    // Parse timezone from request body
    let timezone = DEFAULT_TIMEZONE
    try {
      const body = await req.json()
      if (body.timezone && isValidTimezone(body.timezone)) {
        timezone = body.timezone
      } else if (body.timezone) {
        console.warn(`Invalid timezone "${body.timezone}" for user ${user_id}, using default`)
      }
    } catch {
      // No body or invalid JSON - use default timezone
    }

    // 1. Check if already migrated
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('peer_support_migrated')
      .eq('id', user_id)
      .single()

    if (userError) {
      console.error('Error fetching user:', userError)
      return new Response(
        JSON.stringify({ error: 'User not found' }),
        { status: 404, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (userData?.peer_support_migrated) {
      // Already migrated, but still update timezone if provided
      if (timezone !== DEFAULT_TIMEZONE) {
        await supabase
          .from('users')
          .update({ timezone: timezone })
          .eq('id', user_id)
      }
      console.log(`[peer_migrate_user] no-op | userId=${user_id} reason=already_migrated`)
      return new Response(
        JSON.stringify({ already_migrated: true, timezone_updated: timezone !== DEFAULT_TIMEZONE }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // 2. Check if already imported (deduplication for retries)
    const { count: existingMigratedCount } = await supabase
      .schema('comms')
      .from('peer_messages')
      .select('id', { count: 'exact', head: true })
      .eq('user_id', user_id)
      .eq('type', 'migrated')

    let importedCount = 0

    if (existingMigratedCount && existingMigratedCount > 0) {
      console.log(`User ${user_id} already has ${existingMigratedCount} migrated messages, skipping import`)
      importedCount = existingMigratedCount
    } else {
      // 3. Import SMS conversation history
      const { data: smsHistory, error: smsError } = await supabase
        .schema('comms')
        .from('sms_messages')
        .select('*')
        .eq('user_id', user_id)
        .eq('canceled', false)
        .not('sent_at', 'is', null)
        .order('sent_at', { ascending: true })

      if (smsError) {
        console.error(`Error fetching SMS history for user ${user_id}:`, smsError)
      }

      if (smsHistory && smsHistory.length > 0) {
        const peerMessages = smsHistory.map((sms: SMSMessage) => ({
          user_id: sms.user_id,
          text: sms.text,
          outbound: sms.outbound,
          type: 'migrated',
          created_at: sms.sent_at
        }))

        const { error: insertError } = await supabase
          .schema('comms')
          .from('peer_messages')
          .insert(peerMessages)

        if (insertError) {
          console.error(`Error importing SMS history for user ${user_id}:`, insertError)
        } else {
          importedCount = peerMessages.length
          console.log(`Imported ${importedCount} messages for user ${user_id}`)
        }
      }
    }

    // 3. Cancel pending SMS messages (all)
    const { data: canceledMessages, error: cancelError } = await supabase
      .schema('comms')
      .from('sms_messages')
      .update({ canceled: true })
      .eq('user_id', user_id)
      .is('sent_at', null)
      .select('id')

    if (cancelError) {
      console.error(`Error canceling pending SMS for user ${user_id}:`, cancelError)
    } else {
      console.log(`Canceled ${canceledMessages?.length || 0} pending SMS for user ${user_id}`)
    }

    // 4. Set migration flag and timezone
    const { error: updateError } = await supabase
      .from('users')
      .update({
        peer_support_migrated: true,
        peer_support_migrated_at: new Date().toISOString(),
        timezone: timezone
      })
      .eq('id', user_id)

    if (updateError) {
      console.error(`Error updating user migration flag for user ${user_id}:`, updateError)
      return new Response(
        JSON.stringify({ error: 'Failed to update migration flag' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    console.log(`[peer_migrate_user] success | userId=${user_id} imported=${importedCount} canceledSms=${canceledMessages?.length || 0}`)

    return new Response(
      JSON.stringify({
        success: true,
        imported_messages: importedCount,
        canceled_sms: canceledMessages?.length || 0,
        timezone: timezone
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error(`[peer_migrate_user] error | userId=${user?.id}`, error)
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : String(error) }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
}

// Wrap handler with auth middleware
Deno.serve(withAuth(handler, supabase))

/* To invoke locally:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/peer_migrate_user' \
    --header 'Authorization: Bearer YOUR_USER_JWT_TOKEN' \
    --header 'Content-Type: application/json'

  Note: User ID is extracted from the JWT token automatically.
*/
