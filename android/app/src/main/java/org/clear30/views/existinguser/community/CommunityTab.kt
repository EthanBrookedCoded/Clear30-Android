package org.clear30.views.existinguser.community

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.reportPost
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.clear30.data.model.Post
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.checkCommunityActivity
import org.clear30.data.supabase.createCommunityPost
import org.clear30.data.supabase.deleteCommunityPost
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.data.supabase.getCommunityPostById
import org.clear30.data.supabase.getCommunityTags
import org.clear30.data.supabase.removePublicFile
import org.clear30.data.supabase.uploadPublicFile
import org.clear30.util.adding
import org.clear30.util.now
import org.clear30.util.toPngBytes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * CommunityTab — ported from the community feed (CommunityFeedViewModel /
 * Support community views). Loads the post feed from Supabase and renders post
 * cards; tapping a post (detail), creating a post, and reactions are layered on
 * with the navigation stack.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CommunityTab(program: org.clear30.data.model.Program, userInfo: org.clear30.data.model.UserInfo) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var unreadActivity by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Post?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NEWEST) }
    var range by remember { mutableStateOf(DateRange.ALL) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allTags by remember { mutableStateOf<List<org.clear30.data.model.PostTag.Tag>>(emptyList()) }
    // The feed waits for the tag load so the first query is already scoped to
    // the seeded program tag (iOS setupTags loads tags before the first page).
    var tagsReady by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Post?>(null) }
    // Bumped when a pinned post is marked opened so the Newest feed re-filters.
    var pinnedVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(reload, sortMode, range, selectedTags, tagsReady) {
        if (!tagsReady) return@LaunchedEffect
        // "Top" applies the time window (min_date); Newest ignores it.
        val minDate = if (sortMode == SortMode.TOP) range.days?.let {
            now().adding(days = -it).toLocalDateTime(TimeZone.UTC).toString()
        } else null
        val result = SupabaseController.getCommunityFeed(
            sortBy = sortMode.rpc,
            minDate = minDate,
            tagIds = selectedTags.toList().ifEmpty { null },
        )
        posts = result.getOrNull() ?: emptyList()
        error = result.exceptionOrNull()?.message
        // Prime the user-directory cache for the visible authors — touch every
        // post id so UserDirectory.flush() picks them up in a single batched
        // query, instead of N separate selects per card.
        posts?.forEach { org.clear30.data.UserDirectory.lookup(it.userId) }
        org.clear30.data.UserDirectory.flush()
        refreshing = false
    }

    LaunchedEffect(Unit) {
        SupabaseController.getCommunityTags().onSuccess { t ->
            allTags = t.filter { it.type != "day" && it.type != "lobby" }
            // iOS CommunityFeed.setupTags: seed the filter with the current
            // program's tag so the feed opens scoped to the user's program.
            t.firstOrNull { it.type == "program" && it.name == program.communityProgramTagName }
                ?.let { selectedTags = setOf(it.id) }
        }
        tagsReady = true
    }

    // Unread-activity dot on the bell (iOS checkActivity) — re-check on feed
    // refresh and whenever the Activity screen closes (reading clears it).
    LaunchedEffect(reload, showActivity) {
        if (!showActivity) {
            val uid = SupabaseController.getUserID() ?: userInfo.userID
            unreadActivity = SupabaseController.checkCommunityActivity(uid)
        }
    }

    // Drain a deep-link to a specific post (clear30://post/<id>). We wait for
    // the feed to load so we can pick the canonical Post object, but fall back
    // to a stub if the feed lookup misses (the user might have a link to a
    // post they aren't in the cached page for).
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    LaunchedEffect(sub, posts) {
        val r = sub as? org.clear30.data.DeepLinkRoute.Post ?: return@LaunchedEffect
        val loaded = posts ?: return@LaunchedEffect  // wait for the feed
        val target = loaded.firstOrNull { it.id == r.id }
            // Fall back to a stub if the post isn't on the cached feed page;
            // the detail view will still render title/body once the lookup
            // network call lands.
            ?: Post(
                id = r.id, userId = "", title = "Post",
                contentType = "text", body = "", createdAt = "",
            )
        detail = target
        org.clear30.AppState.requestSubRoute(null)
    }

    // Owner edit flow (iOS EditPostView sheet) — rendered above the detail so
    // "Edit" from the detail's menu opens on top and returns to it on close.
    edit?.let { editing ->
        androidx.activity.compose.BackHandler { edit = null }
        EditPostScreen(
            post = editing,
            userInfo = userInfo,
            onClose = { edit = null },
            onSaved = {
                edit = null
                reload++
                // Refresh an open detail so the edited title/body show at once.
                if (detail?.id == editing.id) {
                    scope.launch {
                        SupabaseController.getCommunityPostById(editing.id).onSuccess { detail = it }
                    }
                }
            },
        )
        return
    }

    detail?.let { selected ->
        // System back closes the detail instead of exiting the app.
        androidx.activity.compose.BackHandler { detail = null }
        PostDetail(
            selected,
            userInfo,
            onBack = { detail = null },
            onEdit = { edit = it },
            onDeleted = { detail = null; reload++ },
        )
        return
    }

    if (showActivity) {
        androidx.activity.compose.BackHandler { showActivity = false }
        ActivityScreen(
            userInfo,
            onBack = { showActivity = false },
            // Detail/edit render above this block, so the Activity screen stays
            // underneath (iOS presents them as sheets over ActivityView).
            onOpenPost = { detail = it },
            onEditPost = { edit = it },
            onPostsChanged = { reload++ },
        )
        return
    }

    if (showCreate) {
        androidx.activity.compose.BackHandler { showCreate = false }
        CreatePostScreen(
            program = program,
            allTags = allTags,
            onClose = { showCreate = false },
            onSubmit = { title, body, tagNames ->
                showCreate = false
                scope.launch {
                    val uid = SupabaseController.getUserID() ?: userInfo.userID
                    org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.createdCommunityPost)
                    val err = SupabaseController.createCommunityPost(
                        org.clear30.data.supabase.CreatePost(
                            p_title = title, p_content_type = "text", p_body = body, p_user_id = uid, p_tags = tagNames,
                        ),
                    )
                    if (err != null) org.clear30.data.AlertHandler.info("Couldn't post", err.message)
                    reload++
                }
            },
            onSubmitVideo = { title, tagNames, video, thumb ->
                showCreate = false
                scope.launch {
                    // Mirror iOS CreatePostView: upload clip + thumbnail to the
                    // `community` bucket under <userId>/<uuid>.{mp4,png}, then
                    // create a "video" post pointing at the public URLs.
                    val uid = SupabaseController.getUserID() ?: userInfo.userID
                    org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.createdCommunityPost)
                    val uuid = java.util.UUID.randomUUID().toString()
                    val videoUrl = SupabaseController.uploadPublicFile(
                        org.clear30.data.supabase.COMMUNITY_BUCKET, "$uid/$uuid.mp4", video.readBytes(),
                    ).getOrNull()
                    val thumbUrl = thumb?.let {
                        SupabaseController.uploadPublicFile(
                            org.clear30.data.supabase.COMMUNITY_BUCKET, "$uid/$uuid.png", it.toPngBytes(),
                        ).getOrNull()
                    }
                    video.delete()
                    if (videoUrl == null) {
                        org.clear30.data.AlertHandler.info("Couldn't post", "Video upload failed. Check your connection and try again.")
                        return@launch
                    }
                    val err = SupabaseController.createCommunityPost(
                        org.clear30.data.supabase.CreatePost(
                            p_title = title, p_content_type = "video", p_body = "", p_user_id = uid, p_tags = tagNames,
                            p_video_url = videoUrl, p_thumbnail_url = thumbUrl,
                        ),
                    )
                    if (err != null) org.clear30.data.AlertHandler.info("Couldn't post", err.message)
                    reload++
                }
            },
        )
        return
    }

    androidx.compose.material3.Scaffold { padding ->
        when (val p = posts) {
            null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                Heading1("Community", Modifier.padding(top = Dimens.headingTopPadding, bottom = Dimens.cardSpacing))
                androidx.compose.material3.CircularProgressIndicator()
            }
            else -> {
                // iOS shouldHidePost: in Newest mode, pinned posts the user has
                // already opened (cached `opened_pinned_<id>` on UserInfo) drop
                // out of the feed. (Computed here — LazyListScope isn't
                // composable, so no remember inside the LazyColumn block.)
                val visible = remember(p, sortMode, pinnedVersion) {
                    p.filter { post ->
                        !(
                            sortMode == SortMode.NEWEST && post.isPinned &&
                                userInfo.getCachedBool(OPENED_PINNED_PREFIX + post.id)
                            )
                    }
                }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; reload++ },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        item {
                            CommunityHeader(
                                sortMode = sortMode, onSort = { sortMode = it },
                                range = range, onRange = { range = it },
                                unreadActivity = unreadActivity,
                                onFilter = { showFilter = true },
                                onActivity = { showActivity = true },
                                onCreate = { showCreate = true },
                            )
                        }
                        if (p.isEmpty()) {
                            item {
                                val err = error
                                if (err != null) {
                                    Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Heading3("Couldn't load community")
                                            SmallText(err, color = Clear30Colors.red1)
                                        }
                                    }
                                } else {
                                    EmptyCommunityState(onRefresh = { reload++ })
                                }
                            }
                        } else {
                            items(visible, key = { it.id }) { post ->
                                // animateItem() smooths inserts/reorders when the feed
                                // reloads on a sort/filter/refresh change.
                                Box(Modifier.animateItem()) {
                                    PostCard(
                                        post,
                                        userInfo = userInfo,
                                        onEdit = { edit = it },
                                        onDeleted = { reload++ },
                                    ) {
                                        // iOS markPinnedPostOpened: opening a pinned
                                        // post hides it from Newest on return.
                                        if (post.isPinned) {
                                            userInfo.setCacheBool(OPENED_PINNED_PREFIX + post.id, true)
                                            scope.launch { org.clear30.data.Clear30Store.save(userInfo) }
                                            pinnedVersion++
                                        }
                                        detail = post
                                        org.clear30.data.Logger.logEvent(
                                            userInfo.loggingID,
                                            org.clear30.data.LogEventType.openedCommunityPost,
                                            mapOf(org.clear30.data.LogEventExtraDataType.ID to post.id),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        // iOS handleTagPickerDismiss logs a filter edit when the selection
        // actually changed while the sheet was up.
        val tagsBeforeSheet = remember { selectedTags }
        val closeFilter = {
            showFilter = false
            if (selectedTags != tagsBeforeSheet) {
                org.clear30.data.Logger.logEvent(
                    userInfo.loggingID,
                    org.clear30.data.LogEventType.editedCommunityFeedFilters,
                )
            }
        }
        TagFilterSheet(
            tags = allTags,
            selected = selectedTags,
            onToggle = { id -> selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id },
            onDone = closeFilter,
            onDismiss = closeFilter,
        )
    }
}

/** UserInfo cache-key prefix for opened pinned posts (iOS `openedPinnedPrefix`). */
internal const val OPENED_PINNED_PREFIX = "opened_pinned_"

/**
 * The community tag name for the user's current program — iOS
 * `Program.communityProgramTagName` (TagModel.swift): "Clear30" during a break,
 * "Clear30 Preparation" during start-soon, otherwise the core program name.
 */
internal val org.clear30.data.model.Program.communityProgramTagName: String
    get() = when (currentBreak?.type) {
        org.clear30.data.model.ProgramBreakType.CLEAR30 -> "Clear30"
        org.clear30.data.model.ProgramBreakType.CLEAR30_START_SOON -> "Clear30 Preparation"
        null -> coreProgramName
    }

/** iOS empty feed: centered "No posts, yet..." + a TinyTextButton "Refresh". */
@Composable
private fun EmptyCommunityState(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        SmallText(
            "No posts, yet...",
            color = Clear30Colors.text.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
        // iOS TinyTextButton(icon: "arrow.clockwise"): TinyText + 10pt icon on an
        // opacityGray pill; the whole button sits at 0.7 opacity (→ 0.75 per the
        // alpha rule, same rounding as the assessment TinyTextButton port).
        Row(
            Modifier
                .pressScale(onClick = onRefresh)
                .alpha(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(Clear30Colors.opacityGray)
                .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            TinyText("Refresh")
            androidx.compose.material3.Icon(
                sfSymbol("arrow.clockwise"),
                contentDescription = null,
                tint = Clear30Colors.text,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
internal fun PostCard(
    post: Post,
    userInfo: org.clear30.data.model.UserInfo,
    onEdit: (Post) -> Unit = {},
    onDeleted: () -> Unit = {},
    onClick: () -> Unit,
) {
    // Author label sourced from the shared UserDirectory cache (batched
    // public.users lookup). The cache returns a stable "User #abc123" handle
    // on cold-miss so the row never renders blank; the real display name
    // streams in once the feed-wide flush() resolves.
    val directory by org.clear30.data.UserDirectory.cache.collectAsStateWithLifecycle()
    val author = remember(post.userId, directory) {
        org.clear30.data.UserDirectory.lookup(post.userId)
    }
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            // Author row: "emoji name · relative-time"  + overflow menu.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TinyText(
                    buildString {
                        author.emoji?.let { append(it).append(" ") }
                        append(author.displayName)
                    },
                    color = Clear30Colors.text.copy(alpha = 0.75f),
                )
                TinyText(" · ${relativeTime(post.createdAt)}", color = Clear30Colors.text.copy(alpha = 0.25f))
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (!post.isPinned) {
                    PostOverflowMenu(post, userInfo, onEdit = onEdit, onDeleted = onDeleted)
                }
            }

            // Video posts: show the poster frame + play badge (tap the card to
            // open the post, where it plays inline). Falls back to a gray box +
            // play glyph when no thumbnail was uploaded.
            if (post.isVideo) {
                // iOS FeedCardMedia renders video at 1:1.5 (portrait).
                org.clear30.views.components.VideoThumbnail(post.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f / 1.5f))
            }
            Heading3(post.title)

            // Tag pills — colored by the tag (program=green, day=blue, else its hex).
            val tags = post.postTags.orEmpty().mapNotNull { it.tag }
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.take(4).forEach { tag -> TagPill(tag) }
                }
            }

            if (post.body.isNotBlank()) SmallText(post.body, color = Clear30Colors.text.copy(alpha = 0.75f), maxLines = 3)

            // Footer: comment + view counts on the left, reaction pills on the right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FeedStat("bubble.fill", compactCount(post.totalCommentsCount))
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                FeedStat("chart.bar.fill", compactCount(post.viewCount))
                Spacer(Modifier.weight(1f))
                ReactionPills(post)
            }
        }
    }
}

/** Left-side feed stat: small muted icon + compact count (comments / views). */
@Composable
private fun FeedStat(icon: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.Icon(
            org.clear30.views.components.sfSymbol(icon),
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.25f),
            modifier = Modifier.size(16.dp),
        )
        TinyText(value, color = Clear30Colors.text.copy(alpha = 0.25f))
    }
}

/**
 * ReactionPills — the iOS feed reaction tray. The feed gives a raw `reactions`
 * array (emoji + user_id), so we aggregate it into emoji→count (or use
 * `reactionCounts` when present), show the top 3 as compact pills.
 */
@Composable
private fun ReactionPills(post: Post) {
    val counts = remember(post.reactionCounts, post.reactions) {
        post.reactionCounts?.takeIf { it.isNotEmpty() }?.associate { it.emoji to it.count }
            ?: post.reactions.orEmpty().groupingBy { it.emoji }.eachCount()
    }
    val top = counts.entries.sortedByDescending { it.value }.take(3)
    if (top.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        top.forEach { (emoji, count) -> CompactReactionPill(emoji, count) }
    }
}

@Composable
private fun CompactReactionPill(emoji: String, count: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Clear30Colors.opacityGrayFlattened)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        org.clear30.views.components.MiniText(emoji)
        org.clear30.views.components.MiniText(compactCount(count), color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

/** Compact count formatting (Swift `Int.compact`): 1100 -> "1.1k", 29000 -> "29k". */
internal fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> trimCountUnit(n / 1_000_000.0) + "M"
    n >= 1_000 -> trimCountUnit(n / 1_000.0) + "k"
    else -> n.toString()
}

private fun trimCountUnit(v: Double): String =
    (if (v < 10) "%.1f".format(v) else v.toInt().toString()).removeSuffix(".0")

/** Relative timestamp ("now"/"5m"/"3d"/"10mo"/"1y") from an ISO created_at string. */
internal fun relativeTime(createdAt: String?): String {
    val s = createdAt?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    // `timestamp without time zone` payloads have no offset — assume UTC.
    val hasZone = s.endsWith("Z") || s.contains("+") || Regex("-\\d{2}:\\d{2}$").containsMatchIn(s)
    val iso = s.replace(" ", "T").let { if (hasZone) it else "${it}Z" }
    val instant = runCatching { kotlinx.datetime.Instant.parse(iso) }.getOrNull() ?: return ""
    // iOS FeedCardText.relativeTime: clamp to 0, then now/m/h/d, weeks up to
    // "4w" (weeks < 5), months up to "11mo", then years (min "1y").
    val secs = maxOf(0L, (org.clear30.util.now() - instant).inWholeSeconds)
    val days = secs / 86_400
    val weeks = days / 7
    val months = days / 30
    return when {
        secs < 60 -> "now"
        secs < 3_600 -> "${secs / 60}m"
        secs < 86_400 -> "${secs / 3_600}h"
        days < 7 -> "${days}d"
        weeks < 5 -> "${weeks}w"
        months < 12 -> "${months}mo"
        else -> "${maxOf(1L, days / 365)}y"
    }
}

/**
 * TagPill — small rounded category tag rendered next to the post body. Color
 * stays neutral (gray-on-light) so we don't introduce a third gradient color
 * to compete with the brand gradient and the prompts row above the feed.
 */
@Composable
internal fun TagPill(tag: org.clear30.data.model.PostTag.Tag) {
    val accent = tagAccent(tag)
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(accent.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        org.clear30.views.components.MiniText(tag.name, color = accent)
    }
}

/** Tag accent color: program=green, day=blue, otherwise the tag's own hex. */
internal fun tagAccent(tag: org.clear30.data.model.PostTag.Tag): androidx.compose.ui.graphics.Color = when (tag.type) {
    "program" -> Clear30Colors.green
    "day" -> Clear30Colors.meditation1
    else -> tag.color?.let { runCatching { org.clear30.views.theme.colorFromHex(it) }.getOrNull() } ?: Clear30Colors.green
}

/**
 * PostOverflowMenu — the "⋯" menu from iOS PostDetailView's header, shared by
 * the feed card and the detail screen. Owners get Edit + Delete (with the iOS
 * confirm alert); everyone else gets Report. Hidden entirely for pinned posts
 * (iOS gates the Menu on `!post.isPinned`).
 */
@Composable
internal fun PostOverflowMenu(
    post: Post,
    userInfo: org.clear30.data.model.UserInfo,
    onEdit: (Post) -> Unit,
    onDeleted: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isOwner = post.userId.isNotEmpty() && post.userId == userInfo.userID
    Box {
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            androidx.compose.material3.Icon(
                org.clear30.views.components.sfSymbol("ellipsis"),
                contentDescription = "More",
                tint = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (isOwner) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text("Edit") },
                    onClick = {
                        expanded = false
                        onEdit(post)
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text("Delete") },
                    onClick = {
                        expanded = false
                        // iOS handleDelete: yes/no confirm, then delete_post RPC
                        // + remove the clip/thumbnail from the community bucket.
                        org.clear30.data.AlertHandler.show(
                            org.clear30.data.AlertHandler.Alert(
                                title = "Delete⁉️",
                                message = "Are you sure you want to delete your post? This action can't be undone.",
                                primaryLabel = "Yes",
                                onPrimary = {
                                    scope.launch { deletePostAndFiles(post, userInfo, onDeleted) }
                                },
                                secondaryLabel = "No",
                            ),
                        )
                    },
                )
            } else {
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text("Report") },
                    onClick = {
                        expanded = false
                        // iOS reportPost: check-then-insert into
                        // community.reported_posts; duplicate reports get the
                        // "already reported" copy.
                        scope.launch {
                            val uid = SupabaseController.getUserID() ?: userInfo.userID
                            val (created, err) = SupabaseController.reportPost(uid, post.id)
                            org.clear30.data.Logger.logEvent(
                                userInfo.loggingID,
                                org.clear30.data.LogEventType.reportedCommunityPost,
                            )
                            if (err != null || created != true) {
                                org.clear30.data.AlertHandler.info("Success 💯", "Post already reported.")
                            } else {
                                org.clear30.data.AlertHandler.info("Success 💯", "Post reported.")
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Delete a post + its uploaded media (iOS PostViewModel.deletePost): the
 * `community.delete_post` RPC, then best-effort removal of the video/thumbnail
 * from the community bucket, then the success alert + parent refresh.
 */
internal suspend fun deletePostAndFiles(
    post: Post,
    userInfo: org.clear30.data.model.UserInfo,
    onDeleted: () -> Unit,
) {
    val err = SupabaseController.deleteCommunityPost(post.id)
    if (err != null) {
        org.clear30.data.AlertHandler.error("Error 😞", "Could not delete post. ${err.message}")
        return
    }
    post.videoUrl?.takeIf { it.isNotBlank() }?.let {
        SupabaseController.removePublicFile(org.clear30.data.supabase.COMMUNITY_BUCKET, it)
    }
    post.thumbnailUrl?.takeIf { it.isNotBlank() }?.let {
        SupabaseController.removePublicFile(org.clear30.data.supabase.COMMUNITY_BUCKET, it)
    }
    org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.deletedCommunityPost)
    org.clear30.data.AlertHandler.info("Success 💯", "Post deleted.")
    onDeleted()
}

