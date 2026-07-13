package org.clear30.views.newuser.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.Heading1
import org.clear30.views.components.MultiLineOffWhiteInput
import org.clear30.views.components.OffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.softShadow
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.roundToInt

/**
 * Free-text question renderer — ported from AssessmentInput.swift. Uses the brand
 * [OffWhiteInput] (single-line) / [MultiLineOffWhiteInput] so the field matches
 * the rest of onboarding (rounded off-white box, Lexend text). Next is a full-
 * width pill (StretchedButton), enabled when optional or the field is non-blank.
 */
@Composable
fun AssessmentInput(
    question: ProgramAssessmentQuestion,
    placeholder: String,
    multiLine: Boolean,
    optional: Boolean,
    onCompleted: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2)
        Spacer(Modifier.weight(1f))
        if (multiLine) {
            MultiLineOffWhiteInput(value = input, onValueChange = { input = it }, placeholder = placeholder)
        } else {
            OffWhiteInput(value = input, onValueChange = { input = it }, placeholder = placeholder)
        }
        Spacer(Modifier.weight(1f))
        val enabled = optional || input.isNotBlank()
        StretchedButton(
            "Next",
            gradient = if (enabled) Clear30Gradients.clear30 else null,
            modifier = Modifier.padding(bottom = Dimens.cardSpacing),
        ) {
            if (enabled) onCompleted(input.trim())
        }
    }
}

/**
 * Slider question renderer — ported from AssessmentSlider.swift.
 *
 * The slider works in iOS's 1-indexed value space: `value` ranges over
 * `question.min..question.max` and starts at `min + round((max-min)/2)`. The big
 * label shows `displayedOptions[value - 1]` when present (e.g. "$30"), otherwise
 * "`value` `valueLabel`(s)" pluralized (e.g. "4 days"). The returned integer is
 * the raw slider value; AssessmentQuestionView maps it back to an option index
 * (displayedOptions → value-1, else first option containing "`value`").
 */
@Composable
fun AssessmentSlider(question: ProgramAssessmentQuestion, valueLabel: String, onCompleted: (Int) -> Unit) {
    val min = question.min
    val max = question.max
    if (max < min) { onCompleted(min); return }
    val display = question.displayedOptions
    // iOS: initialValue = min + ((max - min) / 2).rounded()  (.rounded() = half-up)
    val initial = min + ((max - min) / 2.0).roundToInt()
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    val rounded = value.roundToInt().coerceIn(min, max)

    val bigLabel = when {
        display != null && rounded - 1 in display.indices -> display[rounded - 1]
        valueLabel.isBlank() -> "$rounded"
        else -> "$rounded $valueLabel${if (rounded == 1) "" else "s"}"
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        Heading1(bigLabel)
        Spacer(Modifier.height(Dimens.cardSpacing * 2))
        GradientSlider(
            value = value,
            valueRange = min.toFloat()..max.toFloat(),
            onValueChange = { newValue ->
                if (newValue.roundToInt().coerceIn(min, max) != rounded) Haptics.mediumImpact()
                value = newValue
            },
            onValueChangeFinished = { value = value.roundToInt().coerceIn(min, max).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        // Track endpoint labels (iOS shows displayOptions.first/last or min/max).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallText(display?.firstOrNull() ?: "$min", color = LocalDim())
            SmallText(display?.lastOrNull() ?: "$max", color = LocalDim())
        }
        Spacer(Modifier.weight(1f))
        StretchedButton(
            "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.padding(top = Dimens.cardSpacing, bottom = Dimens.cardSpacing),
        ) {
            onCompleted(rounded)
        }
    }
}

/**
 * GradientSlider — custom slider matching iOS `AssessmentSlider`: a 20dp track
 * whose active portion is the blue→green clear30 gradient (with a soft green glow)
 * over a light-gray remainder, and a white 27dp thumb. Drag or tap to set; the
 * caller snaps to whole values on release.
 */
@Composable
private fun GradientSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumb = 27.dp
    val track = 20.dp
    val span = valueRange.endInclusive - valueRange.start
    BoxWithConstraints(modifier.height(thumb)) {
        val wPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { thumb.toPx() }
        val travel = (wPx - thumbPx).coerceAtLeast(1f)
        val frac = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val centerX = thumbPx / 2f + frac * travel

        fun setFromX(x: Float) {
            val f = ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
            onValueChange(valueRange.start + f * span)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(thumb)
                .pointerInput(wPx) { detectTapGestures { setFromX(it.x); onValueChangeFinished() } }
                .pointerInput(wPx) {
                    detectHorizontalDragGestures(onDragEnd = onValueChangeFinished) { change, _ ->
                        setFromX(change.position.x)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Light-gray track.
            Box(
                Modifier.fillMaxWidth().height(track)
                    .clip(RoundedCornerShape(50)).background(Clear30Colors.opacityGray),
            )
            // Active gradient fill up to the thumb, with a soft green glow.
            Box(
                Modifier
                    .width(with(density) { centerX.toDp() })
                    .height(track)
                    .softShadow(Clear30Colors.green.copy(alpha = 0.5f), cornerRadius = track / 2, blurRadius = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Clear30Gradients.clear30),
            )
            // White thumb.
            Box(
                Modifier
                    .offset { IntOffset((centerX - thumbPx / 2f).roundToInt(), 0) }
                    .size(thumb)
                    .softShadow(Clear30Colors.shadow, cornerRadius = 10.dp, blurRadius = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            )
        }
    }
}

/**
 * Spectrum renderer — ported from AssessmentSpectrum.swift. A stepped slider with
 * left/middle/right anchor labels. When `showDots` (the default), the displayed
 * option at the current step is shown as a big emoji above the track (e.g. the
 * help/harm 😖🙁😐🙂😁 faces); otherwise the labels carry the meaning (e.g. the
 * ☀️ … 🌙 time-of-day spectrum). Returns the selected step index.
 */
@Composable
fun AssessmentSpectrum(
    question: ProgramAssessmentQuestion,
    left: String,
    middle: String,
    right: String,
    showDots: Boolean,
    onCompleted: (Int) -> Unit,
) {
    val displays = question.displayedOptions ?: question.options
    val steps = displays.size.coerceAtLeast(2)
    // iOS starts centered: value = (steps - 1) / 2.
    var value by remember { mutableFloatStateOf(((steps - 1) / 2).toFloat()) }
    val index = value.roundToInt().coerceIn(0, steps - 1)

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        // Current step's display, in BOTH modes (iOS AssessmentSpectrum.swift:47-61):
        // showDots gets the large 75pt emoji in an 80pt frame; the non-dots
        // variant (e.g. numeric steps) reads as a Heading1.
        if (index in displays.indices) {
            if (showDots) {
                androidx.compose.material3.Text(displays[index], fontSize = 75.sp, modifier = Modifier.height(80.dp))
            } else {
                Heading1(displays[index])
            }
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0f..(steps - 1).toFloat(),
            steps = (steps - 2).coerceAtLeast(0),
            colors = clear30SliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding / 2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // showDots dims the anchor labels (0.5); without dots they read full strength.
            val anchorColor = if (showDots) LocalDim() else Clear30Colors.text
            SmallText(left, color = anchorColor)
            if (middle.isNotEmpty()) SmallText(middle, color = anchorColor)
            SmallText(right, color = anchorColor)
        }
        Spacer(Modifier.weight(1f))
        StretchedButton(
            "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.padding(top = Dimens.cardSpacing, bottom = Dimens.cardSpacing),
        ) {
            onCompleted(index)
        }
    }
}

/**
 * clear30-green slider track/thumb, mirroring the iOS gradient track + white thumb.
 * TODO(port): iOS draws the track with the full clear30 blue→green gradient and a
 * rounded-white thumb with a green glow; Material3 Slider only takes solid colors,
 * so this approximates with solid green. A custom track/thumb would match exactly.
 */
@Composable
internal fun clear30SliderColors() = SliderDefaults.colors(
    thumbColor = Clear30Colors.green,
    activeTrackColor = Clear30Colors.green,
    inactiveTrackColor = Clear30Colors.text.copy(alpha = 0.25f),
    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
    inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
)

/** Dimmed content color (0.5) for secondary labels, inheriting white on gradients. */
@Composable
internal fun LocalDim() = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.5f)
