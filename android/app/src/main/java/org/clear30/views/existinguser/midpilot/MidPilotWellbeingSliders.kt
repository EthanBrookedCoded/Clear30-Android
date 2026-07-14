package org.clear30.views.existinguser.midpilot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.newuser.assessment.GradientSlider
import org.clear30.views.newuser.assessment.QuestionCard
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * MidPilotWellbeingSliders — port of MidPilotWellbeingSliders.swift: three 1-10
 * gradient sliders (sleep / anxiety / focus) with a live value readout and
 * "Much worse / Much better" endpoints, then Next.
 */
@Composable
fun MidPilotWellbeingSliders(onCompleted: (sleep: Int, anxiety: Int, focus: Int) -> Unit) {
    var sleep by remember { mutableFloatStateOf(5f) }
    var anxiety by remember { mutableFloatStateOf(5f) }
    var focus by remember { mutableFloatStateOf(5f) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            QuestionCard(
                "", "How Has Your Wellbeing Changed?",
                modifier = Modifier.padding(bottom = Dimens.cardSpacing * 2),
            )
            WellbeingSlider("😴 Sleep Quality", sleep, { sleep = it })
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
            WellbeingSlider("😰 Anxiety Levels", anxiety, { anxiety = it })
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
            WellbeingSlider("📚 Academic Focus", focus, { focus = it })
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
        }
        StretchedButton(
            "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.padding(vertical = Dimens.cardSpacing),
        ) {
            onCompleted(sleep.roundToInt(), anxiety.roundToInt(), focus.roundToInt())
        }
    }
}

@Composable
private fun WellbeingSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val display = value.roundToInt().coerceIn(1, 10)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing / 2)) {
            SmallText(label, Modifier.weight(1f))
            Heading3("$display")
        }
        GradientSlider(
            value = value,
            valueRange = 1f..10f,
            onValueChange = { new ->
                if (new.roundToInt().coerceIn(1, 10) != display) Haptics.mediumImpact()
                onChange(new)
            },
            onValueChangeFinished = { onChange(value.roundToInt().coerceIn(1, 10).toFloat()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 4),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            SmallText("Much worse", color = Clear30Colors.text.copy(alpha = 0.5f))
            SmallText("Much better", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}
