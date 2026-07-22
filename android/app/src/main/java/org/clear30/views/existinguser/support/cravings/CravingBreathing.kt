package org.clear30.views.existinguser.support

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

// MARK: - Cadence

enum class BreathingCadence(val title: String, val pattern: String) {
    CALM("Calm", "4-4-6"),
    RELAXING("Relaxing", "4-7-8"),
    BOX("Box", "4-4-4-4"),
    EVEN("Even", "5-5");
}

private enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT }

private fun BreathPhase.seconds(c: BreathingCadence): Double = when (this) {
    BreathPhase.INHALE -> when (c) { BreathingCadence.CALM -> 4.0; BreathingCadence.RELAXING -> 4.0; BreathingCadence.BOX -> 4.0; BreathingCadence.EVEN -> 5.0 }
    BreathPhase.HOLD_IN -> when (c) { BreathingCadence.CALM -> 4.0; BreathingCadence.RELAXING -> 7.0; BreathingCadence.BOX -> 4.0; BreathingCadence.EVEN -> 0.0 }
    BreathPhase.EXHALE -> when (c) { BreathingCadence.CALM -> 6.0; BreathingCadence.RELAXING -> 8.0; BreathingCadence.BOX -> 4.0; BreathingCadence.EVEN -> 5.0 }
    BreathPhase.HOLD_OUT -> when (c) { BreathingCadence.CALM -> 0.0; BreathingCadence.RELAXING -> 0.0; BreathingCadence.BOX -> 4.0; BreathingCadence.EVEN -> 0.0 }
}

/** Wave shape of one breath cycle: inhale rises, hold flat high, exhale falls, hold flat low. */
private class BreathCurve(c: BreathingCadence) {
    private val inhale = BreathPhase.INHALE.seconds(c)
    private val holdIn = BreathPhase.HOLD_IN.seconds(c)
    private val exhale = BreathPhase.EXHALE.seconds(c)
    private val holdOut = BreathPhase.HOLD_OUT.seconds(c)
    val cycleDuration = maxOf(0.001, inhale + holdIn + exhale + holdOut)

    private fun smoothstep(x: Double): Double { val v = x.coerceIn(0.0, 1.0); return v * v * (3 - 2 * v) }

    /** 0..1 height at cycle progress [c]. */
    fun value(c: Float): Float {
        val t = c.toDouble() * cycleDuration
        val endInhale = inhale; val endHoldIn = inhale + holdIn; val endExhale = inhale + holdIn + exhale
        return when {
            t < endInhale -> smoothstep(t / maxOf(0.001, inhale)).toFloat()
            t < endHoldIn -> 1f
            t < endExhale -> (1 - smoothstep((t - endHoldIn) / maxOf(0.001, exhale))).toFloat()
            else -> 0f
        }
    }

    fun phaseLabel(c: Float): String {
        val t = c.toDouble() * cycleDuration
        val endInhale = inhale; val endHoldIn = inhale + holdIn; val endExhale = inhale + holdIn + exhale
        return when {
            t < endInhale -> "Breathe in"
            t < endHoldIn -> "Hold"
            t < endExhale -> "Breathe out"
            else -> "Hold"
        }
    }

    fun progress(elapsed: Double): Float {
        var mod = elapsed % cycleDuration
        if (mod < 0) mod += cycleDuration
        return (mod / cycleDuration).toFloat()
    }
}

private const val BREATH_SESSION = 90.0

@Composable
fun BreathingView(initialCadence: BreathingCadence = BreathingCadence.CALM, onClose: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var cadence by remember { mutableStateOf(initialCadence) }
    var showDone by remember { mutableStateOf(false) }
    var rewardQuote by remember { mutableStateOf("") }

    // Two clocks: the wave clock (resets on cadence switch) and the session clock.
    var waveStartMs by remember { mutableStateOf(0L) }
    var sessionStartMs by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var remaining by remember { mutableFloatStateOf(BREATH_SESSION.toFloat()) }
    var lastLabel by remember { mutableStateOf("") }

    fun startSession() {
        val now = System.currentTimeMillis()
        waveStartMs = now; sessionStartMs = now
        remaining = BREATH_SESSION.toFloat()
        started = true; showDone = false
    }

    // Frame loop: advance the wave + session clocks while a session is running.
    LaunchedEffect(started, showDone) {
        if (!started || showDone) return@LaunchedEffect
        while (true) {
            withFrameMillis { now ->
                elapsed = (System.currentTimeMillis() - waveStartMs) / 1000f
                remaining = maxOf(0f, (BREATH_SESSION - (System.currentTimeMillis() - sessionStartMs) / 1000.0).toFloat())
            }
            if (remaining <= 0f) {
                rewardQuote = GameQuotes.randomBreathingReward()
                Haptics.successHeavy()
                showDone = true
                break
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            showDone -> BreathingDone(rewardQuote, sessionSeconds = (BREATH_SESSION - remaining), onAgain = { startSession() }, onClose = onClose)
            !started -> GameIntroView(
                title = "Breathe", symbol = "wind",
                blurb = "A moment to slow everything down.",
                lines = listOf(
                    GameIntroLine("arrow.up.and.down", "Follow the ball up as you inhale, down as you exhale"),
                    GameIntroLine("metronome", "Pick a cadence that feels right"),
                    GameIntroLine("timer", "Breathe along until the timer runs out"),
                ),
                gradient = Clear30Gradients.meditation, glow = Clear30Colors.meditation1,
                onClose = onClose, onStart = { startSession() },
            )
            else -> Column(Modifier.fillMaxSize().padding(top = Dimens.headingTopPadding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding), horizontalArrangement = Arrangement.End) {
                    GameQuitButton(onQuit = onClose)
                }
                Spacer(Modifier.weight(1f))
                BreathWave(cadence, elapsed) { label ->
                    if (label != lastLabel) { lastLabel = label; Haptics.lightImpact() }
                }
                Spacer(Modifier.weight(1f))
                CadenceChips(cadence, remaining) { picked ->
                    if (picked != cadence) {
                        cadence = picked
                        val now = System.currentTimeMillis()
                        waveStartMs = now; sessionStartMs = now
                        remaining = BREATH_SESSION.toFloat()
                    }
                }
            }
        }
    }
}

@Composable
private fun BreathWave(cadence: BreathingCadence, elapsed: Float, onLabel: (String) -> Unit) {
    val curve = remember(cadence) { BreathCurve(cadence) }
    val currentC = curve.progress(elapsed.toDouble())
    val label = curve.phaseLabel(currentC)
    LaunchedEffect(label) { onLabel(label) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        // Phase chip.
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.button)
                .border(2.dp, Clear30Colors.meditation1.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            Icon(sfSymbol("wind"), null, tint = Clear30Colors.meditation1, modifier = Modifier.size(13.dp))
            SmallText(label)
        }
        // Rolling-hill wave: a wave-shaped track scrolls right-to-left under a fixed ball.
        Canvas(Modifier.fillMaxWidth().height(280.dp)) {
            val w = size.width; val h = size.height
            val ballX = w / 2f
            val baseY = h * 0.85f; val peakY = h * 0.29f
            val amplitude = baseY - peakY
            val scrollSpeed = w / curve.cycleDuration.toFloat()
            val ballY = baseY - curve.value(currentC) * amplitude

            // Track path.
            val top = Path()
            var px = 0f; var first = true
            while (px <= w) {
                val waveTime = elapsed + (px - ballX) / scrollSpeed
                var mod = waveTime % curve.cycleDuration.toFloat()
                if (mod < 0) mod += curve.cycleDuration.toFloat()
                val c = mod / curve.cycleDuration.toFloat()
                val y = baseY - curve.value(c) * amplitude
                if (first) { top.moveTo(px, y); first = false } else top.lineTo(px, y)
                px += 3f
            }
            // Filled area under the curve, fading down.
            val fill = Path().apply {
                addPath(top); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    0f to Clear30Colors.meditation1.copy(alpha = 0.25f),
                    0.6f to Clear30Colors.meditation1.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                ),
            )
            drawPath(top, color = Clear30Colors.meditation1, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The ball, sitting on the track.
            drawCircle(Clear30Colors.meditation1.copy(alpha = 0.25f), radius = 35f, center = Offset(ballX, ballY))
            drawCircle(Color.White, radius = 18f, center = Offset(ballX, ballY))
            drawCircle(Clear30Colors.meditation1, radius = 18f, center = Offset(ballX, ballY), style = Stroke(width = 3f))
        }
    }
}

@Composable
private fun CadenceChips(current: BreathingCadence, remaining: Float, onPick: (BreathingCadence) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = Dimens.horizontalPadding, end = Dimens.horizontalPadding, bottom = Dimens.cardSpacing * 2),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallText("Cadence")
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)
                    .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(sfSymbol("timer"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(11.dp))
                TinyText(formatTime(remaining), color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
        // Two per row.
        BreathingCadence.entries.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                rowItems.forEach { c ->
                    val selected = c == current
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(99.dp))
                            .background(if (selected) Clear30Gradients.meditation else Clear30Gradients.gray)
                            .let { if (!selected) it.border(1.5.dp, Clear30Colors.opacityGray, RoundedCornerShape(99.dp)) else it }
                            .clickable { Haptics.lightImpact(); onPick(c) }
                            .padding(vertical = Dimens.cardSpacing / 1.75f),
                        contentAlignment = Alignment.Center,
                    ) {
                        TinyText("${c.title} · ${c.pattern}", color = if (selected) Color.White else Clear30Colors.text, maxLines = 1)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BreathingDone(quote: String, sessionSeconds: Double, onAgain: () -> Unit, onClose: () -> Unit) {
    var iconPop by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (iconPop) 1f else 0.6f, label = "breathDonePop")
    LaunchedEffect(Unit) { iconPop = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Dimens.headingTopPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding), horizontalArrangement = Arrangement.End) {
                GameQuitButton(onQuit = onClose)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(100.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                    Box(
                        Modifier.size(80.dp).scale(scale)
                            .clip(CircleShape).background(Clear30Gradients.meditation)
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(sfSymbol("wind"), null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Heading3("You found your calm")
                    SmallText(quote, color = Clear30Colors.text.copy(alpha = 0.75f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                    SmallText(formatTime(sessionSeconds.toFloat()), color = Clear30Colors.meditation1)
                    SmallText("breathed", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().padding(start = Dimens.horizontalPadding, end = Dimens.horizontalPadding, bottom = Dimens.cardSpacing * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                GamePrimaryButton("Go again", "arrow.clockwise", Clear30Gradients.meditation) { onAgain() }
                GamePrimaryButton("Done", null, null, onClick = onClose)
            }
        }
        ConfettiOverlay()
    }
}

private fun formatTime(t: Float): String {
    val total = maxOf(0, kotlin.math.ceil(t.toDouble()).toInt())
    return "%d:%02d".format(total / 60, total % 60)
}
