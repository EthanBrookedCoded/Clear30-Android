package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
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
import org.clear30.views.components.sfSymbol
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
    // The opened day GROUP (iOS navigates with the whole [ProgramMessage] group).
    var open by remember { mutableStateOf<List<ProgramMessage>?>(null) }
    var favoritesMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent)
    }

    open?.let { group ->
        // System back must ALSO bump rev — the lists are remember(rev)-keyed
        // over in-place-mutated models, so skipping it left a just-unfavorited
        // message sitting in the Favorites list.
        val close: () -> Unit = { open = null; rev++ }
        androidx.activity.compose.BackHandler { close() }
        MessageDetail(program, group, userInfo, journalEntries, onBack = close)
        return
    }

    val tabs = remember(program.contentInfo.size, rev) { buildLibraryTabs(program) { it } }
    // Key the selection on the tab NAMES, not the list instance — rev bumps
    // rebuild `tabs` (same names) on every viewer close, and an instance key
    // bounced the user back to the default tab each time.
    val tabNames = tabs.map { it.name }
    val defaultIndex = remember(tabNames) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabNames) { mutableStateOf(defaultIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton(if (favoritesMode) "xmark" else "chevron.backward", onClick = {
                if (favoritesMode) favoritesMode = false else onBack()
            })
            Heading1(if (favoritesMode) "Favorites" else "Messages")
            Spacer(Modifier.weight(1f))
            // Favorites heart (iOS AllMessagesView header, W18).
            Icon(
                sfSymbol("heart.fill"),
                contentDescription = "Favorites",
                tint = if (favoritesMode) Clear30Colors.red2 else Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp).clickable { favoritesMode = !favoritesMode },
            )
        }

        if (favoritesMode) {
            val favorites = remember(rev) {
                program.contentInfo.entries.sortedByDescending { it.key }
                    .flatMap { it.value.messages }.unlocked.filter { it.favorited }
            }
            if (favorites.isEmpty()) {
                Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                    SmallText(
                        "Favorite a message with the heart inside it and it'll appear here.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    favorites.forEach { msg -> MessageRow(msg, program) { open = listOf(msg) } }
                }
            }
            return
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
                // Group same-day messages (iOS AllMessagesView: one card per DAY;
                // a second message that day — the assessment-response message —
                // becomes a badge on the card and its content follows the main
                // message inside the viewer).
                val dayGroups = section.items.groupBy { org.clear30.data.model.PlainDate.from(it.unlockOn) }.values
                dayGroups.forEach { group ->
                    val primary = group.first()
                    val sub = group.getOrNull(1)
                    MessageRow(
                        msg = primary,
                        program = program,
                        badge = sub?.let { "${it.topicEmoji ?: "💬"} ${it.topicTitle}" },
                        onOpen = { open = group },
                    )
                }
            }
        }
    }
}

/** iOS `MessageCard` (AllMessagesView.swift:361-521): "emoji title" left, and a
 *  Day-N pill right — gradient-filled + checkmark once the day's feed was
 *  completed, outlined + arrow otherwise. */
@Composable
private fun MessageRow(
    msg: ProgramMessage,
    program: Program,
    badge: String? = null,
    onOpen: () -> Unit,
) {
    val day = program.getBreak(msg.unlockOn)?.let { "Day ${it.getBreakDay(msg.unlockOn)}" }
        ?: "${msg.unlockOn.let { org.clear30.data.model.PlainDate.from(it) }.month}/${org.clear30.data.model.PlainDate.from(msg.unlockOn).day}"
    val progress = program.contentInfo[org.clear30.data.model.PlainDate.from(msg.unlockOn)]?.progress ?: 0.0
    val complete = progress >= 1.0
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                SmallText("${msg.topicEmoji ?: "💬"} ${msg.topicTitle}")
                // Same-day sub-message pill (iOS MessageCard badge).
                badge?.let {
                    Row(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                            .background(Clear30Colors.opacityGray)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        TinyText(it, color = Clear30Colors.text.copy(alpha = 0.75f))
                    }
                }
            }
            Row(
                Modifier
                    .padding(start = Dimens.cardSpacing / 2)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                    .then(
                        if (complete) {
                            Modifier.background(Clear30Gradients.clear30)
                        } else {
                            Modifier.border(
                                2.dp,
                                Clear30Colors.text.copy(alpha = 0.25f),
                                androidx.compose.foundation.shape.RoundedCornerShape(99.dp),
                            )
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText(day, color = if (complete) androidx.compose.ui.graphics.Color.White else Clear30Colors.text)
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                Icon(
                    sfSymbol(if (complete) "checkmark" else "arrow.right"),
                    contentDescription = null,
                    tint = if (complete) androidx.compose.ui.graphics.Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp),
                )
            }
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
