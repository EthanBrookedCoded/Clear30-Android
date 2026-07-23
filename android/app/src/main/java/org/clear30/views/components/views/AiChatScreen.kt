package org.clear30.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.model.ClaireMessage
import org.clear30.data.model.SupabaseClaireMessage
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * AiChatScreen — reusable chat UI (extracted from the Claire chat) so both
 * Claire and Dr Fred share it. Message bubbles + input; [send] posts the
 * conversation and returns the assistant reply.
 */
@Composable
fun AiChatScreen(
    title: String,
    greeting: String,
    userBubble: Brush,
    onBack: () -> Unit,
    send: suspend (List<SupabaseClaireMessage>) -> String?,
    avatarRes: Int? = null,
    initialInput: String = "",
    loadHistory: (suspend () -> List<ClaireMessage>)? = null,
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf(ClaireMessage(text = greeting, isUser = false)) }
    // Seeded prompts (Claire prompt cards / feeling emojis) pre-fill the input —
    // the user taps send, matching iOS ClaireTextMode.
    var input by remember { mutableStateOf(initialInput) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Load prior conversation (iOS AllClaire loads from claire.messages on open).
    // Replace the greeting with history only when there actually is some.
    LaunchedEffect(Unit) {
        val history = loadHistory?.invoke().orEmpty()
        if (history.isNotEmpty()) {
            messages.clear()
            messages.addAll(history)
        }
    }

    // Keep the newest bubble (and the typing indicator) in view.
    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // Bottom inset = `imePadding()` ONLY (W81 rule, applied to the bottom edge):
    // this renders inside AllTabs' Scaffold content, whose bottom padding is the
    // tab bar — and CustomTabBar already applies
    // `windowInsetsPadding(WindowInsets.navigationBars)` itself. A
    // `navigationBarsPadding()` here therefore counted the gesture bar a second
    // time and floated the composer ~a nav bar above the tab bar. Consuming the
    // navigation-bar inset (the host spent it) also keeps `imePadding()` honest:
    // with the keyboard up the composer rises by the keyboard height, not
    // keyboard + gesture bar. Inside Clear30FullScreenCover (Today → Claire) the
    // dialog's decor fits the system windows, so both resolve to 0 there.
    Column(
        Modifier.fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            if (avatarRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(avatarRes),
                    contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
            }
            Heading1(title)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // contentPadding (not outer padding) so bubble shadows aren't
            // clipped at the list's top/bottom edges.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            items(messages) { msg -> ChatBubble(msg.text, msg.isUser, userBubble) }
            if (sending) item { ChatTypingBubble() }
        }

        ChatComposer(input = input, onInputChange = { input = it }, sendBrush = userBubble) {
            val text = input.trim()
            if (text.isEmpty() || sending) return@ChatComposer
            messages.add(ClaireMessage(text = text, isUser = true))
            input = ""; sending = true
            scope.launch {
                val history = messages.map { SupabaseClaireMessage(if (it.isUser) "user" else "assistant", it.text) }
                val reply = send(history)
                messages.add(ClaireMessage(text = reply ?: "Sorry, I couldn't respond just now.", isUser = false))
                sending = false
            }
        }
    }
}

/**
 * ChatComposer — the shared composer (iOS `ChatComposeMessageView`): off-white
 * filled multi-line input + a 30dp gradient send disc that only appears once
 * there's text. Used by Claire, Dr. Fred, and Gerad so every chat reads the same.
 */
@Composable
fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    sendBrush: Brush,
    modifier: Modifier = Modifier,
    onSend: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        MultiLineOffWhiteInput(input, onInputChange, placeholder = "Message", modifier = Modifier.weight(1f), smallText = true)
        if (input.isNotBlank()) {
            Box(
                Modifier.size(30.dp).pressScale { onSend() }.clip(CircleShape).background(sendBrush),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    sfSymbol("arrow.up"),
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * ChatBubble — the shared bubble (iOS `BubbleStyle`, Cards.swift:936-946):
 * vertical padding cardSpacing × 0.8, horizontal cardSpacing, capped at ~80% of
 * the screen width; gradient for the user side, flat gray for the other side.
 * [onLongPressUser] enables long-press actions on the user's own bubbles
 * (e.g. delete in the Gerad chat).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(text: String, isUser: Boolean, userBubble: Brush, onLongPressUser: (() -> Unit)? = null) {
    val maxBubbleWidth = LocalConfiguration.current.screenWidthDp.dp * 0.8f
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = maxBubbleWidth).clip(RoundedCornerShape(Dimens.cornerRadius))
                .then(if (isUser) Modifier.background(userBubble) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                .then(
                    if (isUser && onLongPressUser != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onLongPressUser)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing * 0.8f),
        ) {
            SmallText(text, color = if (isUser) Color.White else Clear30Colors.text)
        }
    }
}

/** Animated "…typing" bubble shown on the non-user side while awaiting a reply. */
@Composable
fun ChatTypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
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
                    animationSpec = infiniteRepeatable(
                        animation = tween(450, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(i * 150),
                    ),
                    label = "dot$i",
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(Clear30Colors.text.copy(alpha = alpha)))
            }
        }
    }
}

