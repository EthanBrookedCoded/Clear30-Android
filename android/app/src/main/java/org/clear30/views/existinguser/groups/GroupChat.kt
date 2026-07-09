package org.clear30.views.existinguser.groups

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupChatMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.deleteGroupChatMessage
import org.clear30.data.supabase.getGroupChatMessages
import org.clear30.data.supabase.sendGroupChatMessage
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.LoadingIcon
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/** How often the group chat re-fetches to pick up others' messages. */
private const val GROUP_CHAT_POLL_INTERVAL_MS = 5_000L

/**
 * GroupChat — the group's shared message thread, backed by `groups.group_messages`.
 * Multi-author (unlike Dr Fred / peer): each non-own message is labelled with the
 * sender's emoji + name (resolved from [group] members). The user sends optimistically
 * and can long-press their own messages to soft-delete; others' messages arrive on a
 * poll loop (Android stand-in for iOS Realtime).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChat(group: Clear30Group, userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val byId = remember { mutableStateMapOf<String, Clear30GroupChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // memberID → "emoji  Name" for author labels.
    val memberLabels = remember(group.members) {
        group.members.associate { it.memberID to "${it.emoji}  ${it.name}".trim() }
    }

    fun merge(incoming: List<Clear30GroupChatMessage>) {
        val dropOptimistic = mutableListOf<String>()
        incoming.forEach { m ->
            if (m.isDeleted) {
                byId.remove(m.id)
            } else {
                byId[m.id] = m
                if (m.userId == userInfo.userID) {
                    byId.values.filter { it.id.startsWith("local-") && it.userId == userInfo.userID && it.message == m.message }
                        .forEach { dropOptimistic.add(it.id) }
                }
            }
        }
        dropOptimistic.forEach { byId.remove(it) }
    }

    LaunchedEffect(group.id) {
        SupabaseController.getGroupChatMessages(group.id, count = 30)
            .onSuccess { merge(it) }
            .onFailure { error = "Could not load messages. ${it.message ?: ""}".trim() }
        loading = false
    }
    LaunchedEffect(group.id) {
        while (true) {
            delay(GROUP_CHAT_POLL_INTERVAL_MS)
            SupabaseController.getGroupChatMessages(group.id, count = 30).onSuccess { merge(it) }
        }
    }

    val ordered = byId.values.filter { !it.isDeleted }.sortedBy { it.orderDate }

    LaunchedEffect(ordered.size) {
        if (ordered.isNotEmpty()) listState.animateScrollToItem(ordered.size - 1)
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
        byId[optimistic.id] = optimistic
        scope.launch {
            val err = SupabaseController.sendGroupChatMessage(group.id, userInfo.userID, text)
            if (err != null) {
                byId.remove(optimistic.id)
                error = "Could not send. ${err.message}".trim()
            } else {
                delay(1_200)
                SupabaseController.getGroupChatMessages(group.id, count = 15).onSuccess { merge(it) }
            }
            sending = false
        }
    }

    fun delete(message: Clear30GroupChatMessage) {
        if (message.userId != userInfo.userID) return
        byId.remove(message.id)
        if (message.id.startsWith("local-")) return
        scope.launch {
            val err = SupabaseController.deleteGroupChatMessage(message.id)
            if (err != null) {
                byId[message.id] = message
                error = "Could not delete. ${err.message}".trim()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading3(group.name ?: "Group chat")
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIcon() }
            error != null && ordered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallText(error!!, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(Dimens.cardSpacing))
            }
            ordered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallText("Say hi 👋 — start the conversation.", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                items(ordered, key = { it.id }) { m ->
                    Bubble(
                        message = m,
                        isUser = m.userId == userInfo.userID,
                        authorLabel = memberLabels[m.userId],
                        gradient = group.gradient,
                        onDelete = { delete(m) },
                    )
                }
            }
        }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: Clear30GroupChatMessage,
    isUser: Boolean,
    authorLabel: String?,
    gradient: androidx.compose.ui.graphics.Brush,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && authorLabel != null) {
            TinyText(authorLabel, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
        }
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(Dimens.cornerRadius))
                .then(if (isUser) Modifier.background(gradient) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                .then(if (isUser) Modifier.combinedClickable(onClick = {}, onLongClick = onDelete) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SmallText(message.message, color = if (isUser) Color.White else Clear30Colors.text)
        }
    }
}

/** Parse the group-message timestamp for ordering; unparseable sorts earliest. */
private val Clear30GroupChatMessage.orderDate: Instant
    get() = timestamp.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.DISTANT_PAST
