package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Community models — ported from Post.swift / Reaction.swift / Comment.swift
 * (feed subset). Snake_case keys match the `community.posts` payload (with the
 * embedded post_tags/reactions/comments selects). Nested types are kept lean for
 * the feed; the full post-detail/editing models are layered on later.
 */
@Serializable
data class Post(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("content_type") val contentType: String,
    val body: String,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("view_count") var viewCount: Int = 0,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_flagged_by_llm") val isFlaggedByLLM: Boolean = false,
    var reactions: List<Reaction>? = null,
    @SerialName("reaction_counts") var reactionCounts: List<ReactionCount>? = null,
    @SerialName("post_tags") val postTags: List<PostTag>? = null,
    var comments: List<Comment>? = null,
    @SerialName("comments_count") var commentsCount: Int? = null,
) {
    val totalCommentsCount: Int get() = commentsCount ?: comments?.size ?: 0
    val isVideo: Boolean get() = contentType == "video"
}

@Serializable
data class ReactionCount(val emoji: String, val count: Int)

@Serializable
data class Reaction(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val emoji: String,
)

@Serializable
data class PostTag(val tag: Tag? = null) {
    @Serializable
    data class Tag(val id: String, val name: String, val type: String? = null, val color: String? = null)
}

@Serializable
data class Comment(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
)
