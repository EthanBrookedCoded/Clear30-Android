package org.clear30.views.newuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * NormativeFeedbackView — 1:1 port of `NormativeFeedbackView.swift` (onboarding
 * path, where `action != nil`). A scroll of the user's [ProgramNormativeFeedback]:
 *
 *   • Heading2 "✨ Your Personalized Cannabis Snapshot" + subtitle
 *   • Score cards — gradient cards with a 0-100 `BreakProgressBar`, Low/High
 *     labels, and a collapsible insight ("Show more"/"Show less")
 *   • Amount cards — gradient cards with title / subtitle / big amount
 *   • Text sections — Heading3 section title + plain "strength / growth" cards
 *   • A trailing white `TextIconButton` ("Next" + arrow)
 *
 * The iOS TypingTextSlide intro and the onboarding `fadeOut` scroll mask are
 * omitted. // TODO(port): typing intro, scroll fade-out.
 *
 * Renders an empty-state explainer when the feedback hasn't arrived yet (the
 * `program_get_feedback` RPC fires after assessment submit).
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
        Modifier.fillMaxSize().systemBarsPadding().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Heading2("✨ Your Personalized Cannabis Snapshot")
            SmallText(
                "Based on your answers, here's where you're currently at:",
                modifier = Modifier.padding(top = Dimens.cardSpacing / 2, bottom = Dimens.cardSpacing),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )

            if (feedback == null) {
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    SmallText("Calculating your feedback…", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            } else {
                feedback.scoreCards.forEach { card ->
                    ProgressCard(card, Modifier.padding(bottom = Dimens.cardSpacing))
                }
                feedback.amountCards.forEach { card ->
                    AmountCard(card, Modifier.padding(bottom = Dimens.cardSpacing))
                }
                // iOS adds an extra cardSpacing below the amount cards before the sections.
                if (feedback.amountCards.isNotEmpty()) Spacer(Modifier.height(Dimens.cardSpacing))
                feedback.sections.forEach { section -> TextSection(section) }
            }
        }

        TextIconButton(text = "Next", icon = "arrow.right", onClick = onNext)
    }
}

/**
 * ProgressCard — gradient card: Heading3 title, a 0-100 `BreakProgressBar` showing
 * the "value%", Low/High labels, and a collapsible insight toggled by a "Show
 * more" / "Show less" white pill.
 */
@Composable
private fun ProgressCard(card: ProgramNormativeFeedbackScore, modifier: Modifier = Modifier) {
    var showMore by remember { mutableStateOf(true) }
    val value = card.score.toInt()
    Clear30Card(modifier = modifier.fillMaxWidth(), gradient = gradientFor(card.color1, card.color2)) {
        Column(Modifier.fillMaxWidth()) {
            Heading3(card.title, Modifier.padding(bottom = Dimens.cardSpacing / 2), color = Color.White)

            BreakProgressBar(value, 100, "$value%")

            Row(
                Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2),
            ) {
                SmallText("Low", color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                SmallText("High", color = Color.White.copy(alpha = 0.5f))
            }

            AnimatedVisibility(visible = !showMore) {
                SmallText(card.insight, color = Color.White)
            }

            ShowMoreButton(
                showMore = showMore,
                modifier = Modifier.padding(top = if (showMore) 0.dp else Dimens.cardSpacing),
            ) { showMore = !showMore }
        }
    }
}

/** Show more / less pill — white text on a white@0.25 chip with a chevron. */
@Composable
private fun ShowMoreButton(showMore: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
            TinyText(if (showMore) "Show more" else "Show less", color = Color.White, maxLines = 1)
            Icon(
                sfSymbol(if (showMore) "chevron.down" else "chevron.up"),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/**
 * BreakProgressBar — 1:1 port of the iOS `BreakProgressBar`: a 40dp white rounded
 * bar over a 0.25-opacity white track, filled to `currentValue / maxValue`, with
 * the override text overlaid leading. (iOS masks the gradient through the text;
 * here the text sits on the white fill in the brand text color for the same read.)
 */
@Composable
private fun BreakProgressBar(currentValue: Int, maxValue: Int, textOverride: String) {
    val pct = (currentValue.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    val corner = Dimens.cornerRadius * 0.75f
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(corner)),
    ) {
        // Track (white @ 0.25)
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
        // Fill (white, to percentage)
        Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(Color.White))
        // Overlaid value, leading (iOS .padding(.leading, 11)).
        DefaultText(
            textOverride,
            Modifier.align(Alignment.CenterStart).padding(start = 11.dp),
            color = Clear30Colors.text,
        )
    }
}

/** AmountCard — gradient card: Heading3 title, half-opacity subtitle, big Heading2 amount. */
@Composable
private fun AmountCard(card: ProgramNormativeFeedbackNumber, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier.fillMaxWidth(), gradient = gradientFor(card.color1, card.color2)) {
        Column(Modifier.fillMaxWidth()) {
            Heading3(card.title, Modifier.padding(bottom = Dimens.cardSpacing / 2), color = Color.White)
            SmallText(
                card.subtitle,
                Modifier.padding(bottom = Dimens.cardSpacing / 4),
                color = Color.White.copy(alpha = 0.5f),
            )
            Heading2(card.amount, color = Color.White)
        }
    }
}

/** A feedback text section — Heading3 title then a list of plain "strength / growth" cards. */
@Composable
private fun TextSection(section: ProgramNormativeFeedbackTextSection) {
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing)) {
        Heading3(section.sectionTitle, Modifier.padding(bottom = Dimens.cardSpacing))
        section.content.forEach { entry ->
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing)) {
                Column(Modifier.fillMaxWidth()) {
                    SmallText(entry.title, Modifier.padding(bottom = Dimens.cardSpacing / 4))
                    TinyText(entry.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * TextIconButton — 1:1 port of the iOS `TextIconButton`: a full-width off-white
 * card pill with centered dark text + a trailing icon.
 */
@Composable
private fun TextIconButton(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.fillMaxWidth().pressScale(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text.isNotEmpty()) SmallText(text, maxLines = 1)
            if (icon.isNotBlank()) {
                Icon(sfSymbol(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(12.dp))
            }
        }
    }
}

private fun gradientFor(c1: String, c2: String): Brush = Clear30Gradients.linear(
    listOf(colorFromHex(c1), colorFromHex(c2)),
    Clear30Gradients.bottomLeading,
    Clear30Gradients.topTrailing,
)
