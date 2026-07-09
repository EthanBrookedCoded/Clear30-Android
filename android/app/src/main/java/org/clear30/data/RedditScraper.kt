package org.clear30.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.fetchRedditJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RedditScraper — ported from RedditScraper.swift. Fetches a Reddit thread's JSON
 * (`<permalink>.json`) and parses it into typed models for the in-app viewer, so
 * Reddit content renders natively instead of loading reddit.com in a WebView.
 *
 * Reddit's endpoint returns a 2-element array: `[0]` is the post Listing, `[1]`
 * is the comment Listing. `replies` on a comment is polymorphic — an empty string
 * `""` when there are none, or a nested Listing object — so we walk the JSON tree
 * manually (kotlinx-serialization can't model that union cleanly).
 */
object RedditScraper {

    // Reddit rate-limits/blocks requests without a descriptive User-Agent.
    private const val USER_AGENT = "android:org.clear30:v1 (by /u/clear30app)"
    private const val MAX_DEPTH = 6
    private const val MAX_TOP_LEVEL = 60

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient(OkHttp) }

    /** Fetch + parse [url]; null on any network/parse failure (caller falls back to the web view). */
    suspend fun fetch(url: String): RedditThread? = runCatching {
        // Prefer the server-side proxy (Reddit 403s direct app/datacenter fetches);
        // fall back to a direct fetch, then null → the caller shows the web view.
        val body = SupabaseController.fetchRedditJson(url) ?: directFetch(url) ?: return@runCatching null

        val root = json.parseToJsonElement(body).jsonArray
        val postData = root.getOrNull(0)?.jsonObject
            ?.get("data")?.jsonObject?.get("children")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("data")?.jsonObject
            ?: return@runCatching null
        val post = parsePost(postData)

        val commentChildren = root.getOrNull(1)?.jsonObject
            ?.get("data")?.jsonObject?.get("children")?.jsonArray
            ?: JsonArray(emptyList())
        val comments = parseComments(commentChildren, depth = 0).take(MAX_TOP_LEVEL)

        RedditThread(post = post, comments = comments)
    }.onFailure { android.util.Log.w("RedditScraper", "fetch failed for $url: ${it.message}") }
        .getOrNull()

    /** Direct fetch fallback — works on residential/mobile IPs Reddit doesn't block; null otherwise. */
    private suspend fun directFetch(url: String): String? = runCatching {
        http.get(jsonUrl(url)) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }.bodyAsText().takeIf { it.trimStart().startsWith("[") }
    }.getOrNull()

    /** `https://reddit.com/r/x/comments/id/slug/?foo` → `…/slug.json?raw_json=1&limit=100`. */
    private fun jsonUrl(url: String): String {
        val clean = url.substringBefore("?").trimEnd('/')
        val base = if (clean.endsWith(".json")) clean else "$clean.json"
        return "$base?raw_json=1&limit=100"
    }

    private fun parsePost(d: JsonObject) = RedditPost(
        title = d.str("title"),
        body = d.str("selftext"),
        author = d.str("author"),
        score = d.int("ups"),
        numComments = d.int("num_comments"),
        subreddit = d.str("subreddit"),
        createdUtc = d.dbl("created_utc"),
        permalink = d.str("permalink"),
    )

    private fun parseComments(children: JsonArray, depth: Int): List<RedditComment> {
        if (depth > MAX_DEPTH) return emptyList()
        return children.mapNotNull { child ->
            val obj = child.jsonObject
            // Skip "more" stubs (kind == "more") and anything without a real body.
            if (obj["kind"]?.jsonPrimitive?.content != "t1") return@mapNotNull null
            val d = obj["data"]?.jsonObject ?: return@mapNotNull null
            val author = d.str("author")
            val body = d.str("body")
            if (author.isBlank() || body.isBlank() || body == "[deleted]" || body == "[removed]") return@mapNotNull null

            val repliesEl = d["replies"]
            val replies = if (repliesEl is JsonObject) {
                val rc = repliesEl["data"]?.jsonObject?.get("children")?.jsonArray
                if (rc != null) parseComments(rc, depth + 1) else emptyList()
            } else {
                emptyList()
            }
            RedditComment(
                id = d.str("id"),
                author = author,
                body = body,
                score = d.int("ups"),
                createdUtc = d.dbl("created_utc"),
                depth = depth,
                replies = replies,
            )
        }
    }

    // JsonObject readers tolerant of missing/typed fields.
    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content ?: ""
    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.let { runCatching { it.int }.getOrNull() } ?: 0
    private fun JsonObject.dbl(key: String): Double = this[key]?.jsonPrimitive?.let { runCatching { it.double }.getOrNull() } ?: 0.0
}

/**
 * Builds the inline card preview — a single plain-text string from the post body,
 * markdown-stripped and capped (port of iOS `ParsedRedditContent.buildInlinePreview`
 * + `cleanRedditText`). Used by the Today-feed Reddit card so it previews the
 * thread instead of just showing an icon + title.
 */
fun redditInlinePreview(body: String, maxLength: Int = 1000): String {
    val cleaned = body
        .replace("&nbsp;", " ")
        .replace(" ", " ")
        .replace("​", "")
    val paragraphs = cleaned.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    val parts = mutableListOf<String>()
    var total = 0
    for (p in paragraphs) {
        val noSpace = p.replace(" ", "")
        // Skip horizontal-rule paragraphs (---, ___, ***).
        if (noSpace.length >= 3 && noSpace.all { it == '-' || it == '_' || it == '*' }) continue
        var plain = p
            .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1") // [text](url) → text
            .replace("**", "").replace("__", "").replace("*", "").replace("_", "")
        plain = plain.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (plain.isEmpty()) continue
        if (total + plain.length > maxLength) {
            val remaining = maxLength - total
            if (remaining > 0) parts.add(plain.take(remaining))
            break
        }
        parts.add(plain)
        total += plain.length
    }
    return parts.joinToString("\n\n")
}

/** A parsed Reddit thread — the post plus its top-level comment forest. */
data class RedditThread(
    val post: RedditPost,
    val comments: List<RedditComment>,
)

data class RedditPost(
    val title: String,
    val body: String,
    val author: String,
    val score: Int,
    val numComments: Int,
    val subreddit: String,
    val createdUtc: Double,
    val permalink: String,
)

data class RedditComment(
    val id: String,
    val author: String,
    val body: String,
    val score: Int,
    val createdUtc: Double,
    val depth: Int,
    val replies: List<RedditComment>,
)
