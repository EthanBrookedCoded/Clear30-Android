package org.clear30.data.supabase

import kotlinx.serialization.Serializable
import org.clear30.data.model.SupabaseClaireMessage

/**
 * Dr Fred chat — ported from SupabaseDrFred.swift. Sends the conversation to the
 * `dr_fred_send_message` RPC and returns the reply. Contract assumed
 * ({messages}->{content}); reconcile with the deployed function.
 */
@Serializable
private data class DrFredRequest(val messages: List<SupabaseClaireMessage>)

@Serializable
private data class DrFredResponse(val content: String)

suspend fun SupabaseController.sendDrFredMessage(history: List<SupabaseClaireMessage>): String? {
    val (response, _) = callFunction(
        SupabaseFunction.sendDrFredMessage,
        DrFredRequest(history),
        DrFredResponse::class.java,
    )
    return response?.content
}
