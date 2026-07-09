package org.clear30.views.existinguser.support

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.DrFredMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getDrFredMessages
import org.clear30.data.supabase.sendDrFredMessage
import org.clear30.util.adding
import org.clear30.util.isSameDay
import org.clear30.util.isToday
import org.clear30.util.justDay
import org.clear30.util.now
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.LoadingIcon
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** How often the chat re-fetches to pick up Dr. Fred's replies (iOS uses Realtime). */
private const val DR_FRED_POLL_INTERVAL_MS = 5_000L
private const val FRED_BIO =
    "Hi, I'm Dr. Fred.\nI'm an addiction specialist here to help you think through " +
        "anything that's coming up — cravings, setbacks, motivation, or just how it's going.\n\n" +
        "Send me a message anytime and I'll get back to you."

/** Local seen-marker keys (mirror iOS DrFredChatViewModel cache keys). */
private const val LAST_SEEN_ID_KEY = "dr_fred_last_seen_id"
private const val LAST_PREVIEW_KEY = "dr_fred_last_message_preview"
private const val CONVERSATION_COUNT_KEY = "dr_fred_conversation_count"

/**
 * DrFredChat — ported from DrFredChat.swift. Dr. Fred (addiction specialist) is a
 * two-way message thread backed by `comms.dr_fred`. Messages are grouped by day;
 * the user sends (optimistically, via the `dr_fred_send_message` RPC) and Dr. Fred's
 * replies arrive on a poll loop (Android stand-in for iOS's Realtime subscription).
 *
 * There's no soft-delete column on `dr_fred` (unlike peer_messages), so messages
 * aren't deletable here. On load / new replies we bump the local seen-marker so the
 * support-tab badge doesn't resurface already-seen messages, matching iOS.
 */
@Composable
fun DrFredChat(userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Source of truth: messages keyed by id (negative ids are optimistic).
    val byId = remember { mutableStateMapOf<Long, DrFredMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // True from when a message is sent until Dr. Fred's reply lands — drives the
    // "thinking" typing bubble (his first reply can take a few seconds to generate).
    var awaitingReply by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun bumpMarker() {
        val newest = byId.values.filter { !it.isOptimistic }.maxByOrNull { it.id } ?: return
        var changed = false
        if (newest.id > userInfo.getCachedInt(LAST_SEEN_ID_KEY)) {
            userInfo.setCacheInt(LAST_SEEN_ID_KEY, newest.id.toInt()); changed = true
        }
        userInfo.setCacheString(LAST_PREVIEW_KEY, newest.text)
        // Cache the user-message count for the Dr. Fred achievement.
        userInfo.setCacheInt(CONVERSATION_COUNT_KEY, byId.values.count { it.isUser && !it.isOptimistic })
        if (changed) scope.launch { Clear30Store.save(userInfo) }
    }

    fun merge(incoming: List<DrFredMessage>) {
        val dropOptimistic = mutableListOf<Long>()
        incoming.forEach { m ->
            byId[m.id] = m
            // A confirmed user message supersedes its optimistic placeholder.
            if (m.isUser && !m.isOptimistic) {
                byId.values.filter { it.isOptimistic && it.isUser && it.text == m.text }
                    .forEach { dropOptimistic.add(it.id) }
            }
        }
        dropOptimistic.forEach { byId.remove(it) }
        // Stop "thinking" once Dr. Fred's reply (a confirmed inbound message) lands
        // at or after the user's latest message.
        val newestFred = byId.values.filter { !it.isOptimistic && !it.isUser }.maxByOrNull { it.orderDate }
        val newestUser = byId.values.filter { it.isUser }.maxByOrNull { it.orderDate }
        if (newestFred != null && (newestUser == null || newestFred.orderDate >= newestUser.orderDate)) {
            awaitingReply = false
        }
        bumpMarker()
    }

    // Initial load.
    LaunchedEffect(Unit) {
        SupabaseController.getDrFredMessages(count = 20)
            .onSuccess { merge(it) }
            .onFailure { error = "Could not load messages. ${it.message ?: ""}".trim() }
        loading = false
    }

    // Poll for Dr. Fred's replies while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DR_FRED_POLL_INTERVAL_MS)
            SupabaseController.getDrFredMessages(count = 20).onSuccess { merge(it) }
        }
    }

    val ordered = byId.values.sortedBy { it.orderDate }

    // Keep the newest message (or the thinking bubble) in view.
    LaunchedEffect(ordered.size, awaitingReply) {
        val rows = displayRowCount(ordered) + if (awaitingReply) 1 else 0
        if (rows > 0) listState.animateScrollToItem(maxOf(0, rows - 1))
    }

    // Safety: never leave the thinking bubble spinning forever if a reply is lost.
    LaunchedEffect(awaitingReply) {
        if (awaitingReply) { delay(90_000); awaitingReply = false }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        awaitingReply = true
        val optimistic = DrFredMessage(
            id = -now().toEpochMilliseconds(),
            userId = userInfo.userID,
            text = text,
            outbound = false,
            createdAt = now().toString(),
        )
        byId[optimistic.id] = optimistic
        scope.launch {
            val err = SupabaseController.sendDrFredMessage(text)
            if (err != null) {
                byId.remove(optimistic.id)
                awaitingReply = false
                error = "Could not send message. ${err.message}".trim()
            } else {
                Logger.logEvent(userInfo.loggingID, LogEventType.sentDrFredMessage)
                delay(1_200) // give the server-inserted row time to land, then reconcile
                SupabaseController.getDrFredMessages(count = 10).onSuccess { merge(it) }
            }
            sending = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            IconButton("chevron.backward", onClick = onBack)
            FredAvatar(diameter = 32.dp)
            Heading3("Dr. Fred")
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIcon() }
            error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallText(error!!, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(Dimens.cardSpacing))
            }
            ordered.isEmpty() -> Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = Dimens.headingTopPadding),
            ) { FredProfileContent() }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                forEachDay(ordered) { dayLabel, message ->
                    if (dayLabel != null) {
                        item { DateSeparator(dayLabel) }
                    } else if (message != null) {
                        item(key = message.id) { Bubble(message) }
                    }
                }
                if (awaitingReply) item("fred-typing") { FredTypingBubble() }
            }
        }

        // Compose bar
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), enabled = !sending)
            IconButton("arrow.right") { send() }
        }
    }
}

/** The Dr. Fred gradient, matching iOS / the support-tab row. */
private val fredGradient: Brush get() = Clear30Gradients.fred

/** Dr. Fred avatar — his photo clipped to a disc. */
@Composable
private fun FredAvatar(diameter: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Image(
        painter = painterResource(org.clear30.R.drawable.fred),
        contentDescription = "Dr. Fred",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(diameter).clip(CircleShape).background(fredGradient),
    )
}

/** Dr. Fred bio — shown as the empty-state placeholder. */
@Composable
private fun FredProfileContent() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            FredAvatar(diameter = 72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Heading3("Dr. Fred")
                TinyText("Addiction specialist", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
        SmallText(FRED_BIO)
    }
}

/** Centered day label between message groups (iOS `ChatDateSeparatorView`). */
@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2), contentAlignment = Alignment.Center) {
        TinyText(label, color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1)
    }
}

/** Three pulsing dots in an assistant-styled bubble — Dr. Fred is "thinking". */
@Composable
private fun FredTypingBubble() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "fredTyping")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.clip(RoundedCornerShape(Dimens.cornerRadius))
                .background(Clear30Colors.opacityGrayFlattened)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.9f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        initialStartOffset = androidx.compose.animation.core.StartOffset(i * 150),
                    ),
                    label = "fredDot$i",
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(Clear30Colors.text.copy(alpha = alpha)))
            }
        }
    }
}

/** A single message bubble (Dr. Fred messages aren't deletable — no soft-delete column). */
@Composable
private fun Bubble(message: DrFredMessage) {
    val isUser = message.isUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(Dimens.cornerRadius))
                .then(if (isUser) Modifier.background(fredGradient) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SmallText(message.text, color = if (isUser) Color.White else Clear30Colors.text)
        }
    }
}

// MARK: - Day grouping helpers (specialized for DrFredMessage; mirrors PeerSupportChat)

/** Number of rendered rows (a date header per day + one row per message). */
private fun displayRowCount(ordered: List<DrFredMessage>): Int {
    var days = 0
    var last: Instant? = null
    ordered.forEach { m ->
        val day = m.orderDate.justDay
        if (last == null || !day.isSameDay(last!!)) { days++; last = day }
    }
    return days + ordered.size
}

/**
 * Walks [ordered] (oldest→newest) emitting a day label before the first message of
 * each calendar day, then each message.
 */
private inline fun forEachDay(ordered: List<DrFredMessage>, emit: (dayLabel: String?, message: DrFredMessage?) -> Unit) {
    var last: Instant? = null
    ordered.forEach { m ->
        val day = m.orderDate.justDay
        if (last == null || !day.isSameDay(last!!)) {
            emit(drFredDateLabel(day), null)
            last = day
        }
        emit(null, m)
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today" / "Yesterday" / "Jun 16" (with year when not the current year). */
private fun drFredDateLabel(day: Instant): String {
    if (day.isToday) return "Today"
    if (day.adding(days = 1).isToday) return "Yesterday"
    val date = day.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val base = "${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
    val thisYear = now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year
    return if (date.year == thisYear) base else "$base, ${date.year}"
}
