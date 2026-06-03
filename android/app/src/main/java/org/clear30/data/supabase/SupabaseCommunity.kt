package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import org.clear30.data.model.Post

/**
 * Community feed fetch — ported from SupabaseCommunity.swift (getUserPosts /
 * feed query). Selects posts with embedded tags / reactions / comments.
 *
 * iOS scoped this to the `community` schema (`.schema(community_schema)`).
 * supabase-kt sets schema on the Postgrest plugin; if the community tables live
 * in a non-public schema, configure `defaultSchema`/a dedicated client — see TODO.
 */
private const val POSTS_TABLE = "posts"
private const val FEED_COLUMNS = "*, post_tags(tag:tags(id, name, type, color)), reactions(*), comments(*)"

suspend fun SupabaseController.getCommunityFeed(start: Int = 0, end: Int = 20): Result<List<Post>> = runCatching {
    client.postgrest.from(POSTS_TABLE)
        .select(Columns.raw(FEED_COLUMNS)) {
            filter { eq("is_hidden", false) }
            order("created_at", Order.DESCENDING)
            range(start.toLong(), end.toLong())
        }
        .decodeList<Post>()
}

/** RPC params for creating a post (Swift `CreatePost`, p_-prefixed). */
@Serializable
data class CreatePost(
    val p_title: String,
    val p_content_type: String,
    val p_body: String,
    val p_user_id: String,
    val p_tags: List<String> = emptyList(),
    val p_video_url: String? = null,
    val p_thumbnail_url: String? = null,
    val p_journal_entry_id: Int? = null,
)

/** createCommunityPost RPC -> new post id. */
suspend fun SupabaseController.createCommunityPost(post: CreatePost): SupabaseFunctionError? =
    callFunction(SupabaseFunction("create_post"), post)

/** Reaction / comment inserts (Swift addReactionForPost / addCommentToPost). */
@Serializable
data class ReactionInsert(val post_id: String, val user_id: String, val emoji: String)

@Serializable
data class CommentInsert(val post_id: String, val user_id: String, val body: String)

suspend fun SupabaseController.addReaction(postId: String, userId: String, emoji: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from("reactions").insert(ReactionInsert(postId, userId, emoji))
}.fold({ null }, { it.toError() })

suspend fun SupabaseController.addComment(postId: String, userId: String, body: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from("comments").insert(CommentInsert(postId, userId, body))
}.fold({ null }, { it.toError() })
