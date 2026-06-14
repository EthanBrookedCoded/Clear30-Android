package org.clear30.views.newuser.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Free-text question renderer — ported from AssessmentInput.swift. Next is
 * enabled when optional or the field is non-blank; returns the trimmed string.
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
    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2)
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(placeholder) },
            singleLine = !multiLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        val enabled = optional || input.isNotBlank()
        DefaultButton("Next", gradient = if (enabled) Clear30Gradients.clear30 else null, modifier = Modifier.fillMaxWidth()) {
            if (enabled) onCompleted(input.trim())
        }
    }
}

/**
 * Slider question renderer — ported from AssessmentSlider.swift. Picks a value
 * among the option indices and returns the chosen index.
 */
@Composable
fun AssessmentSlider(question: ProgramAssessmentQuestion, valueLabel: String, onCompleted: (Int) -> Unit) {
    val options = question.displayedOptions ?: question.options
    if (options.isEmpty()) { onCompleted(0); return }
    var value by remember { mutableFloatStateOf(0f) }
    val index = value.toInt().coerceIn(0, options.lastIndex)

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        Heading1(if (valueLabel.isBlank()) options[index] else "${options[index]} $valueLabel")
        Spacer(Modifier.weight(1f))
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0f..options.lastIndex.toFloat().coerceAtLeast(0f),
            steps = (options.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
        DefaultButton("Next", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
            onCompleted(index)
        }
    }
}

/**
 * Spectrum renderer — ported from AssessmentSpectrum.swift. A stepped slider with
 * left/middle/right anchor labels; returns the selected step index.
 */
@Composable
fun AssessmentSpectrum(
    question: ProgramAssessmentQuestion,
    left: String,
    middle: String,
    right: String,
    onCompleted: (Int) -> Unit,
) {
    val steps = (question.displayedOptions ?: question.options).size.coerceAtLeast(2)
    var value by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        QuestionCard(question.prompt1, question.prompt2, modifier = Modifier.padding(bottom = Dimens.cardSpacing))
        Spacer(Modifier.weight(1f))
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0f..(steps - 1).toFloat(),
            steps = (steps - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding / 2), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallText(left)
            if (middle.isNotEmpty()) SmallText(middle)
            SmallText(right)
        }
        Spacer(Modifier.weight(1f))
        DefaultButton("Next", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
            onCompleted(value.toInt().coerceIn(0, steps - 1))
        }
    }
}
