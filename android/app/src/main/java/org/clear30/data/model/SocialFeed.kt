package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.util.now

/** PostStatus — ported from SocialFeed.swift. Per-post seen/opened tracking. */
@Serializable
data class PostStatus(
    var postId: String = "",
    var userId: String = "",
    var seen: Boolean = false,
    var opened: Boolean = false,
)

/**
 * SocialFeed — ported from SocialFeed.swift (`@Model`). The single persisted
 * feed-status container, stored via [org.clear30.data.LocalStore].
 */
@Serializable
class SocialFeed(
    var feed: MutableList<PostStatus> = mutableListOf(),
    var updatedAt: Instant = now(),
) {
    companion object {
        const val STORE_KEY = "social_feed"
    }
}
