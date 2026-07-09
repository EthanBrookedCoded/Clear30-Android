package org.clear30.views.existinguser.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * TodayFeed — ported in spirit from TodayFeedView (Home.swift). Renders the
 * unlocked program-content messages (newest first) as cards reading directly
 * from [Program.contentInfo]. Populates once the message-sync handler lands;
 * shows an empty state meanwhile.
 */
@Composable
fun TodayFeed(program: Program, userInfo: UserInfo, modifier: Modifier = Modifier) {
    val messages: List<ProgramMessage> = program.contentInfo.values
        .flatMap { it.messages }
        .unlocked
        .reversed()

    // Tapping a card opens a full-bleed MessageDetail; system back closes it.
    var open by remember { mutableStateOf<ProgramMessage?>(null) }
    open?.let { msg ->
        androidx.activity.compose.BackHandler { open = null }
        MessageDetail(program, msg, userInfo, onBack = { open = null })
        return
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        if (messages.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText("Your daily content will appear here.", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        } else {
            messages.forEach { msg -> MessageCard(msg) { open = msg } }
        }
    }
}

@Composable
private fun MessageCard(message: ProgramMessage, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                message.topicEmoji?.let { SmallText(it) }
                Heading3(message.topicTitle)
            }
            if (message.subtitle.isNotBlank()) TinyText(message.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}
