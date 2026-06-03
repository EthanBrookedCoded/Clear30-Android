package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.util.now
import java.util.UUID

/**
 * ClaireMessage — ported from ClaireMessage.swift. In-memory chat message for
 * the Claire AI coach.
 */
data class ClaireMessage(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    val isUser: Boolean,
    val timeStamp: Instant = now(),
)

/** Wire format for the chat edge function (role/content), matching the iOS payload. */
@Serializable
data class SupabaseClaireMessage(
    val role: String,
    val content: String,
) {
    fun toClaireMessage() = ClaireMessage(text = content, isUser = role == "user")
}
