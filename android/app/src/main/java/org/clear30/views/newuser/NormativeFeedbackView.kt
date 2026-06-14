package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.ProgramNormativeFeedback
import org.clear30.data.model.ProgramNormativeFeedbackNumber
import org.clear30.data.model.ProgramNormativeFeedbackScore
import org.clear30.data.model.ProgramNormativeFeedbackTextSection
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * NormativeFeedbackView — ported from the iOS feedback display. Renders the
 * three feedback sections of the user's [ProgramNormativeFeedback]:
 *
 *   • Score cards — gradient cards with the 0-100 score + insight
 *   • Amount cards — large number + label gradient cards
 *   • Text sections — collapsible heading + body pairs
 *
 * Renders an empty-state explainer when the feedback hasn't arrived yet (the
 * `program_get_feedback` RPC fires after assessment submit; on the first paint
 * we show a "loading" hint instead of blanks).
 */
@Composable
fun NormativeFeedbackView(
    feedback: ProgramNormativeFeedback?,
    userInfo: UserInfo,
    onNext: () -> Unit,
) {
    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedFeedback)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Heading1("Your feedback")
        SmallText(
            "Here's what we're seeing based on your answers. None of it is a diagnosis — it's a starting point.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        if (feedback == null) {
            Spacer(Modifier.padding(top = Dimens.cardSpacing))
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText("Calculating your feedback…", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            return@Column
        }

        if (feedback.scoreCards.isNotEmpty()) {
            Heading2("Scores")
            feedback.scoreCards.forEach { ScoreCard(it) }
        }

        if (feedback.amountCards.isNotEmpty()) {
            Heading2("By the numbers")
            feedback.amountCards.forEach { AmountCard(it) }
        }

        feedback.sections.forEach { section -> TextSection(section) }

        Spacer(Modifier.padding(top = Dimens.cardSpacing))

        StretchedButton(
            "Continue",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
        ) { onNext() }
    }
}

@Composable
private fun ScoreCard(card: ProgramNormativeFeedbackScore) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = gradientFor(card.color1, card.color2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            // Score chip
            Box(
                Modifier.clip(RoundedCornerShape(Dimens.cornerRadius)).background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                GiganticText("${card.score.toInt()}", color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Heading3(card.title, color = Color.White)
                SmallText(card.insight, color = Color.White)
            }
        }
        // 0..100 bar
        Spacer(Modifier.padding(top = Dimens.cardSpacing / 2))
        ProgressBar(card.score.toFloat().coerceIn(0f, 100f) / 100f)
    }
}

@Composable
private fun AmountCard(card: ProgramNormativeFeedbackNumber) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = gradientFor(card.color1, card.color2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            GiganticText(card.amount, color = Color.White)
            Column(Modifier.weight(1f)) {
                Heading3(card.title, color = Color.White)
                TinyText(card.subtitle, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun TextSection(section: ProgramNormativeFeedbackTextSection) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading2(section.sectionTitle)
        section.content.forEach { entry ->
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Heading3(entry.title)
                    SmallText(entry.subtitle)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.White),
        )
    }
}

private fun gradientFor(c1: String, c2: String): Brush = Clear30Gradients.linear(
    listOf(colorFromHex(c1), colorFromHex(c2)),
    Clear30Gradients.bottomLeading,
    Clear30Gradients.topTrailing,
)
