package org.clear30.views.existinguser.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.GroupController
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupActivityItem
import org.clear30.data.model.Clear30GroupActivityItemType
import org.clear30.data.model.Clear30GroupChatMessage
import org.clear30.data.model.Clear30GroupMember
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.deleteGroupChatMessage
import org.clear30.data.supabase.getGroupActivityItems
import org.clear30.data.supabase.getGroupChatMessages
import org.clear30.data.supabase.sendGroupChatMessage
import org.clear30.util.adding
import org.clear30.util.isSameDay
import org.clear30.util.isToday
import org.clear30.util.justDay
import org.clear30.views.components.ChatComposer
import org.clear30.views.components.Heading3
import org.clear30.views.components.LoadingIcon
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/** How often the group chat re-fetches to pick up others' messages and activity. */
private const val GROUP_CHAT_POLL_INTERVAL_MS = 5_000L

/** How many messages / activity items each fetch pulls. */
private const val GROUP_CHAT_PAGE = 30

/**
 * GroupChat — the group's shared thread of messages AND activity events, backed
 * by `groups.group_messages` + `groups.group_activity` (iOS GroupChat.swift +
 * GroupChatViewModel). Renders INLINE under the shared GroupAll heading + pill
 * picker, with no header of its own. Items are merged by timestamp and grouped
 * by date with a separator row; message bubbles carry the sender's name above
 * and the member's emoji avatar beside them (left for others, right for self,
 * bottom-aligned); activity events render as centered gray cards that open the
 * send-note popup when a response makes sense (iOS `canResponse`). The user
 * sends optimistically via the shared composer and long-presses their own
 * bubbles to delete; new rows arrive on a poll loop (Android stand-in for iOS
 * Realtime).
 */
@Composable
fun GroupChat(group: Clear30Group, userInfo: UserInfo, controller: GroupController) {
    val scope = rememberCoroutineScope()
    val msgById = remember { mutableStateMapOf<String, Clear30GroupChatMessage>() }
    val actById = remember { mutableStateMapOf<String, Clear30GroupActivityItem>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var noteTarget by remember { mutableStateOf<Clear30GroupMember?>(null) }
    // "Have we seen the full history of this stream?" — a fetch returning fewer
    // rows than requested means yes (iOS hasLoadedAllMessages/hasLoadedAllActivity).
    var allMessagesLoaded by remember { mutableStateOf(false) }
    var allActivityLoaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun mergeMessages(incoming: List<Clear30GroupChatMessage>, requested: Int) {
        val dropOptimistic = mutableListOf<String>()
        incoming.forEach { m ->
            if (m.isDeleted) {
                msgById.remove(m.id)
            } else {
                msgById[m.id] = m
                if (m.userId == userInfo.userID) {
                    msgById.values.filter { it.id.startsWith("local-") && it.userId == userInfo.userID && it.message == m.message }
                        .forEach { dropOptimistic.add(it.id) }
                }
            }
        }
        dropOptimistic.forEach { msgById.remove(it) }
        if (incoming.size < requested) allMessagesLoaded = true
    }

    fun mergeActivities(incoming: List<Clear30GroupActivityItem>, requested: Int) {
        incoming.forEach { actById[it.id] = it }
        if (incoming.size < requested) allActivityLoaded = true
    }

    LaunchedEffect(group.id) {
        val errors = mutableListOf<String>()
        SupabaseController.getGroupChatMessages(group.id, count = GROUP_CHAT_PAGE)
            .onSuccess { mergeMessages(it, GROUP_CHAT_PAGE) }
            .onFailure { errors.add("Could not load messages. ${it.message ?: ""}".trim()) }
        SupabaseController.getGroupActivityItems(group.id, count = GROUP_CHAT_PAGE)
            .onSuccess { mergeActivities(it, GROUP_CHAT_PAGE) }
            .onFailure { errors.add("Could not load group activity. ${it.message ?: ""}".trim()) }
        if (errors.isNotEmpty()) error = errors.joinToString("\n")
        loading = false
    }
    LaunchedEffect(group.id) {
        while (true) {
            delay(GROUP_CHAT_POLL_INTERVAL_MS)
            SupabaseController.getGroupChatMessages(group.id, count = GROUP_CHAT_PAGE)
                .onSuccess { mergeMessages(it, GROUP_CHAT_PAGE) }
            SupabaseController.getGroupActivityItems(group.id, count = GROUP_CHAT_PAGE)
                .onSuccess { mergeActivities(it, GROUP_CHAT_PAGE) }
        }
    }

    // Merge the two streams chronologically. Both are fetched newest-first with
    // a page limit, so keep "chronological completeness" like the iOS view model
    // (GroupChatViewModel.updateVisibleMessages): only show an item if the whole
    // opposite stream is loaded, or the item is newer than the oldest loaded
    // item of the opposite stream — otherwise old rows would interleave with
    // gaps in the other stream.
    val messages = msgById.values.filter { !it.isDeleted }
    val activities = actById.values.filter { it.type != null }
    val oldestMessage = messages.minOfOrNull { it.orderDate }
    val oldestActivity = activities.minOfOrNull { it.orderInstant }
    val ordered: List<GroupChatItem> = buildList {
        messages.forEach { m ->
            if (allActivityLoaded || (oldestActivity != null && m.orderDate >= oldestActivity)) {
                add(GroupChatItem.Message(m))
            }
        }
        activities.forEach { a ->
            if (allMessagesLoaded || (oldestMessage != null && a.orderInstant >= oldestMessage)) {
                add(GroupChatItem.Activity(a))
            }
        }
    }.sortedBy { it.orderDate }

    LaunchedEffect(ordered.size) {
        val rows = displayRowCount(ordered)
        if (rows > 0) listState.animateScrollToItem(rows - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        val optimistic = Clear30GroupChatMessage(
            id = "local-${org.clear30.util.now().toEpochMilliseconds()}",
            groupId = group.id,
            userId = userInfo.userID,
            message = text,
            timestamp = org.clear30.util.now().toString(),
            isDeleted = false,
        )
        msgById[optimistic.id] = optimistic
        scope.launch {
            val err = SupabaseController.sendGroupChatMessage(group.id, userInfo.userID, text)
            if (err != null) {
                msgById.remove(optimistic.id)
                error = "Could not send. ${err.message}".trim()
            } else {
                delay(1_200)
                SupabaseController.getGroupChatMessages(group.id, count = 15).onSuccess { mergeMessages(it, 15) }
            }
            sending = false
        }
    }

    fun delete(message: Clear30GroupChatMessage) {
        if (message.userId != userInfo.userID) return
        msgById.remove(message.id)
        if (message.id.startsWith("local-")) return
        scope.launch {
            val err = SupabaseController.deleteGroupChatMessage(message.id)
            if (err != null) {
                msgById[message.id] = message
                error = "Could not delete. ${err.message}".trim()
            }
        }
    }

    when {
        // Loading (iOS: LoadingIcon filling the section).
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIcon() }

        // Error (iOS: Heading3("Error") + dim message).
        error != null && ordered.isEmpty() -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Spacer(Modifier.weight(1f))
            Heading3("Error")
            SmallText(
                error!!,
                color = Clear30Colors.text.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
        }

        // Match the app's other chat screens: AllTabs/CustomTabBar already
        // spends the navigation-bar inset, while imePadding keeps the composer
        // directly above the software keyboard when it opens.
        else -> Column(
            Modifier.fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            if (ordered.isEmpty()) {
                // iOS empty state (GroupChat.swift:45-53).
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SmallText(
                        "Send a message\nto your group!",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    forEachChatDay(ordered) { dayLabel, item ->
                        if (dayLabel != null) {
                            item { ChatDateSeparator(dayLabel) }
                        } else when (item) {
                            is GroupChatItem.Message -> item(key = item.id) {
                                val message = item.message
                                val member = group.members.firstOrNull { it.memberID == message.userId }
                                if (member != null) {
                                    GroupChatMessageRow(
                                        name = member.name,
                                        emoji = member.emoji,
                                        message = message.message,
                                        gradient = group.gradient,
                                        isUser = member.memberID == userInfo.userID,
                                        onDelete = { delete(message) },
                                    )
                                }
                            }
                            is GroupChatItem.Activity -> item(key = item.id) {
                                val activity = item.activity
                                val member = group.members.firstOrNull { it.memberID == activity.userId }
                                if (member != null) {
                                    val canRespond = activity.canResponse && member.memberID != userInfo.userID
                                    GroupChatActivityRow(
                                        text = "${member.emoji} ${member.name} ${activity.displayString}".trim(),
                                        canRespond = canRespond,
                                        onTap = { noteTarget = member },
                                    )
                                }
                            }
                            null -> {}
                        }
                    }
                }
            }

            // Shared composer (iOS ChatComposeMessageView on the group gradient):
            // off-white multiline input + gradient send disc that only appears
            // once there's text.
            ChatComposer(
                input = input,
                onInputChange = { input = it },
                sendBrush = group.gradient,
                modifier = Modifier.padding(vertical = Dimens.headingTopPadding),
            ) { send() }
        }
    }

    // Activity tap → the shared send-note popup (iOS groupController.sendNote).
    noteTarget?.let { member ->
        SendNotePopup(
            group = group,
            member = member,
            onDismiss = { noteTarget = null },
            onSend = { msg ->
                scope.launch { controller.addNote(msg, member.memberID)?.let { error = it } }
            },
        )
    }
}

// MARK: - Thread items (iOS GroupChatMessageType)

/** One row of the merged thread — a chat message or an activity event. */
private sealed interface GroupChatItem {
    val id: String
    val orderDate: Instant

    data class Message(val message: Clear30GroupChatMessage) : GroupChatItem {
        override val id: String get() = message.id
        override val orderDate: Instant get() = message.orderDate
    }

    data class Activity(val activity: Clear30GroupActivityItem) : GroupChatItem {
        override val id: String get() = activity.id
        override val orderDate: Instant get() = activity.orderInstant
    }
}

/** iOS `Clear30GroupActivityItem.string`. */
private val Clear30GroupActivityItem.displayString: String
    get() = when (type) {
        Clear30GroupActivityItemType.SMOKED -> "smoked."
        Clear30GroupActivityItemType.SOBER -> "didn't smoke!"
        Clear30GroupActivityItemType.JOINED -> "joined the group!"
        Clear30GroupActivityItemType.MESSAGE -> "sent a note."
        null -> activity
    }

/** iOS `Clear30GroupActivityItem.canResponse` — note-worthy event types. */
private val Clear30GroupActivityItem.canResponse: Boolean
    get() = when (type) {
        Clear30GroupActivityItemType.SMOKED,
        Clear30GroupActivityItemType.SOBER,
        Clear30GroupActivityItemType.JOINED,
        -> true
        Clear30GroupActivityItemType.MESSAGE, null -> false
    }

/**
 * One chat row (iOS GroupChatMessageView): emoji avatar bottom-aligned beside
 * the bubble (left for others, right for self), TinyText sender name at half
 * opacity above the bubble, gradient + white text for the user's own bubbles.
 * Long-press an own bubble to delete it (iOS context-menu Delete).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChatMessageRow(
    name: String,
    emoji: String,
    message: String,
    gradient: Brush,
    isUser: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        if (!isUser) GroupMemberEmoji(emoji)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            TinyText(name, color = Clear30Colors.text.copy(alpha = 0.5f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .then(if (isUser) Modifier.background(gradient) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                    .then(if (isUser) Modifier.combinedClickable(onClick = {}, onLongClick = onDelete) else Modifier)
                    .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing * 0.8f),
            ) {
                TinyText(message, color = if (isUser) Color.White else Clear30Colors.text)
            }
        }
        if (isUser) GroupMemberEmoji(emoji)
    }
}

/**
 * One activity row (iOS GroupChatActivityView): a centered opacity-gray card
 * (no shadow) reading "{emoji} {name} {activity}", plus "Tap to send a note!"
 * when tapping should open the send-note popup. Inert for the user's own
 * activity and for note events.
 */
@Composable
private fun GroupChatActivityRow(text: String, canRespond: Boolean, onTap: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 4),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .then(if (canRespond) Modifier.pressScale { onTap() } else Modifier)
                .cardStyle(color = Clear30Colors.opacityGray, shadowColor = Color.Transparent),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TinyText(text)
            if (canRespond) {
                TinyText("Tap to send a note!", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/** Centered day label between message groups (iOS `ChatDateSeparatorView`). */
@Composable
private fun ChatDateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2), contentAlignment = Alignment.Center) {
        TinyText(label, color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1)
    }
}

// MARK: - Day grouping helpers

/** Number of rendered rows (a date header per day + one row per item). */
private fun displayRowCount(ordered: List<GroupChatItem>): Int {
    var days = 0
    var last: Instant? = null
    ordered.forEach { m ->
        val day = m.orderDate.justDay
        if (last == null || !day.isSameDay(last!!)) { days++; last = day }
    }
    return days + ordered.size
}

/**
 * Walks [ordered] (oldest→newest) emitting a day label before the first item
 * of each calendar day, then each item (iOS `messagesGroupedByDate`).
 */
private inline fun forEachChatDay(
    ordered: List<GroupChatItem>,
    emit: (dayLabel: String?, item: GroupChatItem?) -> Unit,
) {
    var last: Instant? = null
    ordered.forEach { m ->
        val day = m.orderDate.justDay
        if (last == null || !day.isSameDay(last!!)) {
            emit(chatSeparatorLabel(day), null)
            last = day
        }
        emit(null, m)
    }
}

/** "Today" / "Yesterday" / weekday (same week) / "Jan 15th" (iOS `formattedForChatSeparator`). */
private fun chatSeparatorLabel(day: Instant): String {
    if (day.isToday) return "Today"
    if (day.adding(days = 1).isToday) return "Yesterday"
    val date = day.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    return if (day.isSameWeekAs(org.clear30.util.now())) {
        date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        monthDayWithSuffix(date)
    }
}

/** Parse a row timestamp for ordering; unparseable sorts earliest. */
private fun parseTimestamp(raw: String): Instant =
    raw.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.DISTANT_PAST

private val Clear30GroupChatMessage.orderDate: Instant get() = parseTimestamp(timestamp)

private val Clear30GroupActivityItem.orderInstant: Instant get() = parseTimestamp(timestamp)
