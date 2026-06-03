package org.clear30.views.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Named gradients, ported 1:1 from GlobalData (Clear30App.swift).
 *
 * SwiftUI LinearGradient takes UnitPoint start/end (0..1). Compose's
 * linearGradient takes pixel offsets, so we expose helpers that map the
 * SwiftUI unit points onto the actual draw size. For the common
 * leading->trailing / topLeading->bottomTrailing cases use the prebuilt
 * brushes; for size-aware gradients use [linear].
 */
object Clear30Gradients {

    // SwiftUI UnitPoint analogues
    val leading = Offset(0f, 0.5f);  val trailing = Offset(1f, 0.5f)
    val topLeading = Offset(0f, 0f); val bottomTrailing = Offset(1f, 1f)
    val bottomLeading = Offset(0f, 1f); val topTrailing = Offset(1f, 0f)

    /** Build a brush from unit points (matches SwiftUI's start/endPoint semantics). */
    fun linear(colors: List<Color>, start: Offset = leading, end: Offset = trailing): Brush =
        Brush.linearGradient(
            colors = colors,
            start = Offset(start.x * GRAD_SPAN, start.y * GRAD_SPAN),
            end = Offset(end.x * GRAD_SPAN, end.y * GRAD_SPAN),
        )

    private const val GRAD_SPAN = 1000f

    val clear30 = linear(listOf(Clear30Colors.blue, Clear30Colors.green), leading, trailing)
    val clear30Bright = linear(listOf(Clear30Colors.brightGreen1, Clear30Colors.brightGreen2), bottomLeading, topTrailing)
    val reddit = linear(listOf(Clear30Colors.reddit1, Clear30Colors.reddit2), leading, trailing)
    val youtube = linear(listOf(Clear30Colors.youTube1, Clear30Colors.youTube2), leading, trailing)
    val meditation = linear(listOf(Clear30Colors.meditation1, Clear30Colors.meditation2), leading, trailing)
    val symptomCard = linear(listOf(Clear30Colors.symptom1, Clear30Colors.symptom2), bottomLeading, topTrailing)
    val journals = linear(listOf(Clear30Colors.journal1, Clear30Colors.journal2), topLeading, bottomTrailing)
    val community = linear(listOf(Clear30Colors.community1, Clear30Colors.community2), topLeading, bottomTrailing)
    val instagram = linear(listOf(Clear30Colors.instagram1, Clear30Colors.instagram2), bottomLeading, topTrailing)
    val supplements = linear(listOf(Clear30Colors.supplements1, Clear30Colors.supplements2), bottomLeading, topTrailing)
    val sleep = linear(listOf(Clear30Colors.sleep1, Clear30Colors.sleep2), bottomLeading, topTrailing)
    val red = linear(listOf(Clear30Colors.red1, Clear30Colors.red2), bottomLeading, topTrailing)
    val claire = linear(listOf(Clear30Colors.claire1, Clear30Colors.claire2), leading, trailing)
    val white = Brush.linearGradient(listOf(Color.White, Color.White))
    val black = Brush.linearGradient(listOf(Color.Black, Color.Black))
    val button = Brush.linearGradient(listOf(Clear30Colors.button, Clear30Colors.button))
    val gray = Brush.linearGradient(listOf(Clear30Colors.opacityGray, Clear30Colors.opacityGray))
    val grayFlat = Brush.linearGradient(listOf(Clear30Colors.opacityGrayFlattened, Clear30Colors.opacityGrayFlattened))

    /**
     * HSV-based gradient used by CustomCheckIn (hue 0..1, sat 0.6, brightness 0.9,
     * second stop offset by `hueOffset`), bottomLeading -> topTrailing.
     */
    fun hue(hue: Double, sat: Double = 0.6, value: Double = 0.9, hueOffset: Double = 0.07): Brush =
        linear(
            listOf(
                Color.hsv((hue * 360).toFloat(), sat.toFloat(), value.toFloat()),
                Color.hsv(((hue + hueOffset) * 360).toFloat() % 360f, sat.toFloat(), value.toFloat()),
            ),
            bottomLeading, topTrailing,
        )

    /** Stage gradient pair (1..6) -> brush, matching Stage{n}-1 / Stage{n}-2. */
    fun stage(stage: Int): Brush = when (stage) {
        1 -> linear(listOf(Clear30Colors.stage1a, Clear30Colors.stage1b))
        2 -> linear(listOf(Clear30Colors.stage2a, Clear30Colors.stage2b))
        3 -> linear(listOf(Clear30Colors.stage3a, Clear30Colors.stage3b))
        4 -> linear(listOf(Clear30Colors.stage4a, Clear30Colors.stage4b))
        5 -> linear(listOf(Clear30Colors.stage5a, Clear30Colors.stage5b))
        else -> linear(listOf(Clear30Colors.stage6a, Clear30Colors.stage6b))
    }
}
