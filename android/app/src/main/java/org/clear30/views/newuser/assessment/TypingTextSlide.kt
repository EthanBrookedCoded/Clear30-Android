package org.clear30.views.newuser.assessment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import org.clear30.views.theme.Lexend
import java.text.BreakIterator
import kotlin.math.cos
import kotlin.math.exp

/** One line of a [TypingTextSlide], with its style + opacity (iOS `TypingLine`). */
data class TypingLine(val text: String, val style: TypingTextStyle, val alpha: Float = 1f)

enum class TypingTextStyle { Heading3, Body, Small }

/**
 * TypingTextSlide — reveals each line one character at a time, then auto-forwards
 * via [onComplete]. Ported from TypingTextSlide.swift, with a playful twist: each
 * character doesn't just fade — it **bounces in** with a springy overshoot (a
 * damped-spring step response) and a light haptic, so the welcome feels lively.
 *
 * A single reveal "clock" drives everything: each character's pop is a pure
 * function of `clock - itsGlobalIndex`, computed in `graphicsLayer` (draw-time, so
 * the layout never reflows and only the transform re-runs per frame). Every glyph
 * is laid out up front (untyped ones scaled to 0), so a centered line never jitters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypingTextSlide(
    lines: List<TypingLine>,
    centerText: Boolean = true,
    onComplete: (() -> Unit)? = null,
) {
    // Split into grapheme clusters so emoji (e.g. "☺️") pop in as one unit.
    val lineGraphemes = remember(lines) { lines.map { it.text.graphemes() } }
    val total = remember(lineGraphemes) { lineGraphemes.sumOf { it.size } }
    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(total) {
        clock.snapTo(0f)
        delay(INITIAL_DELAY)
        // Light haptic as each (non-blank) character lands.
        val haptics = scope.launch {
            for (g in lineGraphemes.flatten()) {
                delay(CHAR_DELAY)
                if (g.isNotBlank()) Haptics.lightImpact()
            }
        }
        clock.animateTo(
            targetValue = total.toFloat() + SETTLE_CHARS,
            animationSpec = tween((total * CHAR_DELAY).toInt() + SETTLE_MS, easing = LinearEasing),
        )
        haptics.cancel()
        delay(COMPLETION_DELAY)
        onComplete?.invoke()
    }

    val align = if (centerText) Alignment.CenterHorizontally else Alignment.Start
    val rowArrangement = if (centerText) Arrangement.Center else Arrangement.Start
    val base = LocalContentColor.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.horizontalPadding * 2),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = align,
    ) {
        Spacer(Modifier.weight(1f))
        var globalIndex = 0
        lineGraphemes.forEachIndexed { i, graphemes ->
            val line = lines[i]
            val (size, weight) = line.style.spec()
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = rowArrangement) {
                graphemes.forEach { g ->
                    val idx = globalIndex
                    Text(
                        text = g,
                        fontFamily = Lexend,
                        fontWeight = weight,
                        fontSize = size,
                        lineHeight = size * LINE_HEIGHT_RATIO,
                        color = base,
                        style = TypingTextStyleSpec,
                        modifier = Modifier.graphicsLayer {
                            // Pop is a pure function of how far the clock has passed
                            // this glyph; pivots from the bottom so glyphs "spring up".
                            val t = clock.value - idx
                            val s = popScale(t)
                            scaleX = s
                            scaleY = s
                            alpha = popAlpha(t) * line.alpha
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    )
                    globalIndex++
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Maps a [TypingTextStyle] to the matching Lexend size/weight (see Text.kt). */
private fun TypingTextStyle.spec(): Pair<TextUnit, FontWeight> = when (this) {
    TypingTextStyle.Heading3 -> 22.sp to FontWeight.Medium
    TypingTextStyle.Body -> 19.sp to FontWeight.Normal
    TypingTextStyle.Small -> 15.5.sp to FontWeight.Normal
}

/**
 * Damped-spring step response (0 → 1 with overshoot) used for the bounce-in. At
 * t=0 it's 0; it springs up past 1 (~1.2) then settles back to 1, giving the
 * playful pop. `t` is measured in character-reveal units.
 */
private fun popScale(t: Float): Float =
    if (t <= 0f) 0f else (1.0 - exp(-POP_DECAY * t) * cos(POP_FREQ * t)).toFloat()

/** Quick fade-in paired with [popScale] so a glyph isn't visible before it pops. */
private fun popAlpha(t: Float): Float =
    if (t <= 0f) 0f else (t / POP_FADE).coerceIn(0f, 1f)

/** Splits a string into grapheme clusters so multi-codepoint emoji stay whole. */
private fun String.graphemes(): List<String> {
    if (isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    val result = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result.add(substring(start, end))
        start = end
        end = iterator.next()
    }
    return result
}

private const val LINE_HEIGHT_RATIO = 1.3f
private val TypingTextStyleSpec = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private const val INITIAL_DELAY = 700L
private const val CHAR_DELAY = 55L
private const val COMPLETION_DELAY = 900L
private const val SETTLE_CHARS = 4f        // extra clock travel so the last glyph settles
private const val SETTLE_MS = 260          // matching extra animation time
private const val POP_DECAY = 4.5          // higher = settles faster (less wobble)
private const val POP_FREQ = 7.0           // higher = more bounces
private const val POP_FADE = 0.5f          // fade-in window, in character-reveal units
