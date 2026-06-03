package org.clear30.views.newuser.assessment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import org.clear30.data.model.AssessmentDaysType
import org.clear30.data.model.ImageChoice
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Remaining assessment renderers — image-choice, date picker, number/days.
 * Together with AssessmentRenderers.kt + the multiple-choice in
 * AssessmentQuestionView.kt, these cover every [AssessmentQuestionType] case.
 */

/**
 * Image-choice grid (system SF-symbol icons or migrated drawables).
 *
 * Server payloads pair `images[i]` with `options[i]` — i.e. the i-th tile's
 * label is `options[i]` and the index sent back via [onCompleted] is `i`. If
 * those lists arrive out of sync we use the shorter length so a missing label
 * never gets paired against a misaligned later option (which would make the
 * label under tile N describe a different option than the response value N).
 */
@Composable
fun AssessmentImageChoice(
    question: ProgramAssessmentQuestion,
    images: List<ImageChoice>,
    onCompleted: (Int) -> Unit,
) {
    val context = LocalContext.current
    val options = question.displayedOptions ?: question.options
    val pairs = images.indices
        .filter { it < options.size || it < images.size }
        .map { i -> Triple(i, images[i], options.getOrNull(i)) }

    if (images.size != options.size) {
        android.util.Log.w(
            "AssessmentImageChoice",
            "images.size=${images.size} != options.size=${options.size} for '${question.strippedPrompt}'",
        )
    }

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        pairs.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                row.forEach { (flatIndex, choice, label) ->
                    Clear30Card(
                        modifier = Modifier.weight(1f).clickable { onCompleted(flatIndex) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (choice.isSystemImage) {
                                Icon(sfSymbol(choice.imageName), contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(48.dp))
                            } else {
                                val sanitized = remember(choice.imageName) {
                                    choice.imageName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                                }
                                val resId = remember(sanitized) {
                                    val id = context.resources.getIdentifier(sanitized, "drawable", context.packageName)
                                    if (id == 0) {
                                        android.util.Log.w(
                                            "AssessmentImageChoice",
                                            "Drawable not found for image '${choice.imageName}' (looked for R.drawable.$sanitized) — falling back to photo glyph",
                                        )
                                    }
                                    id
                                }
                                if (resId != 0) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(resId),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                    )
                                } else {
                                    Icon(sfSymbol("photo"), contentDescription = null, tint = Clear30Colors.gray, modifier = Modifier.size(48.dp))
                                }
                            }
                            label?.let { SmallText(it) }
                        }
                    }
                }
                // Pad row if odd count
                if (row.size == 1) Box(Modifier.weight(1f))
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Date picker — Material3 calendar; returns the selected date or null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentDatePicker(
    question: ProgramAssessmentQuestion,
    cancelOption: String?,
    onCompleted: (Instant?) -> Unit,
) {
    val state = rememberDatePickerState()

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Box(Modifier.weight(1f)) {
            DatePicker(state = state, modifier = Modifier.fillMaxWidth())
        }
        DefaultButton("Next", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
            onCompleted(state.selectedDateMillis?.let { Instant.fromEpochMilliseconds(it) })
        }
        if (cancelOption != null) {
            SmallText(
                cancelOption,
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.cardSpacing / 2)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .clickable { onCompleted(null) }
                    .padding(12.dp),
                color = Color.Gray,
            )
        }
    }
}

/**
 * Number / days renderer — a stepper for `AssessmentQuestionType.Number`.
 *
 * For [AssessmentDaysType.LastSmokedAndStartSoon] the question is dual-mode
 * ("last smoked" vs "starting soon"), so the renderer shows a toggle that
 * switches both the suffix and the upper bound — past mode allows up to one
 * year, future ("startSoon") mode is capped at `startSoonMax`.
 *
 * The displayed text and the submitted value are kept identical: typing past
 * the cap immediately rewrites the field to the clamped value so the user can
 * never see a number that disagrees with what gets persisted.
 */
@Composable
fun AssessmentNumber(
    question: ProgramAssessmentQuestion,
    daysType: AssessmentDaysType,
    onCompleted: (Int) -> Unit,
) {
    // For the dual-mode type, the user toggles between past and future framing.
    var isPast by remember(daysType) {
        mutableStateOf(daysType !is AssessmentDaysType.StartSoon)
    }
    val (suffix, max) = when (daysType) {
        is AssessmentDaysType.LastSmoked -> "days ago" to 365
        is AssessmentDaysType.DaysUsing -> "days" to 365
        is AssessmentDaysType.StartSoon -> "days from now" to 60
        is AssessmentDaysType.LastSmokedAndStartSoon ->
            if (isPast) "days ago" to 365 else "days from now" to daysType.startSoonMax
    }

    var value by remember(daysType) { mutableIntStateOf(0) }
    var text by remember(daysType) { mutableStateOf("0") }

    // Re-clamp when the upper bound changes (e.g. user toggled dual-mode).
    androidx.compose.runtime.LaunchedEffect(max) {
        if (value > max) {
            value = max
            text = max.toString()
        }
    }

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))

        if (daysType is AssessmentDaysType.LastSmokedAndStartSoon) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                DefaultButton(
                    "I last smoked",
                    gradient = if (isPast) Clear30Gradients.clear30 else null,
                    modifier = Modifier.weight(1f),
                ) { isPast = true }
                DefaultButton(
                    "I'm starting",
                    gradient = if (!isPast) Clear30Gradients.clear30 else null,
                    modifier = Modifier.weight(1f),
                ) { isPast = false }
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    val digits = t.filter { it.isDigit() }.take(4)
                    val coerced = (digits.toIntOrNull() ?: 0).coerceIn(0, max)
                    value = coerced
                    // Keep displayed text and value in sync — if the user typed
                    // past `max`, the field collapses to the clamped value
                    // immediately rather than showing a number we won't submit.
                    text = if (digits.isEmpty()) "" else coerced.toString()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SmallText(suffix)
        }

        Spacer(Modifier.weight(1f))
        DefaultButton("Next", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
            onCompleted(value)
        }
    }
}
