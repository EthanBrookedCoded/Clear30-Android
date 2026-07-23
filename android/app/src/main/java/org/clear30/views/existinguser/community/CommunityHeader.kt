package org.clear30.views.existinguser.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.model.Activity
import org.clear30.data.model.Post
import org.clear30.data.model.PostTag
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getCommunityActivity
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.data.supabase.getCommunityPostById
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.setActivityIsRead
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.PillPicker
import org.clear30.views.components.PillPickerStyle
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Feed sort (iOS `SortMode`): Newest -> recent, Top -> engagement. */
internal enum class SortMode(val label: String, val rpc: String) {
    NEWEST("Newest", "recent"),
    TOP("Top", "engagement"),
}

/** Time window for "Top" (iOS `DateRange`); null = all time. */
internal enum class DateRange(val label: String, val days: Int?) {
    WEEK("7d", 7), MONTH("30d", 30), THREE("90d", 90), ALL("All", null),
}

/** Community header — title, the filter/bell/+ icons, and the sort + time toggles. */
@Composable
internal fun CommunityHeader(
    sortMode: SortMode,
    onSort: (SortMode) -> Unit,
    range: DateRange,
    onRange: (DateRange) -> Unit,
    unreadActivity: Boolean,
    onFilter: () -> Unit,
    onActivity: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        Modifier.padding(top = Dimens.headingTopPadding, bottom = Dimens.cardSpacing / 2),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Heading1("Community")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                CircleIconBtn("line.3.horizontal.decrease", onFilter)
                Box {
                    CircleIconBtn("bell.fill", onActivity)
                    // Unread-activity dot (iOS CommunityFeed): 8pt red circle
                    // tucked into the bell's top-trailing corner.
                    if (unreadActivity) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Clear30Colors.red1),
                        )
                    }
                }
                CircleIconBtn("plus", onCreate)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            PillPicker(sortMode, SortMode.values().toList(), { it.label }, onSort, style = PillPickerStyle.Primary())
            if (sortMode == SortMode.TOP) {
                PillPicker(range, DateRange.values().toList(), { it.label }, onRange, style = PillPickerStyle.Secondary)
            }
        }
    }
}

@Composable
private fun CircleIconBtn(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Clear30Colors.opacityGray).pressScale(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(icon), contentDescription = icon, tint = Clear30Colors.text, modifier = Modifier.size(18.dp))
    }
}

/** Filter-by-Tag bottom sheet — gray pills, colored gradient when selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagFilterSheet(
    tags: List<PostTag.Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Own padding (bottom headingTopPadding) instead of the standard sheet
    // content padding.
    Clear30Sheet(onDismiss = onDismiss, contentPadding = false) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding).padding(bottom = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("Filter by Tag")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                tags.forEach { tag -> FilterTagPill(tag, selected = tag.id in selected) { onToggle(tag.id) } }
            }
            // iOS TextIconButton("Done", "checkmark") — a full-width primary
            // button below the tag flow (AllTagView.swift:60-62), W20.
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Clear30Gradients.button)
                    .pressScale { onDone() }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallText("Done")
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                Icon(
                    sfSymbol("checkmark"),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterTagPill(tag: PostTag.Tag, selected: Boolean, onClick: () -> Unit) {
    val accent = tagAccent(tag)
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .then(if (selected) Modifier.background(accent) else Modifier.background(Clear30Colors.opacityGray))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        SmallText(tag.name, color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.6f))
    }
}

/** Activity — iOS ActivityView modes (Notifications is the default tab). */
internal enum class ActivityMode(val label: String) { NOTIFICATIONS("Notifications"), MY_POSTS("My Posts") }

/** iOS ActivityViewModel page sizes: 10 notifications / 5 posts per page. */
private const val NOTIFICATIONS_PAGE_SIZE = 10
private const val MY_POSTS_PAGE_SIZE = 5

@Composable
internal fun ActivityScreen(
    userInfo: UserInfo,
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onEditPost: (Post) -> Unit = {},
    onPostsChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ActivityMode.NOTIFICATIONS) }

    // Notifications feed (iOS ActivityViewModel.notifications).
    val notifications = remember { mutableStateListOf<Activity>() }
    var notificationsPage by remember { mutableIntStateOf(0) }
    var notificationsEnded by remember { mutableStateOf(false) }
    var notificationsLoading by remember { mutableStateOf(true) }

    // My posts (iOS ActivityViewModel.myPosts).
    val myPosts = remember { mutableStateListOf<Post>() }
    var myPostsPage by remember { mutableIntStateOf(0) }
    var myPostsEnded by remember { mutableStateOf(false) }
    var myPostsLoading by remember { mutableStateOf(true) }

    suspend fun loadActivities(showLoading: Boolean) {
        if (notificationsEnded) return
        if (showLoading) notificationsLoading = true
        val start = notificationsPage * NOTIFICATIONS_PAGE_SIZE
        val loaded = SupabaseController.getCommunityActivity(start, start + NOTIFICATIONS_PAGE_SIZE - 1)
            .getOrNull()
        notificationsLoading = false
        if (loaded == null) {
            org.clear30.data.AlertHandler.error(message = "Could not get activity.")
            return
        }
        if (loaded.isEmpty()) {
            notificationsEnded = true
        } else {
            // iOS drops rows without a message (nothing to render on the card).
            notifications.addAll(loaded.filter { a -> a.message != null && notifications.none { it.id == a.id } })
            notificationsEnded = loaded.size < NOTIFICATIONS_PAGE_SIZE
            if (!notificationsEnded) notificationsPage++
        }
    }

    suspend fun loadMyPosts(showLoading: Boolean) {
        if (myPostsEnded) return
        if (showLoading) myPostsLoading = true
        val start = myPostsPage * MY_POSTS_PAGE_SIZE
        val loaded = SupabaseController.getCommunityFeed(
            start = start,
            end = start + MY_POSTS_PAGE_SIZE - 1,
            onlyMyPosts = true,
        ).getOrNull()
        myPostsLoading = false
        if (loaded == null) {
            org.clear30.data.AlertHandler.error(message = "Could not get posts.")
            return
        }
        if (loaded.isEmpty()) {
            myPostsEnded = true
        } else {
            myPosts.addAll(loaded.filter { p -> myPosts.none { it.id == p.id } })
            myPosts.sortByDescending { it.createdAt }
            myPostsEnded = loaded.size < MY_POSTS_PAGE_SIZE
            if (!myPostsEnded) myPostsPage++
            loaded.forEach { org.clear30.data.UserDirectory.lookup(it.userId) }
            org.clear30.data.UserDirectory.flush()
        }
    }

    LaunchedEffect(Unit) {
        org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.openedCommunityActivity)
    }

    // iOS ActivityView.reload(mode): switching tabs resets + reloads that tab.
    LaunchedEffect(mode) {
        when (mode) {
            ActivityMode.NOTIFICATIONS -> {
                org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.openedCommunityNotifications)
                notifications.clear()
                notificationsPage = 0
                notificationsEnded = false
                loadActivities(showLoading = true)
            }
            ActivityMode.MY_POSTS -> {
                org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.openedCommunityMyPosts)
                myPosts.clear()
                myPostsPage = 0
                myPostsEnded = false
                loadMyPosts(showLoading = true)
            }
        }
    }

    // iOS handleOpenActivity: mark read (locally + community.activities), then
    // resolve the post via get_post_by_id and open its detail.
    fun openActivity(activity: Activity) {
        val idx = notifications.indexOfFirst { it.id == activity.id }
        if (idx >= 0) notifications[idx] = notifications[idx].copy(isRead = true)
        scope.launch {
            val uid = SupabaseController.getUserID() ?: userInfo.userID
            SupabaseController.setActivityIsRead(uid, activity.id)
        }
        val postId = activity.postId ?: return
        scope.launch {
            SupabaseController.getCommunityPostById(postId)
                .onSuccess { onOpenPost(it) }
                .onFailure { org.clear30.data.AlertHandler.error(message = "Could not load post.") }
        }
    }

    // Both Activity lists are scroll containers, so they clip at their own
    // bounds — and `softShadow` draws OUTSIDE the card's layout bounds. The
    // horizontal edges were already handled (scrollShadowBleed + horizontal
    // contentPadding); the VERTICAL edges were still clipping: the list started
    // flush under the PillPicker and ended flush at the Column's bottom inset,
    // so the first card's top shadow and the last card's bottom shadow were cut
    // at the list bounds. Fix: the Column no longer spaces or pads around the
    // lists at all — each list owns those insets as vertical contentPadding, so
    // they sit INSIDE the clip (iOS scrollShadowFix pattern).
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding).padding(top = Dimens.headingTopPadding)) {
        Row(
            Modifier.padding(bottom = Dimens.cardSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            IconButton("chevron.backward", onClick = onBack)
            Heading3("Activity", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        PillPicker(mode, ActivityMode.values().toList(), { it.label }, { mode = it }, style = PillPickerStyle.Primary(Clear30Gradients.community))

        when (mode) {
            ActivityMode.NOTIFICATIONS -> {
                if (notificationsLoading) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(top = Dimens.cardSpacing))
                } else if (notifications.isEmpty()) {
                    SmallText(
                        "No activity, yet...",
                        modifier = Modifier.padding(top = Dimens.cardSpacing),
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                } else {
                    LazyColumn(
                        // Widen the clip past the parent's 25dp inset + re-pad via
                        // contentPadding so card shadows aren't cut at the sides;
                        // the vertical contentPadding does the same for the first
                        // card's top shadow and the last card's bottom shadow,
                        // which the list bounds were clipping.
                        modifier = Modifier.scrollShadowBleed(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Dimens.scrollShadowFix,
                            vertical = Dimens.scrollShadowFix,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        items(notifications, key = { it.id }) { activity ->
                            CommunityNotificationCard(activity) { openActivity(activity) }
                        }
                        if (!notificationsEnded) {
                            item {
                                // Composes when scrolled into view → next page.
                                LaunchedEffect(notificationsPage) { loadActivities(showLoading = false) }
                            }
                        }
                    }
                }
            }
            ActivityMode.MY_POSTS -> {
                if (myPostsLoading) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(top = Dimens.cardSpacing))
                } else if (myPosts.isEmpty()) {
                    SmallText(
                        "No posts, yet...",
                        modifier = Modifier.padding(top = Dimens.cardSpacing),
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                } else {
                    LazyColumn(
                        // Same as Notifications: horizontal bleed for the side
                        // shadows, vertical contentPadding for the first/last
                        // card's top/bottom shadow (clipped at the list bounds).
                        modifier = Modifier.scrollShadowBleed(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Dimens.scrollShadowFix,
                            vertical = Dimens.scrollShadowFix,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        items(myPosts, key = { it.id }) { post ->
                            PostCard(
                                post,
                                userInfo = userInfo,
                                onEdit = onEditPost,
                                onDeleted = {
                                    myPosts.removeAll { it.id == post.id }
                                    onPostsChanged()
                                },
                            ) { onOpenPost(post) }
                        }
                        if (!myPostsEnded) {
                            item {
                                LaunchedEffect(myPostsPage) { loadMyPosts(showLoading = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * CommunityNotificationCard (iOS Cards.swift) — bell + relative time on top,
 * the activity message below, and an unread dot pinned to the card's
 * top-trailing corner until it's opened.
 */
@Composable
private fun CommunityNotificationCard(activity: Activity, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Clear30Card(modifier = Modifier.fillMaxWidth().pressScale(onClick = onClick)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // iOS tints the bell with the brand gradient; solid brand
                    // green is the closest single-color tint here.
                    Icon(
                        sfSymbol("bell.fill"),
                        contentDescription = null,
                        tint = Clear30Colors.green,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    TinyText(relativeTime(activity.createdAt), color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                activity.message?.let { SmallText(it) }
            }
        }
        if (!activity.isRead) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Clear30Colors.red1),
            )
        }
    }
}
