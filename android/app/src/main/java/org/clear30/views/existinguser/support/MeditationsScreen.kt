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
    // Source meditations from the message graph — same as the iOS view.
    val meditations = remember(program.contentInfo.size) {
        program.contentInfo.values
            .flatMap { it.messages }
            .mapNotNull { it.meditation }
            .distinctBy { it.url }
    }
    var playing by remember { mutableStateOf<ProgramMeditation?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Meditations")
        }

        if (meditations.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Meditations will appear here as your content unlocks.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(top = Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                items(meditations, key = { it.url }) { med ->
                    MeditationRow(med, isPlaying = playing?.url == med.url) {
                        playing = if (playing?.url == med.url) null else med
                    }
                }
            }
        }

        playing?.let { med ->
            MeditationPlayer(meditation = med, program = program, onClose = { playing = null })
        }
    }
}

@Composable
private fun MeditationRow(meditation: ProgramMeditation, isPlaying: Boolean, onTap: () -> Unit) {
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

/**
 * Mini audio player — wraps a Media3 ExoPlayer. Lifecycle:
 *  - constructed on entry, prepared with the meditation URL, auto-plays
 *  - polls position every 500ms for the progress bar
 *  - on the first time the user actually plays, flips `meditation.visited`
 *    and persists the program (matching iOS' visited tracking)
 *  - released when the composable leaves (DisposableEffect)
 */
@Composable
private fun MeditationPlayer(
    meditation: ProgramMeditation,
    program: Program,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val player = remember(meditation.url) {
        ExoPlayer.Builder(context).build().apply {
            // Process-wide audio attributes baseline (iOS AVAudioSession
            // .playback / .mixWithOthers analog). handleAudioFocus = true so
            // the meditation pauses on incoming call / nav prompt instead of
            // being talked over.
            setAudioAttributes(org.clear30.data.AudioBaseline.attributes, /* handleAudioFocus = */ true)
            setMediaItem(MediaItem.fromUri(meditation.url))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableStateOf(0L) }

    DisposableEffect(meditation.url) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Mark visited on first actual play.
    LaunchedEffect(isPlaying) {
        if (isPlaying && !meditation.visited) {
            // Locate the meditation on its owning message and flip the flag —
            // ProgramMeditation is a value type so we have to mutate via the
            // parent message graph.
            val target = program.contentInfo.values
                .flatMap { it.messages }
                .firstOrNull { it.meditation?.url == meditation.url }
                ?.meditation
            target?.visited = true
            scope.launch { Clear30Store.save(program) }
        }
    }

    // 500ms progress poll while playing — ExoPlayer doesn't push position updates.
    LaunchedEffect(isPlaying, durationMs) {
        while (isPlaying && durationMs > 0L) {
            progress = (player.currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)
            delay(500L)
        }
    }

    Clear30Card(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
        gradient = Clear30Gradients.meditation,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallText(meditation.name, color = Color.White)
                Spacer(Modifier.weight(1f))
                IconButton("xmark", tint = Color.White, onClick = onClose)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                IconButton(if (isPlaying) "pause.fill" else "play.fill", tint = Color.White) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                TinyText(formatTime(player.currentPosition), color = Color.White)
                Spacer(Modifier.weight(1f))
                TinyText(formatTime(durationMs), color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
