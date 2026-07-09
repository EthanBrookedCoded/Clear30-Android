package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DrFredMessage — ported from DrFredMessage.swift. A single message in the
 * `comms.dr_fred` table (Dr. Fred = the addiction specialist coach).
 *
 * `outbound == true` means the message is *from Dr. Fred*; the user's own messages
 * are stored with `outbound == false`, so [isUser] negates it (matching iOS and
 * [PeerMessage]). Dr. Fred is a persistent two-way table thread (NOT a request/
 * response AI chat) — the user's send is inserted server-side via the
 * `dr_fred_send_message` RPC and Dr. Fred's replies arrive as new rows.
 */
@Serializable
data class DrFredMessage(
    val id: Long,
    @SerialName("user_id") val userId: String = "",
    val text: String,
    val outbound: Boolean,
    @SerialName("created_at") val createdAt: String,
) {
    /** The local user's messages are inbound (`outbound == false`). */
    val isUser: Boolean get() = !outbound

    /** Optimistic (not-yet-persisted) messages get a negative client-side id. */
    val isOptimistic: Boolean get() = id < 0

    /** Authoritative instant for ordering/grouping; unparseable values sort earliest. */
    val orderDate: Instant
        get() = createdAt.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.DISTANT_PAST
}
