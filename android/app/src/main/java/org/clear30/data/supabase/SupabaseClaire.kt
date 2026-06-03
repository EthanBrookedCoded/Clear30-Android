package org.clear30.data.supabase

import kotlinx.serialization.Serializable
import org.clear30.data.model.SupabaseClaireMessage

/**
 * Claire AI chat — ported from the AI/chat edge-function calls. Sends the
 * conversation to the chat edge function and returns the assistant reply.
 *
 * The exact edge-function name/contract should be reconciled with the deployed
 * function (Backend/supabase/functions); `claire-chat` + {messages}->{content}
 * is the assumed shape.
 */
@Serializable
private data class ClaireRequest(val messages: List<SupabaseClaireMessage>)

@Serializable
private data class ClaireResponse(val content: String)

suspend fun SupabaseController.sendClaireMessage(history: List<SupabaseClaireMessage>): String? {
    val (response, _) = callEdgeFunction(
        SupabaseEdgeFunction("claire-chat"),
        ClaireRequest(history),
        ClaireResponse::class.java,
    )
    return response?.content
}
