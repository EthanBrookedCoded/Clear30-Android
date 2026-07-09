package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import org.clear30.data.model.DrFredMessage
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * Dr. Fred chat — ported from SupabaseDrFred.swift. The thread lives in the
 * `comms.dr_fred` table; the user's messages are `outbound = false` and Dr. Fred's
 * replies are `outbound = true`.
 *
 * Unlike the previous stub (which posted the whole chat history to a non-existent
 * `dr_fred_send_message({messages})` contract and expected an AI reply), this is a
 * persistent table thread like peer support: the user's send goes through the
 * `dr_fred_send_message(message text)` Postgres RPC — which inserts the row with
 * `user_id = auth.uid()` server-side — and Dr. Fred's replies are admin/coach rows
 * fetched back from the table. iOS receives replies over Realtime; Android polls
 * [getDrFredMessages] (same stand-in as [getPeerMessages]).
 */
private const val COMMS_SCHEMA = "comms"
private const val DR_FRED_TABLE = "dr_fred"

/** Most-recent-first page of messages (Swift `getDrFredMessagesAsync`). */
suspend fun SupabaseController.getDrFredMessages(startIndex: Int = 0, count: Int = 20): Result<List<DrFredMessage>> = runCatching {
    client.postgrest.from(COMMS_SCHEMA, DR_FRED_TABLE)
        .select {
            order("created_at", Order.DESCENDING)
            range(startIndex.toLong(), (startIndex + count - 1).toLong())
        }
        .decodeList<DrFredMessage>()
}

/**
 * Send a user message (Swift `sendDrFredMessage`). Fires the
 * `dr_fred_send_message(message)` RPC, which inserts the inbound row server-side
 * (so the client never sets user_id). Returns null on success.
 */
suspend fun SupabaseController.sendDrFredMessage(message: String): SupabaseFunctionError? =
    callFunction(SupabaseFunction.sendDrFredMessage, mapOf("message" to message))

/** Unread summary used by the support-tab badge (Swift `getUnreadDrFredSummary`). */
@Serializable
data class DrFredUnread(val count: Int, val latestText: String?, val latestId: Long?)

/**
 * Count of Dr. Fred (outbound) messages newer than [sinceId], plus the newest one's
 * text + id (for bumping the local seen-marker). Pass `sinceId = 0` to count all.
 */
suspend fun SupabaseController.getUnreadDrFredSummary(sinceId: Long): DrFredUnread? = runCatching {
    @Serializable data class Row(val id: Long, val text: String)
    val rows = client.postgrest.from(COMMS_SCHEMA, DR_FRED_TABLE)
        .select(Columns.list("id", "text")) {
            filter {
                eq("outbound", true)
                gt("id", sinceId)
            }
            order("id", Order.DESCENDING)
        }
        .decodeList<Row>()
    DrFredUnread(rows.size, rows.firstOrNull()?.text, rows.firstOrNull()?.id)
}.getOrNull()

/**
 * Current max message id (outbound or not). Seeds the local seen-marker on first
 * setup so history isn't surfaced as "unread" (Swift `getMaxDrFredMessageId`).
 */
suspend fun SupabaseController.getMaxDrFredMessageId(): Long = runCatching {
    @Serializable data class Row(val id: Long)
    client.postgrest.from(COMMS_SCHEMA, DR_FRED_TABLE)
        .select(Columns.list("id")) {
            order("id", Order.DESCENDING)
            range(0, 0)
        }
        .decodeList<Row>().firstOrNull()?.id ?: 0L
}.getOrElse { 0L }
