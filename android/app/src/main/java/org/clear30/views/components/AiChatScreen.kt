package org.clear30.views.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
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
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf(ClaireMessage(text = greeting, isUser = false)) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(title)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) { items(messages) { msg -> Bubble(msg, userBubble) } }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), enabled = !sending)
            IconButton("arrow.right") {
                val text = input.trim()
                if (text.isEmpty() || sending) return@IconButton
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
}

@Composable
private fun Bubble(msg: ClaireMessage, userBubble: Brush) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(Dimens.cornerRadius))
                .then(if (msg.isUser) Modifier.background(userBubble) else Modifier.background(Clear30Colors.opacityGrayFlattened))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SmallText(msg.text, color = if (msg.isUser) Color.White else Clear30Colors.text)
        }
    }
}
