package org.clear30.views.newuser.assessment

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestionType
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3Markdown
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/**
 * Result of answering a question — ported from AssessmentQuestionView.swift's
 * result enum (`.multipleChoice(responses)`, `.input(string)`, `.date(date?)`).
 */
sealed interface AssessmentQuestionResult {
    data class MultipleChoice(val responses: List<Int>) : AssessmentQuestionResult
    data class Input(val value: String) : AssessmentQuestionResult
    data class DateAnswer(val date: Instant?) : AssessmentQuestionResult
    /** Day-count answers (AssessmentDaysType) — distinct from option indices. */
    data class Number(val value: Int) : AssessmentQuestionResult
}

/**
 * AssessmentQuestionView — ported from AssessmentQuestionView.swift. Dispatches
 * on [AssessmentQuestionType] to the matching renderer. Multiple-choice is fully
 * ported; the other input types render a placeholder that still advances the
 * flow (each renderer lands in a follow-up).
 */
@Composable
fun AssessmentQuestionView(
    question: ProgramAssessmentQuestion,
    onResult: (AssessmentQuestionResult) -> Unit,
) {
    // Reset every renderer's internal state (selected list, slider value,
    // text/date fields) when navigating to a new question. Without this, the
    // Material3 DatePicker (rememberSaveable) and our remember-backed inputs
    // would leak across consecutive same-typed questions.
    androidx.compose.runtime.key(question.strippedPrompt) {
        when (val t = question.type) {
            is AssessmentQuestionType.MultipleChoice ->
                AssessmentMultipleChoice(question) { onResult(AssessmentQuestionResult.MultipleChoice(it)) }
            is AssessmentQuestionType.SectionedMultipleChoice ->
                AssessmentMultipleChoice(question) { onResult(AssessmentQuestionResult.MultipleChoice(it)) }
            is AssessmentQuestionType.Slider ->
                AssessmentSlider(question, t.valueLabel) {
                    onResult(AssessmentQuestionResult.MultipleChoice(listOf(sliderOptionIndex(question, it))))
                }
            is AssessmentQuestionType.SliderWithCustom ->
                AssessmentSlider(
                    question, t.valueLabel,
                    onCustomInput = { onResult(AssessmentQuestionResult.Input(it.toString())) },
                ) {
                    onResult(AssessmentQuestionResult.MultipleChoice(listOf(sliderOptionIndex(question, it))))
                }
            is AssessmentQuestionType.Spectrum ->
                AssessmentSpectrum(question, t.left, t.middle, t.right, t.showDots) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.MultiSpectrum ->
                AssessmentSpectrum(question, t.left, t.middle, t.right, true) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.Input ->
                AssessmentInput(question, t.placeholder, t.multiLine, t.optional) { onResult(AssessmentQuestionResult.Input(it)) }
            is AssessmentQuestionType.ImageChoiceType ->
                AssessmentImageChoice(question, t.imageChoices) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.DatePicker ->
                AssessmentDatePicker(question, t.cancelOption) { onResult(AssessmentQuestionResult.DateAnswer(it)) }
            is AssessmentQuestionType.Number ->
                // Carry the raw integer; AllAssessment writes it into
                // question.options[0] + responses=[0] (matching iOS) and lifts
                // it into vm.lastSmokedDate when daysType demands it.
                AssessmentNumber(question, t.dayQuestionType) { onResult(AssessmentQuestionResult.Number(it)) }
            is AssessmentQuestionType.Unknown ->
                // Forward-compat: backend added a type this client doesn't know.
                // Show a placeholder + advance so the assessment isn't blocked.
                UnknownQuestionFallback { onResult(AssessmentQuestionResult.MultipleChoice(emptyList())) }
        }
    }
}

@Composable
private fun UnknownQuestionFallback(onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Dimens.horizontalPadding)) {
        SmallText(
            "Unsupported question — please update the app for the full experience.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = Dimens.cardSpacing),
        )
        StretchedButton("Continue", gradient = Clear30Gradients.clear30, modifier = Modifier, onClick = onNext)
    }
}

/**
 * QuestionCard — prompt header (AssessmentQuestionCard.swift). Leading-aligned:
 * a dim SmallText lead-in, the Heading3-sized markdown prompt, and the iOS
 * "Choose …" hint (TinyText @ 0.5) when the question allows multiple picks.
 */
@Composable
fun QuestionCard(prompt1: String, prompt2: String, choiceMin: Int = 1, choiceMax: Int? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        if (prompt1.isNotBlank()) {
            SmallText(
                prompt1,
                color = LocalContentColor.current.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 4),
            )
        }
        Heading3Markdown(prompt2)
        // "Choose …" hint — iOS: "Choose X." / "Choose X to Y." when min != 1,
        // else "Choose up to Y." (only shown for multi-select, choiceMax != null).
        if (choiceMax != null) {
            val hint = when {
                choiceMin != 1 && choiceMin == choiceMax -> "Choose $choiceMin."
                choiceMin != 1 -> "Choose $choiceMin to $choiceMax."
                else -> "Choose up to $choiceMax."
            }
            TinyText(hint, color = LocalContentColor.current.copy(alpha = 0.5f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * MultipleChoiceButton — ported from AssessmentMultipleChoice.swift's
 * `MultipleChoiceButton`. A full-width left-aligned card; selecting fills the
 * clear30 gradient *in over* the white card (rather than cross-dissolving the
 * whole card, shadow and all, like the old [androidx.compose.animation.Crossfade]).
 * The label crosses from dark to white as the green lands, and the fill blooms in
 * with a slight scale so it reads as the option "filling in." Disabled (max
 * reached) dims to 0.5 opacity. Tapping fires a medium haptic through [pressScale].
 */
@Composable
private fun MultipleChoiceButton(
    title: String,
    isSelected: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit,
) {
    // 0 = unselected (white), 1 = selected (green filled). Drives the gradient
    // overlay's alpha/scale and the label's color so the whole selection animates
    // as one coherent fill instead of snapping.
    val fill by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = Anim.default(),
        label = "mcFill",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (isDisabled) Modifier.alpha(0.5f) else Modifier)
            .pressScale(onClick = onClick),
    ) {
        // White card base (shadow + rounded background), sized to the label below.
        Box(Modifier.matchParentSize().cardStyle(color = Clear30Colors.button, padding = false))
        // The gradient fills in on top of the white base; no shadow of its own so
        // it doesn't double the base card's glow. A tiny scale makes it bloom.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = fill
                    val s = 0.97f + 0.03f * fill
                    scaleX = s
                    scaleY = s
                }
                .cardStyle(
                    shadowColor = Color.Transparent,
                    gradient = Clear30Gradients.clear30,
                    padding = false,
                ),
        )
        // Label — inherits Clear30Card's 16dp inset; color crosses to white as the
        // green fill lands (transparent card = padding only, no extra bg/shadow).
        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shadowColor = Color.Transparent,
        ) {
            SmallText(title, color = lerp(Clear30Colors.text, Color.White, fill))
        }
    }
}

/**
 * AssessmentMultipleChoice — ported from AssessmentMultipleChoice.swift.
 * Single-select (max == 1) selects then auto-advances after a short beat; multi-
 * select shows the Next button once `selected.count >= min`. (Sectioned/shuffled
 * layouts are simplified to a flat list for now.)
 */
@Composable
fun AssessmentMultipleChoice(
    question: ProgramAssessmentQuestion,
    // Options that can't combine with others (iOS `exclusiveOptionIndices` —
    // e.g. "None of the above"): picking one clears the rest, and vice versa.
    exclusiveOptionIndices: List<Int>? = null,
    onCompleted: (List<Int>) -> Unit,
) {
    val options = question.displayedOptions ?: question.options
    val min = question.min
    val max = question.max
    val isAge = question.strippedPrompt == AssessmentQuestionID.AGE.raw
    val selected = remember { mutableStateListOf<Int>() }
    var pendingAutoForward by remember { mutableStateOf(false) }

    // Display order: shuffled questions (breakReason/triggers) shuffle ONCE per
    // entry — computed in remember{} so redraws don't reshuffle — with any
    // "…Other" option pinned last (iOS AssessmentMultipleChoice.swift:70-81).
    // `selected` keeps ORIGINAL option indices, so responses map back to the
    // right labels regardless of display order.
    val optionIndexes = remember(question) {
        if (question.shuffled == true) {
            val other = options.indices.filter { options[it].lowercase().endsWith("other") }
            (options.indices - other.toSet()).shuffled() + other
        } else {
            options.indices.toList()
        }
    }

    // Single-select auto-forward: hold ~3 * GlobalData.wait so the green gradient
    // has time to finish filling in (the fill runs ~Anim.default = 233ms) and is
    // fully visible before the slide pushes to the next question.
    LaunchedEffect(pendingAutoForward) {
        if (pendingAutoForward) {
            delay(3 * Anim.waitMillis)
            onCompleted(selected.toList())
        }
    }

    // The prompt + Next get the horizontal inset; the scroll list keeps its own
    // padding (scrollShadowFix) so the soft card shadows (the ~7dp glow on the
    // top / left / right of each option) are NOT clipped by the scroll viewport.
    // This mirrors iOS's `.padding(.horizontal, scrollShadowFix)` inside the
    // ScrollView with the negative outer offset.
    Column(Modifier.fillMaxSize()) {
        QuestionCard(
            question.prompt1, question.prompt2, min, if (max != 1) max else null,
            Modifier
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.cardSpacing),
        )

        // weight(1f) bounds the scroll area to the available space so the options
        // scroll WITHIN the screen — without it, a long list (e.g. 21 break reasons)
        // grows past the screen and shoves the Next button off the bottom.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // horizontalPadding - scrollShadowFix keeps the cards aligned
                    // with the prompt while leaving scrollShadowFix of breathing
                    // room inside the clip for the card shadow. Vertical
                    // headingTopPadding keeps the FIRST card's top shadow visible.
                    .padding(horizontal = Dimens.horizontalPadding - Dimens.scrollShadowFix)
                    .padding(vertical = Dimens.headingTopPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                optionIndexes.forEach { index ->
                    val option = options[index]
                    val isSelected = index in selected
                    val isDisabled = selected.size >= max && !isSelected
                    Box(
                        Modifier.padding(horizontal = Dimens.scrollShadowFix),
                    ) {
                        MultipleChoiceButton(
                            title = option,
                            isSelected = isSelected,
                            isDisabled = isDisabled,
                        ) {
                            if (isSelected) {
                                selected.remove(index)
                            } else {
                                val exclusives = exclusiveOptionIndices.orEmpty()
                                if (index in exclusives) {
                                    // An exclusive pick clears everything else…
                                    selected.clear()
                                } else {
                                    // …and a regular pick clears any exclusives.
                                    selected.removeAll { it in exclusives }
                                }
                                if (selected.size >= max) selected.removeAt(selected.lastIndex)
                                selected.add(index)
                                // Age confirms via an "I am <age>" button (iOS), so
                                // it doesn't auto-forward like other single-selects.
                                if (max == 1 && !isAge) pendingAutoForward = true
                            }
                        }
                    }
                }
            }
        }

        // Terms-of-Use footer (iOS `subtext` via MiniTextWithLinks) — used on the
        // age screen; the Terms / Privacy links are tappable.
        if (question.subtext.isNotEmpty()) {
            TermsSubtext(
                question.subtext,
                Modifier
                    .padding(horizontal = Dimens.horizontalPadding)
                    .padding(top = Dimens.cardSpacing / 2),
            )
        }
        when {
            // Age: an "I am <age>" confirm button after selecting (iOS); no auto-forward.
            isAge && selected.isNotEmpty() -> StretchedButton(
                "I am ${options[selected.first()]}",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier
                    .padding(horizontal = Dimens.horizontalPadding)
                    .padding(top = Dimens.cardSpacing / 2, bottom = Dimens.cardSpacing / 2),
            ) { onCompleted(selected.toList()) }
            // Multi-select Next, shown once `min` chosen (iOS only reveals it then).
            max > 1 && selected.size >= min -> StretchedButton(
                "Next",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier
                    .padding(horizontal = Dimens.horizontalPadding)
                    .padding(top = Dimens.cardSpacing / 2),
            ) { onCompleted(selected.toList()) }
        }
    }
}

/**
 * Parses `[label](url)` markdown into tappable, underlined links — the shared
 * Compose analogue of iOS `MiniTextWithLinks` / `SmallTextWithLinks` (reused by
 * the intro terms footers and the pain-point citation).
 */
internal fun linkedText(markdown: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    var last = 0
    for (m in regex.findAll(markdown)) {
        if (m.range.first > last) append(markdown.substring(last, m.range.first))
        withLink(LinkAnnotation.Url(m.groupValues[2])) {
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(m.groupValues[1]) }
        }
        last = m.range.last + 1
    }
    if (last < markdown.length) append(markdown.substring(last))
}

/**
 * iOS AssessmentQuestionView's slider result mapping: with displayedOptions the
 * 1-based slider value maps to option index `value - 1`; otherwise the first
 * option containing the value's digits wins (fallback index 0). The raw value
 * must NOT be recorded directly — responses are 0-based option indices
 * everywhere (submission payload, restore, weekly spend/usage stats).
 */
private fun sliderOptionIndex(question: ProgramAssessmentQuestion, sliderValue: Int): Int =
    if (question.displayedOptions != null) {
        sliderValue - 1
    } else {
        question.options.indexOfFirst { it.contains(sliderValue.toString()) }.coerceAtLeast(0)
    }

/** Dim Terms-of-Use footer with tappable links (iOS MiniTextWithLinks). */
@Composable
private fun TermsSubtext(text: String, modifier: Modifier = Modifier) {
    Text(
        linkedText(text),
        modifier = modifier.fillMaxWidth(),
        color = LocalContentColor.current.copy(alpha = 0.5f),
        fontFamily = Lexend,
        fontSize = 10.sp,
        lineHeight = 13.sp,
    )
}
