package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.clear30.data.model.Activity
import org.clear30.data.model.Comment
import org.clear30.data.model.CommunityPrompt
import org.clear30.data.model.Post
import org.clear30.data.model.PostTag
import org.clear30.data.model.UserCommunity
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * Community feed fetch — ported from SupabaseCommunity.swift (getUserPosts /
 * feed query). Selects posts with embedded tags / reactions / comments.
 *
 * The tables live in the non-public `community` schema (e.g. `community.posts`),
 * so every call targets it via `from(schema, table)` — querying the default
 * `public` schema returns nothing, which is why the feed came up empty.
 * (Requires `community` to be in the project's exposed PostgREST schemas.)
 */
private const val COMMUNITY_SCHEMA = "community"

/** RPC response of community.get_filtered_posts ({ data, total_count }). */
@Serializable
private data class FilteredPostsResponse(val data: List<Post> = emptyList(), val total_count: Int = 0)

/**
 * Community feed via the `community.get_filtered_posts` RPC. The raw `posts`
 * table SELECT came back empty (a swallowed query/embed error); this RPC is
 * `SECURITY DEFINER` + granted to anon, returns posts with embedded
 * tags/reactions/comment-counts as `{ data, total_count }`, and is exactly what
 * the iOS feed pages through.
 */
suspend fun SupabaseController.getCommunityFeed(
    start: Int = 0,
    end: Int = 20,
    sortBy: String = "recent",   // "recent" (Newest) | "engagement" (Top) | "views"
    minDate: String? = null,     // timestamp-without-tz string; only for Top + a time range
    tagIds: List<String>? = null,
    onlyMyPosts: Boolean = false,
): Result<List<Post>> = runCatching {
    // `sort_by` is unique to the newer get_filtered_posts overload — passing it
    // disambiguates the two community.get_filtered_posts(...) overloads (otherwise
    // PostgREST errors with "Could not choose the best candidate function").
    val params = buildJsonObject {
        put("start_range", start)
        put("end_range", end)
        put("sort_by", sortBy)
        if (onlyMyPosts) put("only_my_posts", true)
        minDate?.let { put("min_date", it) }
        if (!tagIds.isNullOrEmpty()) put("tag_ids", JsonArray(tagIds.map { JsonPrimitive(it) }))
    }
    client.postgrest.rpc("get_filtered_posts", params) { schema = COMMUNITY_SCHEMA }
        .decodeAs<FilteredPostsResponse>()
        .data
}.onFailure { android.util.Log.w("SupabaseCommunity", "community feed failed: ${it.message}") }

/**
 * The Today carousel's FALLBACK source (iOS `getCommunityFeedByDayName`):
 * resolve the "Day N" + program tags, pull the filtered feed for them, and
 * prefer posts matching BOTH tags over program-only matches.
 */
suspend fun SupabaseController.getCommunityFeedByDayName(
    dayName: String,
    programName: String,
    excludePinned: Boolean = true,
    minComments: Int = 1,
    minDate: String,
    sortBy: String = "views",
): Result<List<Post>> = runCatching {
    val tags = client.postgrest.from(COMMUNITY_SCHEMA, "tags")
        .select {
            filter {
                or {
                    eq("name", dayName)
                    eq("name", programName)
                }
            }
        }
        .decodeList<PostTag.Tag>()
    if (tags.isEmpty()) return@runCatching emptyList()

    val params = buildJsonObject {
        put("start_range", 0)
        put("end_range", 10)
        put("tag_ids", JsonArray(tags.map { JsonPrimitive(it.id) }))
        put("only_my_posts", false)
        put("exclude_pinned", excludePinned)
        put("min_comments", minComments)
        put("min_date", minDate)
        put("sort_by", sortBy)
    }
    val posts = client.postgrest.rpc("get_filtered_posts", params) { schema = COMMUNITY_SCHEMA }
        .decodeAs<FilteredPostsResponse>()
        .data
    val programMatch = posts.filter { p -> p.postTags?.any { it.tag?.name == programName } == true }
    val programAndDay = programMatch.filter { p -> p.postTags?.any { it.tag?.name == dayName } == true }
    programAndDay.ifEmpty { programMatch }
}.onFailure { android.util.Log.w("SupabaseCommunity", "feed by day name failed: ${it.message}") }

/** All community tags (community.tags) for the Filter-by-Tag sheet, sorted by name. */
suspend fun SupabaseController.getCommunityTags(): Result<List<PostTag.Tag>> = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "tags")
        .select { order("name", Order.ASCENDING) }
        .decodeList<PostTag.Tag>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "tags failed: ${it.message}") }

/**
 * A post's comments (community.comments). The feed RPC only returns a
 * comments_count, so the detail screen fetches the bodies here.
 */
suspend fun SupabaseController.getPostComments(postId: String): Result<List<Comment>> = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "comments")
        .select {
            filter { eq("post_id", postId) }
            order("created_at", Order.ASCENDING)
        }
        .decodeList<Comment>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "comments failed: ${it.message}") }

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

/**
 * Create a post via `community.create_post_with_tags(p_title, p_content_type,
 * p_body, p_video_url, p_user_id, p_tags)`. The previous `create_post` name did
 * not exist (and was on the wrong schema), so posts never persisted.
 */
suspend fun SupabaseController.createCommunityPost(post: CreatePost): SupabaseFunctionError? = runCatching {
    val params = buildJsonObject {
        put("p_title", post.p_title)
        put("p_content_type", post.p_content_type)
        put("p_body", post.p_body)
        put("p_video_url", post.p_video_url)
        put("p_thumbnail_url", post.p_thumbnail_url)
        put("p_user_id", post.p_user_id)
        if (post.p_tags.isNotEmpty()) put("p_tags", JsonArray(post.p_tags.map { JsonPrimitive(it) }))
    }
    client.postgrest.rpc("create_post_with_tags", params) { schema = COMMUNITY_SCHEMA }
}.onFailure { android.util.Log.w("SupabaseCommunity", "create post failed: ${it.message}") }
    .fold({ null }, { it.toError() })

/** Reaction / comment inserts (Swift addReactionForPost / addCommentToPost). */
@Serializable
data class ReactionInsert(val post_id: String, val user_id: String, val emoji: String)

@Serializable
data class CommentInsert(val post_id: String, val user_id: String, val body: String)

suspend fun SupabaseController.addReaction(postId: String, userId: String, emoji: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "reactions").insert(ReactionInsert(postId, userId, emoji))
}.fold({ null }, { it.toError() })

suspend fun SupabaseController.addComment(postId: String, userId: String, body: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "comments").insert(CommentInsert(postId, userId, body))
}.fold({ null }, { it.toError() })

/**
 * Remove the user's own reaction (toggle-off). iOS only ever inserts reactions; this
 * is the Android inverse so a tapped emoji can be un-set. Deletes the matching row(s).
 */
suspend fun SupabaseController.removeReaction(postId: String, userId: String, emoji: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "reactions").delete {
        filter {
            eq("post_id", postId)
            eq("user_id", userId)
            eq("emoji", emoji)
        }
    }
}.fold({ null }, { it.toError() })

// MARK: - Posts: views / delete / edit (Swift incrementCommunityPostView / deletePost / editPost)

/** Bump a post's view counter (`community.increment_view_count(post_id)`). */
suspend fun SupabaseController.incrementCommunityPostView(postId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.rpc("increment_view_count", buildJsonObject { put("post_id", postId) }) { schema = COMMUNITY_SCHEMA }
}.fold({ null }, { it.toError() })

/** Delete a post the user owns (`community.delete_post(post_id)`). */
suspend fun SupabaseController.deleteCommunityPost(postId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.rpc("delete_post", buildJsonObject { put("post_id", postId) }) { schema = COMMUNITY_SCHEMA }
}.fold({ null }, { it.toError() })

/** Delete a comment the user owns (`community.delete_comment(comment_id)`). */
suspend fun SupabaseController.deleteCommunityComment(commentId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.rpc("delete_comment", buildJsonObject { put("comment_id", commentId) }) { schema = COMMUNITY_SCHEMA }
}.fold({ null }, { it.toError() })

/** Edit a text post's title + body (Swift `editPost`). */
suspend fun SupabaseController.editCommunityPost(postId: String, title: String, body: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "posts").update({
        set("title", title)
        set("body", body)
    }) { filter { eq("id", postId) } }
}.fold({ null }, { it.toError() })

/** Edit a video post's title only (Swift `editVideoPost`). */
suspend fun SupabaseController.editCommunityVideoPost(postId: String, title: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "posts").update({
        set("title", title)
    }) { filter { eq("id", postId) } }
}.fold({ null }, { it.toError() })

/** A single post with embeds (`community.get_post_by_id(p_post_id)`). */
suspend fun SupabaseController.getCommunityPostById(postId: String): Result<Post> = runCatching {
    client.postgrest.rpc("get_post_by_id", buildJsonObject { put("p_post_id", postId) }) { schema = COMMUNITY_SCHEMA }
        .decodeAs<Post>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "get post by id failed: ${it.message}") }

// MARK: - Profiles (Swift getCommunityUser / getCommunityCommentsOwner)

/** Resolve a single community profile (`community.profiles`). */
suspend fun SupabaseController.getCommunityUser(userId: String): Result<UserCommunity?> = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "profiles")
        .select { filter { eq("id", userId) } }
        .decodeList<UserCommunity>()
        .firstOrNull()
}.onFailure { android.util.Log.w("SupabaseCommunity", "community user failed: ${it.message}") }

/** Batch-resolve comment/post author profiles (`community.profiles`, id IN ...). */
suspend fun SupabaseController.getCommunityCommentsOwner(userIds: List<String>): Result<List<UserCommunity>> = runCatching {
    if (userIds.isEmpty()) return@runCatching emptyList()
    client.postgrest.from(COMMUNITY_SCHEMA, "profiles")
        .select { filter { isIn("id", userIds) } }
        .decodeList<UserCommunity>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "comment owners failed: ${it.message}") }

// MARK: - Activity feed (Swift getCommunityActivity / setActivityIsRead / checkActivity)

/** Paged activity feed (`community.get_activity_feed(start_range, end_range)`). */
suspend fun SupabaseController.getCommunityActivity(start: Int = 0, end: Int = 20): Result<List<Activity>> = runCatching {
    val params = buildJsonObject {
        put("start_range", start)
        put("end_range", end)
    }
    client.postgrest.rpc("get_activity_feed", params) { schema = COMMUNITY_SCHEMA }
        .decodeList<Activity>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "activity feed failed: ${it.message}") }

/** Mark one activity row read (Swift `setActivityIsRead`). */
suspend fun SupabaseController.setActivityIsRead(userId: String, activityId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "activities").update({
        set("is_read", true)
    }) {
        filter {
            eq("id", activityId)
            eq("recipient_id", userId)
        }
    }
}.fold({ null }, { it.toError() })

/** True if the user has any unread activity (Swift `checkActivity`; fetches ≤1 row). */
suspend fun SupabaseController.checkCommunityActivity(userId: String): Boolean = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "activities")
        .select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
            filter {
                eq("recipient_id", userId)
                eq("is_read", false)
            }
            range(0, 0)
        }
        .decodeList<ActivityIdRow>()
        .isNotEmpty()
}.getOrDefault(false)

/** True if the user has posted at all (Swift `checkMyPosts`; fetches ≤1 row). */
suspend fun SupabaseController.checkMyPosts(userId: String): Boolean = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "posts")
        .select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
            filter { eq("user_id", userId) }
            range(0, 0)
        }
        .decodeList<ActivityIdRow>()
        .isNotEmpty()
}.getOrDefault(false)

@Serializable
private data class ActivityIdRow(val id: String)

// MARK: - Reporting & prompts (Swift reportPost / getPrompts)

@Serializable
private data class ReportRow(val user_id: String, val post_id: String)

/**
 * Report a post (Swift `reportPost`). Returns true if a new report was filed, false
 * if the user had already reported it. Mirrors the iOS "check then insert" guard.
 */
suspend fun SupabaseController.reportPost(userId: String, postId: String): Pair<Boolean?, SupabaseFunctionError?> = runCatching {
    val existing = client.postgrest.from(COMMUNITY_SCHEMA, "reported_posts")
        .select {
            filter {
                eq("user_id", userId)
                eq("post_id", postId)
            }
            range(0, 0)
        }
        .decodeList<ReportRow>()
    if (existing.isEmpty()) {
        client.postgrest.from(COMMUNITY_SCHEMA, "reported_posts").insert(ReportRow(userId, postId))
        true
    } else {
        false
    }
}.fold({ it to null }, { null to it.toError() })

/** Active daily writing prompts (`community.community_prompts`, start ≤ today ≤ end). */
suspend fun SupabaseController.getCommunityPrompts(today: String): Result<List<org.clear30.data.model.CommunityPrompt>> = runCatching {
    client.postgrest.from(COMMUNITY_SCHEMA, "community_prompts")
        .select(io.github.jan.supabase.postgrest.query.Columns.list("id", "title", "prompt", "start_date", "end_date", "tag")) {
            filter {
                lte("start_date", today)
                gte("end_date", today)
            }
        }
        .decodeList<org.clear30.data.model.CommunityPrompt>()
}.onFailure { android.util.Log.w("SupabaseCommunity", "prompts failed: ${it.message}") }

/** Posts matching a set of prompt titles (`community.get_posts_by_titles`). */
suspend fun SupabaseController.getCommunityPostsByTitles(
    titles: List<String>,
    minComments: Int = 1,
    minDate: String? = null,
    sortBy: String = "views",
    limitCount: Int? = null,
): Result<List<Post>> = runCatching {
    val params = buildJsonObject {
        put("titles", JsonArray(titles.map { JsonPrimitive(it) }))
        put("min_comments", minComments)
        put("sort_by", sortBy)
        minDate?.let { put("min_date", it) }
        limitCount?.let { put("limit_count", it) }
    }
    // The RPC returns { data, total_count } like get_filtered_posts (iOS
    // decodes FilteredPost) — decodeList on the object shape silently threw
    // and every prompt search came back empty.
    client.postgrest.rpc("get_posts_by_titles", params) { schema = COMMUNITY_SCHEMA }
        .decodeAs<FilteredPostsResponse>()
        .data
}.onFailure { android.util.Log.w("SupabaseCommunity", "posts by titles failed: ${it.message}") }
