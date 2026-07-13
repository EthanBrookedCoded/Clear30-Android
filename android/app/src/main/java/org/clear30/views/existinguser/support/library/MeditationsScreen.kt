package org.clear30.views.existinguser.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Meditations library — ported from Support/Library/Meditation. Lists every
 * `ProgramMeditation` referenced by the user's content (across all days), and
 * lets the user play any of them via a Media3 ExoPlayer-backed mini player.
 *
 * Tapping a meditation marks it as `visited = true` on the underlying message's
 * meditation (matching iOS), so the home content feed and the timeline can show
 * progress. Persistence runs on each toggle so a relaunch keeps the state.
 */
@Composable
fun MeditationsScreen(program: Program, onBack: () -> Unit) {
    // Break tabs → stage sections of meditations (iOS AllMeditationsView), same
    // structure as the Reddit/YouTube libraries.
    val tabs = remember(program.contentInfo.size) {
        buildLibraryTabs(program) { msgs -> msgs.mapNotNull { it.meditation }.distinctBy { it.url } }
    }
    val defaultIndex = remember(tabs) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabs) { mutableStateOf(defaultIndex) }
    var playing by remember { mutableStateOf<ProgramMeditation?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Meditations")
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Meditations will appear here as your content unlocks.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        } else {
            if (tabs.size > 1) {
                LibraryTabRow(tabs.map { it.name }, selected, Clear30Gradients.meditation) { selected = it }
            }
            val tab = tabs.getOrNull(selected) ?: tabs.first()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                tab.sections.forEach { section ->
                    StageHeaderCard(section.stage, Clear30Gradients.meditation)
                    section.items.forEach { med ->
                        MeditationRow(med, isPlaying = playing?.url == med.url) {
                            playing = if (playing?.url == med.url) null else med
                        }
                    }
                }
            }
        }

        playing?.let { med ->
            MeditationPage(meditation = med, program = program, onDismiss = { playing = null })
        }
    }
}

@Composable
internal fun MeditationRow(meditation: ProgramMeditation, isPlaying: Boolean, onTap: () -> Unit) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
        gradient = if (isPlaying) Clear30Gradients.meditation else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            androidx.compose.material3.Icon(
                sfSymbol(if (isPlaying) "pause.fill" else "play.fill"),
                contentDescription = null,
                tint = if (isPlaying) Color.White else Clear30Colors.meditation1,
                modifier = Modifier.padding(start = 4.dp),
            )
            SmallText(meditation.name, color = if (isPlaying) Color.White else Clear30Colors.text)
            Spacer(Modifier.weight(1f))
            if (meditation.visited) TinyText("✓", color = if (isPlaying) Color.White else Clear30Colors.gray)
        }
    }
}

// The mini bottom player was replaced by the standardized full-screen
// MeditationPage sheet (MeditationPage.kt), matching iOS.
