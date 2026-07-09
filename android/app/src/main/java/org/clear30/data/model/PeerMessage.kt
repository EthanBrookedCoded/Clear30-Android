package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PeerMessage — ported from PeerMessage.swift. A single message in the Gerad
 * ("accountability buddy") peer-support thread, backed by the `comms.peer_messages`
 * table. Snake_case keys match the row payload.
 *
 * `outbound == true` means the message is *from Gerad* (the supporter); the local
 * user's own messages are stored with `outbound == false`, so [isUser] negates it
 * (matching iOS).
 */
@Serializable
data class PeerMessage(
    val id: Long,
    @SerialName("user_id") val userId: String,
    val text: String,
    val outbound: Boolean,
    val type: String? = null,
    @SerialName("template_id") val templateId: Long? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("notification_sent") val notificationSent: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    val deleted: Boolean = false,
) {
    /** The local user's messages are inbound (`outbound == false`). */
    val isUser: Boolean get() = !outbound

    /** Optimistic (not-yet-persisted) messages get a negative client-side id. */
    val isOptimistic: Boolean get() = id < 0

    /**
     * Authoritative instant for ordering/grouping. Uses `scheduled_for` when the
     * server has populated it, otherwise `created_at` (Swift `orderDate`).
     * Timestamps are ISO-8601 strings; unparseable values sort earliest.
     */
    val orderDate: Instant
        get() = parsePeerTimestamp(scheduledFor) ?: parsePeerTimestamp(createdAt) ?: Instant.DISTANT_PAST
}

/** Insert payload for a user-sent peer message (Swift `PeerMessageInsert`). */
@Serializable
data class PeerMessageInsert(
    @SerialName("user_id") val userId: String,
    val text: String,
    val outbound: Boolean,
    val type: String,
)

/** Lenient ISO-8601 parse; returns null for blank/unparseable values. */
private fun parsePeerTimestamp(value: String?): Instant? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
