package org.clear30.views.newuser.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import org.clear30.data.model.AssessmentQuestionType
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading2Markdown
import org.clear30.views.components.SmallText
import org.clear30.views.components.Clear30Card
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

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
                AssessmentSlider(question, t.valueLabel) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.SliderWithCustom ->
                AssessmentSlider(question, t.valueLabel) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.Spectrum ->
                AssessmentSpectrum(question, t.left, t.middle, t.right) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
            is AssessmentQuestionType.MultiSpectrum ->
                AssessmentSpectrum(question, t.left, t.middle, t.right) { onResult(AssessmentQuestionResult.MultipleChoice(listOf(it))) }
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
        DefaultButton("Continue", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth(), onClick = onNext)
    }
}

/** QuestionCard — prompt header (QuestionCard.swift). */
@Composable
fun QuestionCard(prompt1: String, prompt2: String, choiceMin: Int = 1, choiceMax: Int? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (prompt1.isNotBlank()) SmallText(prompt1, color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 4.dp))
        Heading2Markdown(prompt2)
        if (choiceMax != null && choiceMax > 1) {
            SmallText("Choose $choiceMin–$choiceMax", color = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * AssessmentMultipleChoice — ported from AssessmentMultipleChoice.swift.
 * Single-select (max == 1) advances immediately on tap; multi-select shows the
 * Next button once `selected.count >= min`. (Sectioned/shuffled layouts are
 * simplified to a flat list for now.)
 */
@Composable
fun AssessmentMultipleChoice(question: ProgramAssessmentQuestion, onCompleted: (List<Int>) -> Unit) {
    val options = question.displayedOptions ?: question.options
    val min = question.min
    val max = question.max
    val selected = remember { mutableStateListOf<Int>() }

    Column(Modifier.fillMaxWidth().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, min, if (max != 1) max else null, Modifier.padding(bottom = Dimens.cardSpacing))

        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index in selected
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (max == 1) {
                            onCompleted(listOf(index))
                        } else {
                            if (isSelected) selected.remove(index)
                            else if (selected.size < max) selected.add(index)
                        }
                    },
                    gradient = if (isSelected) Clear30Gradients.clear30 else null,
                ) {
                    SmallText(option, color = if (isSelected) Color.White else Clear30Colors.text)
                }
            }
        }

        if (max > 1 && selected.size >= min) {
            DefaultButton("Next", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                onCompleted(selected.toList())
            }
        }
    }
}

