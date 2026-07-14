package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMeditation
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * MeditationPage — THE standardized meditation player (port of iOS
 * `MeditationPage.swift`), presented as one full-screen sheet from every entry
 * point (library list, cravings/sleep resources, support hub rail) and inline
 * in feed cards via [MeditationPageInline]. Layout mirrors iOS: centered
 * title, a 100dp gradient play/pause disc, and a seekable scrubber with
 * elapsed/total times (max width 250dp in sheet mode).
 *
 * Like iOS, nothing plays until the user taps play; the first actual play
 * flips the meditation's `visited` flag and persists the program.
 */
@Composable
fun MeditationPage(meditation: ProgramMeditation, program: Program, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Clear30Colors.background) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.cardSpacing / 2),
                ) {
                    IconButton("xmark", onClick = onDismiss)
                }
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MeditationPlayerCore(meditation, program, centered = true)
                }
            }
        }
    }
}

/** The inline (feed-card) variant — same player, left-aligned, full-width scrubber. */
@Composable
internal fun MeditationPageInline(meditation: ProgramMeditation, program: Program) {
    // iOS MeditationPage(inlineType: .horizontal): centered on the PLAIN feed
    // card — gradient play disc + gradient full-width bar (T11).
    MeditationPlayerCore(meditation, program, centered = true, inline = true)
}

/** Shared title + play disc + scrubber over one ExoPlayer instance. */
@Composable
private fun MeditationPlayerCore(
    meditation: ProgramMeditation,
    program: Program,
    centered: Boolean,
    inline: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val onGradient = false // both the sheet and the inline card are plain-backed (iOS)
    val player = remember(meditation.url) {
        ExoPlayer.Builder(context).build().apply {
            // Process-wide audio attributes baseline (iOS AVAudioSession
            // .playback analog). handleAudioFocus = true so the meditation
            // pauses on incoming call / nav prompt instead of being talked over.
            setAudioAttributes(org.clear30.data.AudioBaseline.attributes, /* handleAudioFocus = */ true)
            setMediaItem(MediaItem.fromUri(meditation.url.trim()))
            prepare()
            // iOS starts paused — playback begins on the first play tap.
            playWhenReady = false
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var everPlayed by remember { mutableStateOf(false) }

    DisposableEffect(meditation.url) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) { player.pause(); player.seekTo(0); positionMs = 0 }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Mark visited + persist on first actual play (iOS playPauseAction).
    LaunchedEffect(isPlaying) {
        if (isPlaying && !everPlayed) {
            everPlayed = true
            if (!meditation.visited) {
                // ProgramMeditation is a value type — flip the flag on the
                // owning message's instance so the change persists.
                program.contentInfo.values
                    .flatMap { it.messages }
                    .firstOrNull { it.meditation?.url == meditation.url }
                    ?.meditation?.visited = true
                meditation.visited = true
                scope.launch { Clear30Store.save(program) }
            }
        }
    }

    // 500ms position poll while playing — ExoPlayer doesn't push updates.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            delay(500L)
        }
    }

    val textColor = if (onGradient) Color.White else Clear30Colors.text
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2),
    ) {
        Heading3(meditation.name, color = textColor)
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .then(
                    if (onGradient) {
                        Modifier.background(Color.White.copy(alpha = 0.25f))
                    } else {
                        Modifier.background(Clear30Gradients.meditation)
                    },
                )
                .pressScale {
                    Haptics.mediumImpact()
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                sfSymbol(if (isPlaying) "pause" else "play.fill"),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        MeditationScrubber(
            positionMs = positionMs,
            durationMs = durationMs,
            textColor = textColor,
            fill = if (onGradient) null else Clear30Gradients.meditation,
            // iOS `.frame(maxWidth: inline ? nil : 250)` — the feed card's bar
            // spans the card; the full-screen sheet caps at 250.
            modifier = if (inline) Modifier.fillMaxWidth() else Modifier.widthIn(max = 250.dp),
            onSeek = { fraction ->
                if (durationMs > 0) {
                    val target = (durationMs * fraction).toLong()
                    player.seekTo(target)
                    positionMs = target
                }
            },
        )
    }
}

/** Seekable progress track with mm:ss labels (iOS `MediaProgressBar`). */
@Composable
private fun MeditationScrubber(
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    fill: androidx.compose.ui.graphics.Brush?,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        TinyText(formatMeditationTime(positionMs), color = textColor)
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (fill != null) Clear30Colors.opacityGray else Color.White.copy(alpha = 0.25f)),
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(99.dp))
                    .let { m -> if (fill != null) m.background(fill) else m.background(Color.White) },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(widthPx, durationMs) {
                        detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(widthPx, durationMs) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                        }
                    },
            )
        }
        TinyText(formatMeditationTime(durationMs), color = textColor.copy(alpha = 0.5f))
    }
}

private fun formatMeditationTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}
