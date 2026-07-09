package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.data.model.PeerMessage
import org.clear30.data.model.PeerMessageInsert
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * Peer Support (Gerad) — ported from SupabasePeerSupport.swift. The thread lives
 * in the `comms.peer_messages` table; the user sends inbound messages
 * (`outbound = false`) and Gerad's replies come back as `outbound = true`.
 *
 * iOS scoped every call with `.schema("comms")`; supabase-kt takes the schema as
 * the first arg of `from(schema, table)`. The `comms` schema must be exposed to
 * PostgREST/Realtime (same backend config as the `community` schema — see the
 * non-public-schema TODO in CLAUDE.md).
 *
 * Note: iOS receives Gerad's replies over a Supabase Realtime channel; the Android
 * chat polls [getPeerMessages] on an interval instead (realtime channels are not
 * yet wired anywhere in the app). Swapping to a realtime subscription later is a
 * drop-in replacement for the poll loop in PeerSupportChat.
 */
private const val COMMS_SCHEMA = "comms"
private const val PEER_TABLE = "peer_messages"

/**
 * Most-recent-first page of (non-deleted) peer messages (Swift
 * `getPeerMessagesAsync`). No client-side `scheduled_for <= now` filter — the RLS
 * SELECT policy already enforces that server-side, and the device clock lagging
 * the server would otherwise hide just-delivered messages.
 */
suspend fun SupabaseController.getPeerMessages(startIndex: Int = 0, count: Int = 20): Result<List<PeerMessage>> = runCatching {
    client.postgrest.from(COMMS_SCHEMA, PEER_TABLE)
        .select {
            filter { eq("deleted", false) }
            order("scheduled_for", Order.DESCENDING)
            range(startIndex.toLong(), (startIndex + count - 1).toLong())
        }
        .decodeList<PeerMessage>()
}

/** Send a user message (Swift `sendPeerMessage`); stored as inbound + `type = "manual"`. */
suspend fun SupabaseController.sendPeerMessage(message: String, userID: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMS_SCHEMA, PEER_TABLE)
        .insert(PeerMessageInsert(userId = userID, text = message, outbound = false, type = "manual"))
}.fold({ null }, { it.toError() })

/** Soft-delete one of the user's own messages (Swift `deletePeerMessage`). */
suspend fun SupabaseController.deletePeerMessage(messageID: Long): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMS_SCHEMA, PEER_TABLE)
        .update({ set("deleted", true) }) {
            filter { eq("id", messageID) }
        }
}.fold({ null }, { it.toError() })

@Serializable
private data class MarkPeerReadParams(@SerialName("p_user_id") val pUserId: String)

/**
 * Mark Gerad's messages as read (Swift `markPeerMessagesRead`). Best-effort badge
 * clear — failures are ignored, exactly as iOS does (`{ _ in }`). Requires the
 * `mark_peer_messages_read` function to be reachable via PostgREST.
 */
suspend fun SupabaseController.markPeerMessagesRead(userID: String) {
    callFunction(SupabaseFunction("mark_peer_messages_read"), MarkPeerReadParams(userID))
}

@Serializable
private data class PeerMigrateParams(val timezone: String)

/** Migrate the user into peer support (Swift `migrateToPeerSupport`). */
suspend fun SupabaseController.migrateToPeerSupport(timezone: String): SupabaseFunctionError? =
    callEdgeFunction(SupabaseEdgeFunction("peer_migrate_user"), PeerMigrateParams(timezone))
