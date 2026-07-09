package org.clear30.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
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
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            items(messages) { msg -> Bubble(msg, userBubble) }
            if (sending) item { TypingBubble() }
        }

        // Composer — iOS ChatComposeMessageView: off-white filled input + a
        // 30pt gradient send disc that only appears once there's text.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            MultiLineOffWhiteInput(input, { input = it }, placeholder = "Message", modifier = Modifier.weight(1f), smallText = true)
            if (input.isNotBlank()) {
                Box(
                    Modifier.size(30.dp).pressScale {
                        val text = input.trim()
                        if (text.isEmpty() || sending) return@pressScale
                        messages.add(ClaireMessage(text = text, isUser = true))
                        input = ""; sending = true
                        scope.launch {
                            val history = messages.map { SupabaseClaireMessage(if (it.isUser) "user" else "assistant", it.text) }
                            val reply = send(history)
                            messages.add(ClaireMessage(text = reply ?: "Sorry, I couldn't respond just now.", isUser = false))
                            sending = false
                        }
                    }.clip(CircleShape).background(userBubble),
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
}

/** Animated "…typing" bubble shown on the assistant side while awaiting a reply. */
@Composable
private fun TypingBubble() {
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

@Composable
private fun Bubble(msg: ClaireMessage, userBubble: Brush) {
    // iOS BubbleStyle (Cards.swift:936-946): vertical padding cardSpacing * 0.8,
    // horizontal padding cardSpacing; bubbles cap at ~80% of the screen width.
    val maxBubbleWidth = LocalConfiguration.current.screenWidthDp.dp * 0.8f
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = maxBubbleWidth).clip(RoundedCornerShape(Dimens.cornerRadius))
                .then(if (msg.isUser) Modifier.background(userBubble) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing * 0.8f),
        ) {
            SmallText(msg.text, color = if (msg.isUser) Color.White else Clear30Colors.text)
        }
    }
}
