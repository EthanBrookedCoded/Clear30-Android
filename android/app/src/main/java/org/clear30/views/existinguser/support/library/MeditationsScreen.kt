package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Meditations library — ported from iOS AllMeditationsView: content filtered by
 * program break through the [LibraryFilterSheet], stage-sectioned, and laid out as
 * a **2-column grid** of `MeditationCard`s (play disc + visited node + name,
 * Cards.swift:134-199). Tapping a card opens the full-screen MeditationPage;
 * playback marks the meditation `visited` (shown by the node in the card corner).
 */
@Composable
fun MeditationsScreen(program: Program, onBack: () -> Unit) {
    // Rev counter — meditation.visited is mutated in place by the player, so the
    // grid must recompute when the page closes (see memory: revision counters).
    var rev by remember { mutableIntStateOf(0) }
    // Break tabs → stage sections of meditation MESSAGES (iOS MeditationCard takes
    // the whole dayContent message for its emoji + meditation).
    val tabs = remember(program.contentInfo.size, rev) {
        buildLibraryTabs(program) { msgs ->
            msgs.filter { it.meditation != null }.distinctBy { it.meditation?.url }
        }
    }
    val tabNames = tabs.map { it.name }
    val defaultIndex = remember(tabNames) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabNames) { mutableStateOf(defaultIndex) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<ProgramMessage?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Meditations")
            Spacer(Modifier.weight(1f))
            // Break filter (iOS AllMeditationsView.swift:47-54).
            if (tabs.size > 1) {
                LibraryFilterButton { showFilterSheet = true }
            }
        }

        if (showFilterSheet) {
            LibraryFilterSheet(
                options = tabs.map { LibraryFilterOption(it.name, it.dateText) },
                selected = selected,
                onSelect = { selected = it },
                onDismiss = { showFilterSheet = false },
            )
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Meditations will appear here as your content unlocks.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        } else {
            val tab = tabs.getOrNull(selected) ?: tabs.first()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                // "More meditations in N days" (iOS AllMeditationsView.swift:62).
                MoreContentBanner(tab.nextUnlockDate, "meditations")
                tab.sections.forEach { section ->
                    val gradient = section.stage?.gradient ?: Clear30Gradients.meditation
                    StageHeaderCard(section.stage, Clear30Gradients.meditation)
                    // 2-column grid (iOS GridView(columns: 2)).
                    section.items.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                        ) {
                            pair.forEach { msg ->
                                MeditationCard(msg, gradient, Modifier.weight(1f)) { playing = msg }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.cardSpacing))
            }
        }

        playing?.let { msg ->
            msg.meditation?.let { med ->
                MeditationPage(meditation = med, program = program, onDismiss = { playing = null; rev++ })
            }
        }
    }
}

/**
 * iOS `MeditationCard` (Cards.swift:134-199): gradient play disc top-left, the
 * visited node top-right, and "emoji meditation-name" pinned to the bottom.
 */
@Composable
private fun MeditationCard(msg: ProgramMessage, gradient: Brush, modifier: Modifier, onTap: () -> Unit) {
    val med = msg.meditation ?: return
    Clear30Card(modifier = modifier.fillMaxHeight().pressScale(onClick = onTap)) {
        Column(Modifier.fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(gradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol("play.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                VisitedNode(visited = med.visited, gradient = gradient)
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
            Spacer(Modifier.weight(1f))
            SmallText("${msg.topicEmoji ?: "🧘"} ${med.name}".trim())
        }
    }
}

/**
 * iOS `VisitedNode` (AllMessagesView.swift:337-359, onButton variant): a rounded
 * square outline that gains a gradient-filled inner square once visited.
 */
@Composable
private fun VisitedNode(visited: Boolean, gradient: Brush) {
    Box(
        Modifier
            .size(23.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Clear30Colors.button)
            .border(3.dp, Clear30Colors.text.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (visited) {
            Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(gradient))
        }
    }
}
