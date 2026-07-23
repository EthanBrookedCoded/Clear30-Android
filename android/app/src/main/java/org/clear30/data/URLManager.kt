package org.clear30.data

import android.net.Uri

/**
 * URLManager — ported from URLManager.swift. Translates inbound deep-link URIs
 * (clear30:// scheme or https://clear30.org links from the manifest intent
 * filter) into structured [DeepLinkRoute]s that the app can act on.
 *
 * The [AppState.pendingUrl] flow is fed by MainActivity at intent time; AppRoot
 * watches it, calls [parse], and dispatches the result. Tab routes are
 * broadcast via [AppState.requestedTab] so AllTabs can switch tabs without
 * structural prop-drilling; richer routes (post detail, group join,
 * meditation playback) are emitted today and consumed as the matching screens
 * grow their deep-link entry points.
 */
sealed interface DeepLinkRoute {
    data object Today : DeepLinkRoute
    data object Profile : DeepLinkRoute
    data object Community : DeepLinkRoute
    data object Groups : DeepLinkRoute
    data object Support : DeepLinkRoute

    data class Post(val id: String) : DeepLinkRoute
    data class Group(val code: String) : DeepLinkRoute
    /** `?school=<id>` on ANY inbound URL — unlocks the app + school content (iOS URLManager.swift:52-72). */
    data class School(val schoolID: String) : DeepLinkRoute
    /** `?code=<code>` on ANY inbound URL — free-unlock promo link from the
     *  referral site (iOS ReferralCodeHandler.handleURL): validated against
     *  `payment.promo_codes` (`payment_check_code`); any existing code gifts
     *  the app (`userInfo.freeCode`). */
    data class Referral(val code: String) : DeepLinkRoute
    data class Meditation(val url: String) : DeepLinkRoute
    /** The all-messages library on the Support tab (feed-end "All Messages" CTA). */
    data object Messages : DeepLinkRoute
    /** Optional [prompt] pre-fills Claire's composer (Claire prompt cards). */
    data class Claire(val prompt: String = "") : DeepLinkRoute
    data object DrFred : DeepLinkRoute
    data object Settings : DeepLinkRoute
    /** The day-30 "breakdown 📦" push — opens the post-assessment when pending
     *  (iOS URLManager.swift:184-193). */
    data object Breakdown : DeepLinkRoute

    data class Unrecognized(val uri: String) : DeepLinkRoute
}

object URLManager {
    /**
     * Parse [uri]. Accepts both schemes the manifest declares:
     *   clear30://<host>[/<segment>]?<query>
     *   https://clear30.org/<host>[/<segment>]?<query>
     *
     * Examples:
     *   clear30://today              -> Today
     *   clear30://post/abc-123       -> Post("abc-123")
     *   clear30://group/CODE         -> Group("CODE")
     *   clear30://chat/claire        -> Claire
     *   clear30://meditation?url=... -> Meditation(url)
     */
    fun parse(uri: Uri): DeepLinkRoute {
        val raw = uri.toString()
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments.orEmpty()
        // The HTTPS form lacks a "host" in the sense we want — the first path
        // segment plays that role (clear30.org/post/abc -> host == "clear30.org",
        // segments == ["post","abc"]). Treat the first segment as the kind in
        // that case.
        val kind = if (host == "clear30.org") segments.firstOrNull()?.lowercase().orEmpty() else host
        val tail = if (host == "clear30.org") segments.drop(1) else segments

        // These payloads ride on ANY inbound URL as query params. Keep the
        // iOS URLManager precedence: school first, then group_id, then code.
        uri.getQueryParameter("school")?.takeIf { it.isNotBlank() }?.let {
            return DeepLinkRoute.School(it)
        }
        // The production join page opens `clear30://?group_id=<id>`, with no
        // route host. iOS reads group_id directly from URLComponents queryItems,
        // so Android must do the same instead of requiring `clear30://group`.
        uri.getQueryParameter("group_id")?.takeIf { it.isNotBlank() }?.let {
            return DeepLinkRoute.Group(it)
        }
        // Referral code (iOS ReferralCodeHandler.handleURL reads `?code=`).
        uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }?.let {
            return DeepLinkRoute.Referral(it)
        }

        return when (kind) {
            "today", "home" -> DeepLinkRoute.Today
            "profile" -> DeepLinkRoute.Profile
            "community" -> DeepLinkRoute.Community
            "groups" -> DeepLinkRoute.Groups
            "support", "library" -> DeepLinkRoute.Support
            "settings" -> DeepLinkRoute.Settings

            "post" -> tail.firstOrNull()?.let { DeepLinkRoute.Post(it) } ?: DeepLinkRoute.Community
            "group", "join-a-group" -> {
                val code = tail.firstOrNull() ?: uri.getQueryParameter("group_id")
                if (!code.isNullOrBlank()) DeepLinkRoute.Group(code) else DeepLinkRoute.Groups
            }
            "meditation" -> {
                val url = uri.getQueryParameter("url") ?: tail.joinToString("/").ifBlank { null }
                if (!url.isNullOrBlank()) DeepLinkRoute.Meditation(url) else DeepLinkRoute.Support
            }
            "chat" -> when (tail.firstOrNull()?.lowercase()) {
                "fred", "drfred", "dr_fred", "dr-fred" -> DeepLinkRoute.DrFred
                else -> DeepLinkRoute.Claire()
            }
            "breakdown" -> DeepLinkRoute.Breakdown

            else -> DeepLinkRoute.Unrecognized(raw)
        }
    }
}
