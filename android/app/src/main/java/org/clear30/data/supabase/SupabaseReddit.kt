package org.clear30.data.supabase

import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
private data class RedditProxyRequest(val url: String)

/**
 * Fetch a Reddit thread's JSON via the `reddit_proxy` edge function — a
 * server-side fetch that dodges Reddit's client/datacenter-IP 403 block (the same
 * dependency iOS's `RedditScraper` relies on). Returns the raw JSON array text, or
 * null on any failure so [org.clear30.data.RedditScraper] can fall back to a
 * direct fetch and then the web view.
 */
suspend fun SupabaseController.fetchRedditJson(url: String): String? = runCatching {
    client.functions.invoke("reddit_proxy", RedditProxyRequest(url)).bodyAsText()
}.onFailure { android.util.Log.w("RedditProxy", "reddit_proxy failed: ${it.message}") }
    .getOrNull()
    ?.takeIf { it.trimStart().startsWith("[") } // reject the { "error": ... } shape
