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
import org.clear30.views.theme.Dimens

/**
 * MessagesLibraryScreen — ported from the iOS Messages Library. Lists every
 * unlocked program message in date order, with a "Favorites" filter chip and
 * a star-toggle on each row. Tapping a row opens the existing MessageDetail
 * full-screen view; the star toggles `favorited` and persists to local storage.
 */
private enum class LibraryFilter { ALL, FAVORITES, UNREAD }

@Composable
fun MessagesLibraryScreen(program: Program, userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    // Rev counter forces the list to recompute after a star toggle.
    var rev by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<ProgramMessage?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent)
    }

    open?.let { msg ->
        androidx.activity.compose.BackHandler { open = null }
        MessageDetail(program, msg, userInfo, onBack = { open = null; rev++ })
        return
    }

    @Suppress("UNUSED_EXPRESSION") rev
    val messages = program.contentInfo.values.flatMap { it.messages }.unlocked.reversed()
    val filtered = when (filter) {
        LibraryFilter.ALL -> messages
        LibraryFilter.FAVORITES -> messages.filter { it.favorited }
        LibraryFilter.UNREAD -> messages.filter { !it.visited }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Messages")
        }
        // Filter pill row.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            FilterPill("All", filter == LibraryFilter.ALL) { filter = LibraryFilter.ALL }
            FilterPill("Favorites", filter == LibraryFilter.FAVORITES) { filter = LibraryFilter.FAVORITES }
            FilterPill("Unread", filter == LibraryFilter.UNREAD) { filter = LibraryFilter.UNREAD }
        }

        if (filtered.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText(
                    when (filter) {
                        LibraryFilter.ALL -> "Your daily content will appear here as you unlock it."
                        LibraryFilter.FAVORITES -> "Tap the star on a message to favorite it."
                        LibraryFilter.UNREAD -> "All caught up — no unread messages."
                    },
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            items(filtered, key = { msg ->
                // unlockOn is stable across recompositions and unique per message
                // (the assessment seeds it from the program start date + a day
                // offset), so it's a safe key without touching the model.
                msg.unlockOn.toEpochMilliseconds()
            }) { msg ->
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
