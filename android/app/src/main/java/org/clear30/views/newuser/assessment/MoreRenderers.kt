package org.clear30.views.newuser.assessment

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import kotlinx.datetime.atStartOfDayIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.model.AssessmentDaysType
import org.clear30.data.model.ImageChoice
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.VerticalScrollPicker
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
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
 * Server payloads pair `images[i]` with `options[i]`. iOS auto-forwards on tap
 * (medium haptic); here we briefly fill the tapped tile with the clear30 gradient
 * (white icon/text) so the selection is visible, then advance — matching the
 * requested "gradient highlight on select" behaviour.
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

    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        selected?.let { idx ->
            delay(2 * Anim.waitMillis)
            onCompleted(idx)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        pairs.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                row.forEach { (flatIndex, choice, label) ->
                    val isSelected = selected == flatIndex
                    Crossfade(
                        targetState = isSelected,
                        animationSpec = Anim.default(),
                        modifier = Modifier.weight(1f),
                        label = "imgSelect",
                    ) { sel ->
                        Clear30Card(
                            modifier = Modifier.fillMaxWidth().pressScale { if (selected == null) selected = flatIndex },
                            gradient = if (sel) Clear30Gradients.clear30 else null,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (choice.isSystemImage) {
                                    Icon(
                                        sfSymbol(choice.imageName),
                                        contentDescription = null,
                                        tint = if (sel) androidx.compose.ui.graphics.Color.White else Clear30Colors.green,
                                        modifier = Modifier.size(48.dp),
                                    )
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
                }
                // Pad row if odd count
                if (row.size == 1) Box(Modifier.weight(1f))
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Date picker — Material3 calendar; returns the selected date or null. The
 * question's min/max are day offsets from today (iOS `.datePicker` semantics —
 * e.g. the break start date allows today…today+14) and constrain selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentDatePicker(
    question: ProgramAssessmentQuestion,
    cancelOption: String?,
    onCompleted: (Instant?) -> Unit,
) {
    val todayUtcMs = remember {
        // DatePicker works in UTC-midnight millis; anchor "today" the same way.
        val today = org.clear30.data.model.PlainDate.from(org.clear30.util.now())
        kotlinx.datetime.LocalDate(today.year, today.month, today.day)
            .atStartOfDayIn(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()
    }
    val dayMs = 86_400_000L
    val minMs = todayUtcMs + question.min * dayMs
    val maxMs = todayUtcMs + question.max * dayMs
    val state = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMs..maxMs
        },
    )

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Box(Modifier.weight(1f)) {
            DatePicker(state = state, modifier = Modifier.fillMaxWidth())
        }
        StretchedButton("Next", gradient = Clear30Gradients.clear30) {
            // selectedDateMillis is UTC-midnight of the picked CALENDAR DATE.
            // Re-anchor it to local midnight — converting the raw instant through
            // the local zone shifts the date back a day for UTC-negative zones
            // (picking "Jul 13" at 10pm ET used to store Jul 12).
            val selected = state.selectedDateMillis?.let { ms ->
                Instant.fromEpochMilliseconds(ms)
                    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                    .atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
            }
            onCompleted(selected)
        }
        if (cancelOption != null) {
            SmallText(
                cancelOption,
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.cardSpacing / 2)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .pressScale(haptic = false) { onCompleted(null) }
                    .padding(12.dp),
                color = LocalDim(),
            )
        }
        Spacer(Modifier.size(Dimens.cardSpacing))
    }
}

/**
 * Number / days renderer — ported from AssessmentDays.swift. Shows a vertical
 * scroll-picker wheel (the centered item enlarges, like iOS) instead of a text
 * field, matching iOS's `VerticalScrollPicker` of `AssessmentDaysCard`s.
 *
 * `daysUsing` shows "`index` of `max-1` days" on the button; the other
 * (date-backed) types show relative-day text ("Today", "Yesterday", "N days
 * ago") and a plain "Next". The submitted value is `index - additionalItems`
 * (iOS `getValue`), carried as an Int to AllAssessment.
 */
@Composable
fun AssessmentNumber(
    question: ProgramAssessmentQuestion,
    daysType: AssessmentDaysType,
    onCompleted: (Int) -> Unit,
) {
    val max = question.max
    val additionalItems = when (daysType) {
        is AssessmentDaysType.LastSmokedAndStartSoon -> daysType.startSoonMax
        else -> 0
    }
    val numItems = (max + additionalItems).coerceAtLeast(1)
    val reversed = when (daysType) {
        is AssessmentDaysType.LastSmoked, is AssessmentDaysType.LastSmokedAndStartSoon -> true
        else -> false
    }
    val initialIndex = when (daysType) {
        is AssessmentDaysType.LastSmokedAndStartSoon -> (daysType.startSoonMax - 1).coerceAtLeast(0)
        is AssessmentDaysType.StartSoon -> 1
        else -> 0
    }.coerceIn(0, numItems - 1)

    var selectedIndex by remember(daysType) { mutableIntStateOf(initialIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))

        VerticalScrollPicker(
            numItems = numItems,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { selectedIndex = it },
            startAtBottom = reversed,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(Dimens.cornerRadius))
                // iOS uses clear30OpacityGray @ 0.5 (a faint gray well); 0.25 is
                // the closest allowed raw alpha to that very-light fill.
                .background(Clear30Colors.opacityGray.copy(alpha = 0.25f)),
        ) { index ->
            Clear30Card {
                SmallTextGradient(daysText(daysType, index, additionalItems))
            }
        }

        Spacer(Modifier.weight(1f))

        val value = selectedIndex - additionalItems
        if (daysType is AssessmentDaysType.DaysUsing) {
            StretchedButton(
                "I used cannabis $selectedIndex of ${max - 1} days",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier.padding(bottom = Dimens.cardSpacing),
            ) { onCompleted(value) }
        } else {
            StretchedButton(
                "Next",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier.padding(bottom = Dimens.cardSpacing),
            ) { onCompleted(value) }
        }
    }
}

/** Picker-cell label (iOS `AssessmentDaysType.getText`). */
private fun daysText(daysType: AssessmentDaysType, index: Int, additionalItems: Int): String {
    val value = index - additionalItems
    return when (daysType) {
        is AssessmentDaysType.DaysUsing -> "$index day${if (index == 1) "" else "s"}"
        else -> relativeDayText(value, future = daysType is AssessmentDaysType.StartSoon)
    }
}

/** "Today" / "Yesterday" / "Tomorrow" / "N days ago" / "in N days" (iOS `relativeToToday`). */
private fun relativeDayText(value: Int, future: Boolean): String = when {
    value == 0 -> "Today"
    future && value == 1 -> "Tomorrow"
    future -> "in $value days"
    value == 1 -> "Yesterday"
    else -> "$value days ago"
}

/** SmallText filled with the clear30 gradient (iOS `AssessmentDaysCard`). */
@Composable
private fun SmallTextGradient(text: String) {
    org.clear30.views.components.SmallTextHighlighted(
        formats = listOf(org.clear30.views.components.HighlightedTextFormat(text, highlighted = true)),
        highlightBrush = Clear30Gradients.clear30,
        highlightBold = false,
    )
}
