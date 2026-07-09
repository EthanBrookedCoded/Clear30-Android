package org.clear30.views.existinguser.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.existinguser.today.MessageDetail
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * MessagesLibraryScreen — ported from the iOS AllMessagesView. The unlocked program
 * messages, filtered by program break (the **tabs**) and **sectioned by stage**, the
 * same treatment as the Reddit/YouTube/Meditations libraries. Tapping a row opens the
 * MessageDetail view; the star toggles `favorited` and persists locally.
 */
@Composable
fun MessagesLibraryScreen(
    program: Program,
    userInfo: UserInfo,
    journalEntries: org.clear30.data.model.JournalEntries? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Rev counter forces the tabs to recompute after a star toggle.
    var rev by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<ProgramMessage?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent)
    }

    open?.let { msg ->
        androidx.activity.compose.BackHandler { open = null }
        MessageDetail(program, msg, userInfo, journalEntries, onBack = { open = null; rev++ })
        return
    }

    val tabs = remember(program.contentInfo.size, rev) { buildLibraryTabs(program) { it } }
    val defaultIndex = remember(tabs) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabs) { mutableStateOf(defaultIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Messages")
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Your daily content will appear here as you unlock it.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return
        }

        if (tabs.size > 1) {
            LibraryTabRow(tabs.map { it.name }, selected, Clear30Gradients.clear30) { selected = it }
        }
        val tab = tabs.getOrNull(selected) ?: tabs.first()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            tab.sections.forEach { section ->
                StageHeaderCard(section.stage, Clear30Gradients.clear30)
                section.items.forEach { msg ->
                    MessageRow(
                        msg = msg,
                        onOpen = { open = msg },
                        onToggleFavorite = {
                            msg.favorited = !msg.favorited
                            scope.launch { Clear30Store.save(program) }
                            rev++
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    msg: ProgramMessage,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            msg.topicEmoji?.let { SmallText(it) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Heading3(msg.topicTitle)
                if (msg.subtitle.isNotBlank()) {
                    TinyText(msg.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                if (!msg.visited) {
                    TinyText("New", color = Clear30Colors.green)
                }
            }
            // Standalone star button (separate clickable from the row open).
            Icon(
                if (msg.favorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (msg.favorited) "Unfavorite" else "Favorite",
                tint = if (msg.favorited) Clear30Colors.green else Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onToggleFavorite).padding(8.dp),
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.clickable(onClick = onClick),
        gradient = if (selected) org.clear30.views.theme.Clear30Gradients.clear30 else null,
    ) {
        SmallText(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color.White else Clear30Colors.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
