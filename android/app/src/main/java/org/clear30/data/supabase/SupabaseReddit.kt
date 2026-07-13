package org.clear30.data.supabase

import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

// The edge function's contract (iOS `fetchRedditThread`,
// SupabaseEdgeFunctions.swift:54-60): camelCase `postId`, validated server-side
// as `^[A-Za-z0-9_]+$` / `^[A-Za-z0-9]+$`.
@Serializable
private data class RedditProxyRequest(val subreddit: String, val postId: String)

// iOS RedditScraper.extractPostDetails: group 1 = subreddit, group 2 = postId
// (the base36 id before the title slug). Unanchored, so any reddit host works.
private val POST_DETAILS = Regex("""reddit\.com/r/([^/]+)/comments/([^/]+)""")

/**
 * Fetch a Reddit thread's JSON via the `reddit_proxy` edge function — a
 * server-side fetch (with a never-expiring `library.reddit_threads` cache) that
 * dodges Reddit's client/datacenter-IP 403 block, the same dependency iOS's
 * `RedditScraper` relies on. Returns the raw JSON array text, or null when the
 * URL isn't a canonical `/comments/` link or the call fails, so
 * [org.clear30.data.RedditScraper] can fall back to a direct fetch and then the
 * web view.
 */
suspend fun SupabaseController.fetchRedditJson(url: String): String? = runCatching {
    val match = POST_DETAILS.find(url) ?: return@runCatching null
    val (subreddit, postId) = match.destructured
    client.functions.invoke("reddit_proxy", RedditProxyRequest(subreddit, postId)).bodyAsText()
}.onFailure { android.util.Log.w("RedditProxy", "reddit_proxy failed: ${it.message}") }
    .getOrNull()
    ?.takeIf { it.trimStart().startsWith("[") } // reject the { "error": ... } shape
