package org.clear30.data.supabase

import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.clear30.data.Clear30Store
import org.clear30.data.model.ClaireMessage
import org.clear30.data.model.Program
import org.clear30.data.model.SupabaseClaireMessage
import org.clear30.data.model.UserInfo
import org.clear30.util.now

/**
 * Claire AI chat — the real backend contract (claire_handle_threads +
 * claire_openai_chat). Two edge functions:
 *
 *  - `claire_handle_threads` (action="create") creates an OpenAI assistant thread
 *    seeded with the user's context, returning a `thread_…` id we cache on UserInfo.
 *  - `claire_openai_chat` takes `{ message, threadId, conversationMode }` and, on the
 *    default (assistants) path, **streams** the reply as SSE (`data: {delta}` …
 *    `data: [DONE]`). We read the whole stream and accumulate the text, so the
 *    shared [org.clear30.views.components.AiChatScreen] still gets a single reply
 *    string. (Live token streaming into the bubble is a follow-up.)
 *
 * Replaces the earlier placeholder that POSTed `{messages}` to a nonexistent
 * `claire-chat` function — which is why Claire never responded.
 */

private val claireJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ClaireUserContext(
    val name: String,
    val programName: String,
    val currentDayContext: String,
    val currentDay: Int? = null,
    val assessmentResponses: String? = null,
    val checkIns: String? = null,
    val lastSmoked: String? = null,
    val currentDate: String? = null,
)

@Serializable
private data class ClaireThreadRequest(
    val action: String,
    val userContext: ClaireUserContext? = null,
    val threadId: String? = null,
)

@Serializable
private data class ClaireChatRequest(
    val message: String,
    val threadId: String,
    val conversationMode: String = "text",
)

/**
 * Load the user's Claire chat history (Swift `fetchClaireMessages`). Reads
 * `claire.messages` for `message_type = "chat"`, oldest-first, and maps each row to
 * an in-memory [ClaireMessage] (role → isUser). Rows carry extra columns the lenient
 * controller decoder ignores, so decoding into the role/content wire model is safe.
 * Returns an empty list on any failure (Claire then just shows the greeting).
 */
suspend fun SupabaseController.getClaireMessages(userID: String): List<ClaireMessage> = runCatching {
    client.postgrest.from("claire", "messages")
        .select(Columns.list("role", "content")) {
            filter {
                eq("user_id", userID)
                eq("message_type", "chat")
            }
            order("created_at", Order.ASCENDING)
        }
        .decodeList<SupabaseClaireMessage>()
        .map { it.toClaireMessage() }
}.getOrElse { emptyList() }

/**
 * The user's Claire thread id, creating + caching one on first use. Returns null
 * if creation fails (no session / network) — the caller shows a fallback reply.
 */
suspend fun SupabaseController.getOrCreateClaireThread(userInfo: UserInfo, program: Program): String? {
    userInfo.claireThreadID?.takeIf { it.startsWith("thread_") }?.let { return it }

    val context = ClaireUserContext(
        name = userInfo.name.ifBlank { "Friend" },
        programName = if (program.coreModeration) "Moderation" else "Weed Free",
        currentDayContext = "Day ${program.currentDay.coerceAtLeast(0)} of the program.",
        currentDay = program.currentDay.coerceAtLeast(0),
        lastSmoked = program.lastSmoked.toString(),
        currentDate = now().toString(),
    )
    val body = runCatching {
        client.functions.invoke("claire_handle_threads", ClaireThreadRequest(action = "create", userContext = context))
            .bodyAsText()
    }.onFailure { android.util.Log.w("SupabaseClaire", "thread create failed: ${it.message}") }
        .getOrNull() ?: return null

    // Accept the documented `data.threadId` shape plus a couple of plausible
    // fallbacks so a minor server-shape change doesn't break thread creation.
    val threadId = runCatching {
        val root = claireJson.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject
        (data?.get("threadId") ?: data?.get("thread_id") ?: root["threadId"] ?: root["thread_id"])
            ?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.startsWith("thread_") }
    if (threadId == null) android.util.Log.w("SupabaseClaire", "no threadId in response: ${body.take(200)}")

    if (threadId != null) {
        userInfo.claireThreadID = threadId
        Clear30Store.save(userInfo)
    }
    return threadId
}

/** Send a message to Claire on [threadId]; returns her full reply text. */
suspend fun SupabaseController.sendClaireMessage(message: String, threadId: String): String? {
    val body = runCatching {
        client.functions.invoke("claire_openai_chat", ClaireChatRequest(message = message.take(512), threadId = threadId))
            .bodyAsText()
    }.onFailure { android.util.Log.w("SupabaseClaire", "chat failed: ${it.message}") }
        .getOrNull() ?: return null
    val reply = parseClaireReply(body)
    if (reply == null) android.util.Log.w("SupabaseClaire", "no reply parsed from: ${body.take(200)}")
    return reply
}

/**
 * Extract Claire's reply from either the completion-API JSON (`{ data: { content } }`)
 * or the assistants-API SSE stream
 * (`data: { object:"thread.message.delta", delta:{ content:[{ text:{ value } }] } }`).
 */
private fun parseClaireReply(body: String): String? {
    val trimmed = body.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val content = claireJson.parseToJsonElement(trimmed)
                .jsonObject["data"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            if (!content.isNullOrBlank()) return content
        }
    }
    val out = StringBuilder()
    body.lineSequence().forEach { line ->
        val t = line.trim()
        if (!t.startsWith("data:")) return@forEach
        val data = t.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return@forEach
        runCatching {
            val obj = claireJson.parseToJsonElement(data).jsonObject
            if (obj["object"]?.jsonPrimitive?.content == "thread.message.delta") {
                obj["delta"]?.jsonObject?.get("content")?.jsonArray?.forEach { item ->
                    item.jsonObject["text"]?.jsonObject?.get("value")?.jsonPrimitive?.content?.let { out.append(it) }
                }
            }
        }
    }
    return out.toString().ifBlank { null }
}
