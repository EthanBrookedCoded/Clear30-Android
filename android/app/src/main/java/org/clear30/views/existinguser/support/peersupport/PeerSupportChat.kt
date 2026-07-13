package org.clear30.views.existinguser.support

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.PeerMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.deletePeerMessage
import org.clear30.data.supabase.getPeerMessages
import org.clear30.data.supabase.markPeerMessagesRead
import org.clear30.data.supabase.sendPeerMessage
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
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** How often the chat re-fetches to pick up Gerad's replies (iOS uses Realtime). */
private const val PEER_POLL_INTERVAL_MS = 5_000L
private const val GERAD_BIO =
    "Hey, I'm Gerad.\nI was addicted to weed and was able to stop.\n\n" +
        "My passion is helping other people break out of the same loops I got stuck in, " +
        "whether that's quitting completely or building a relationship with weed that works " +
        "for you instead of against you.\n\nMessage me anytime, I'd love to hear from you :)"

/**
 * PeerSupportChat — ported from PeerSupportChat.swift. The Gerad ("accountability
 * buddy") thread: a two-way message list backed by `comms.peer_messages`. Messages
 * are grouped by day; the user can send (optimistically) and delete their own
 * messages; Gerad's replies arrive via a poll loop (the Android stand-in for iOS's
 * Supabase Realtime subscription).
 */
@Composable
fun PeerSupportChat(userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Source of truth: messages keyed by id (negative ids are optimistic).
    val byId = remember { mutableStateMapOf<Long, PeerMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun merge(incoming: List<PeerMessage>) {
        val dropOptimistic = mutableListOf<Long>()
        incoming.forEach { m ->
            if (m.deleted) {
                byId.remove(m.id)
            } else {
                byId[m.id] = m
                // A confirmed user message supersedes its optimistic placeholder.
                if (m.isUser && !m.isOptimistic) {
                    byId.values.filter { it.isOptimistic && it.isUser && it.text == m.text }
                        .forEach { dropOptimistic.add(it.id) }
                }
            }
        }
        dropOptimistic.forEach { byId.remove(it) }
    }

    // Initial load + mark read.
    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedPeerSupport)
        SupabaseController.getPeerMessages(count = 20)
            .onSuccess { merge(it) }
            .onFailure { error = "Could not load messages. ${it.message ?: ""}".trim() }
        loading = false
        SupabaseController.markPeerMessagesRead(userInfo.userID)
    }

    // Poll for Gerad's replies while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(PEER_POLL_INTERVAL_MS)
            SupabaseController.getPeerMessages(count = 20).onSuccess { merge(it) }
        }
    }

    val ordered = byId.values.filter { !it.deleted }.sortedBy { it.orderDate }

    // Keep the newest message in view.
    LaunchedEffect(ordered.size) {
        if (ordered.isNotEmpty()) listState.animateScrollToItem(maxOf(0, displayRowCount(ordered) - 1))
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        val nowIso = now().toString()
        val optimistic = PeerMessage(
            id = -now().toEpochMilliseconds(),
            userId = userInfo.userID,
            text = text,
            outbound = false,
            type = "manual",
            createdAt = nowIso,
            scheduledFor = nowIso,
            deleted = false,
        )
        byId[optimistic.id] = optimistic
        scope.launch {
            val err = SupabaseController.sendPeerMessage(text, userInfo.userID)
            if (err != null) {
                byId.remove(optimistic.id)
                error = "Could not send message. ${err.message}".trim()
            } else {
                Logger.logEvent(userInfo.loggingID, LogEventType.talkedToPeerSupport)
                delay(1_200) // give the row time to land, then reconcile (Swift fallback fetch)
                SupabaseController.getPeerMessages(count = 10).onSuccess { merge(it) }
            }
            sending = false
        }
    }

    fun delete(message: PeerMessage) {
        byId.remove(message.id)
        if (message.isOptimistic) return
        scope.launch {
            val err = SupabaseController.deletePeerMessage(message.id)
            if (err != null) {
                byId[message.id] = message // restore on failure
                error = "Could not delete message. ${err.message}".trim()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.headingTopPadding,
            ),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                IconButton("chevron.backward", onClick = onBack)
                GeradHeaderPill(showInfoIcon = ordered.isNotEmpty()) { showProfile = true }
            }

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIcon() }
                error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SmallText(error!!, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(Dimens.cardSpacing))
                }
                ordered.isEmpty() -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(vertical = Dimens.headingTopPadding),
                ) { GeradProfileContent() }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    forEachDay(ordered) { dayLabel, message ->
                        if (dayLabel != null) {
                            item { DateSeparator(dayLabel) }
                        } else if (message != null) {
                            item(key = message.id) { Bubble(message, onDelete = { delete(message) }) }
                        }
                    }
                }
            }

            // Compose bar — the shared Claire-style composer (off-white input +
            // gradient send disc), per-chat gradient.
            org.clear30.views.components.ChatComposer(
                input = input,
                onInputChange = { input = it },
                sendBrush = buddyGradient,
                modifier = Modifier.padding(top = Dimens.cardSpacing / 2),
            ) { send() }
        }

        if (showProfile) {
            Column(
                Modifier.fillMaxSize().background(Clear30Colors.background)
                    .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton("xmark", onClick = { showProfile = false })
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(top = Dimens.headingTopPadding),
                ) { GeradProfileContent() }
            }
        }
    }
}

/** The Gerad gradient (meditation1 → blue), matching iOS `buddyGradient`. */
private val buddyGradient: Brush get() = Clear30Gradients.buddy

/** Header pill — avatar + "Gerad" + info icon; tap opens the profile. */
@Composable
private fun GeradHeaderPill(showInfoIcon: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Clear30Colors.opacityGray)
            .pressScale { onClick() }
            .padding(start = Dimens.cardSpacing / 4, end = Dimens.cardSpacing, top = Dimens.cardSpacing / 4, bottom = Dimens.cardSpacing / 4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        GeradAvatar(diameter = 32.dp, iconSize = 16.dp)
        SmallText("Gerad", color = Clear30Colors.text)
        if (showInfoIcon) {
            Icon(sfSymbol("info.circle.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        }
    }
}

/** Gerad avatar — gradient disc + person glyph (placeholder until the real photo is imported). */
@Composable
private fun GeradAvatar(diameter: androidx.compose.ui.unit.Dp, @Suppress("UNUSED_PARAMETER") iconSize: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(org.clear30.R.drawable.gerad),
        contentDescription = "Gerad",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.size(diameter).clip(CircleShape).background(buddyGradient),
    )
}

/** Gerad bio — shown as the empty-state placeholder and behind the info icon. */
@Composable
private fun GeradProfileContent() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            GeradAvatar(diameter = 72.dp, iconSize = 34.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Heading3("Gerad")
                TinyText("Your accountability buddy", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
        SmallText(GERAD_BIO)
    }
}

/** Centered day label between message groups (iOS `ChatDateSeparatorView`). */
@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2), contentAlignment = Alignment.Center) {
        TinyText(label, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier, maxLines = 1)
    }
}

/** A single message bubble; long-press a user bubble to delete it. */
@Composable
private fun Bubble(message: PeerMessage, onDelete: () -> Unit) =
    org.clear30.views.components.ChatBubble(message.text, message.isUser, buddyGradient, onLongPressUser = onDelete)

// MARK: - Day grouping helpers

/** Number of rendered rows (a date header per day + one row per message). */
private fun displayRowCount(ordered: List<PeerMessage>): Int {
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
 * each calendar day, then each message. [emit] is called with either a non-null
 * label (a separator) or a non-null message (a bubble).
 */
private inline fun forEachDay(ordered: List<PeerMessage>, emit: (dayLabel: String?, message: PeerMessage?) -> Unit) {
    var last: Instant? = null
    ordered.forEach { m ->
        val day = m.orderDate.justDay
        if (last == null || !day.isSameDay(last!!)) {
            emit(peerDateLabel(day), null)
            last = day
        }
        emit(null, m)
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today" / "Yesterday" / "Jun 16" (with year when not the current year). */
private fun peerDateLabel(day: Instant): String {
    if (day.isToday) return "Today"
    if (day.adding(days = 1).isToday) return "Yesterday"
    val date = day.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val base = "${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
    val thisYear = now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year
    return if (date.year == thisYear) base else "$base, ${date.year}"
}
