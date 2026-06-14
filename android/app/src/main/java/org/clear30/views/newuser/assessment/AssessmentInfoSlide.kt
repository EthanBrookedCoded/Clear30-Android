package org.clear30.views.newuser.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.Heading3Markdown
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextMarkdown
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * AssessmentInfoSlide — ported from AssessmentInfoSlide2.swift. Dispatches on
 * the slide's named [AssessmentInfoDataID] (when set) to a richer presentation;
 * unnamed info slides fall back to a clean title + body layout.
 *
 * Each branch fires the corresponding analytic (PainPoint / DreamOutcome /
 * SocialProof / Credibility / WhatBringsYouHere mirror the iOS variants) and
 * advances the assessment via [onContinue] when the user taps the primary CTA.
 */
@Composable
fun AssessmentInfoSlide(
    data: AssessmentInfoData,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
) {
    when (data.id) {
        AssessmentInfoDataID.welcomeTyping -> WelcomeTypingSlide(data, onPrimary)
        AssessmentInfoDataID.socialProof -> SocialProofSlide(data, onPrimary)
        AssessmentInfoDataID.credibility -> CredibilitySlide(data, onPrimary)
        AssessmentInfoDataID.greeting -> GreetingSlide(data, onPrimary)
        AssessmentInfoDataID.whereYouAre,
        AssessmentInfoDataID.whereYouGoing -> WhereSlide(data, onPrimary)
        AssessmentInfoDataID.breakReasonAffirmation,
        AssessmentInfoDataID.goalsAffirmation -> AffirmationSlide(data, onPrimary)
        AssessmentInfoDataID.clear30Recommendation -> DreamOutcomeSlide(data, onPrimary)
        AssessmentInfoDataID.currentUseSummary -> PainPointSlide(data, onPrimary)
        AssessmentInfoDataID.checkIn,
        AssessmentInfoDataID.nameContext,
        AssessmentInfoDataID.halftime,
        AssessmentInfoDataID.features,
        AssessmentInfoDataID.expectations -> NarrativeSlide(data, onPrimary, onSecondary)
        null -> NarrativeSlide(data, onPrimary, onSecondary)
        else -> NarrativeSlide(data, onPrimary, onSecondary)
    }
}

/**
 * PainPoint — the normative "You vs Others" comparison (currentUseSummary). Two
 * horizontal bars: Others (clear30) filled to (100−P)%, You (red) filled to P%,
 * with "higher than P% of other people." Ported from AssessmentPainPoint.swift
 * (first stage — the method/spend/impact cards are a follow-up).
 */
@Composable
private fun PainPointSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    val percentile = (data.painPointPercentile ?: 70).coerceIn(0, 100)
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                ComparisonBar("Others", Clear30Gradients.clear30, maxOf(0.1f, (100 - percentile) / 100f))
                ComparisonBar("You", Clear30Gradients.red, percentile / 100f)
            }
        }
        Heading3Markdown(
            "Your cannabis use is higher than **$percentile%** of other people.",
            modifier = Modifier.padding(top = Dimens.cardSpacing),
        )
        Spacer(Modifier.weight(0.6f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/** One labeled comparison bar — a dim full-width track + a brand-filled portion. */
@Composable
private fun ComparisonBar(label: String, brush: Brush, fraction: Float) {
    val shape = RoundedCornerShape(Dimens.cornerRadius / 2)
    Box(Modifier.fillMaxWidth().height(25.dp)) {
        Box(Modifier.fillMaxSize().clip(shape).alpha(0.5f).background(brush))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(brush))
        Box(Modifier.fillMaxSize().padding(start = Dimens.cardSpacing / 2), contentAlignment = Alignment.CenterStart) {
            TinyText(label, color = Color.White)
        }
    }
}

/** Welcome slide — types the title + body out, then auto-forwards (welcomeTyping). */
@Composable
private fun WelcomeTypingSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    TypingTextSlide(
        lines = listOf(
            TypingLine(data.title, TypingTextStyle.Heading3),
            TypingLine(data.body, TypingTextStyle.Small, alpha = 0.75f),
        ),
        centerText = true,
        onComplete = onPrimary,
    )
}

/**
 * Standard narrative slide — eyebrow + headline + body + primary CTA. Used for
 * the unnamed slides and the "halftime / features / expectations" beats, which
 * are all simple read-and-continue moments.
 */
@Composable
private fun NarrativeSlide(data: AssessmentInfoData, onPrimary: () -> Unit, onSecondary: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Spacer(Modifier.size(Dimens.cardSpacing))
        Heading1(data.title)
        if (data.body.isNotBlank()) {
            SmallTextMarkdown(data.body, color = Clear30Colors.text.copy(alpha = 0.75f))
        }
        data.systemImageName?.let { sym ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        sfSymbol(sym),
                        contentDescription = null,
                        tint = Clear30Colors.green,
                        modifier = Modifier.size(data.systemImageSize.dp.coerceAtLeast(48.dp)),
                    )
                }
        }
        Spacer(Modifier.weight(1f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
        if (onSecondary != null && data.secondaryButtonText != null) {
            DefaultButton(
                data.secondaryButtonText!!,
                gradient = null,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSecondary,
            )
        }
    }
}

/**
 * SocialProof — large rating + member count + testimonial scrim. Mirrors the
 * iOS "join thousands" beat shown after the early questions.
 */
@Composable
private fun SocialProofSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Heading1(data.title.ifBlank { "30,000+ members" }, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(20.dp))
                }
                SmallText("  4.9 · App Store", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        if (data.body.isNotBlank()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                    SmallTextMarkdown(data.body, color = Clear30Colors.text)
                }
        }
        Spacer(Modifier.weight(0.6f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/**
 * Credibility — partner logos / org affiliations. iOS rendered NIH, UMich,
 * Harvard, MLB tiles. We stub them as labeled cards until SVG import lands;
 * the real logos drop in by swapping the `Box` placeholders for Image().
 */
@Composable
private fun CredibilitySlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    val partners = listOf("NIH", "U Michigan", "Harvard", "MLB")
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Spacer(Modifier.size(Dimens.cardSpacing))
        Heading1(data.title.ifBlank { "Built with researchers" })
        if (data.body.isNotBlank()) {
            SmallTextMarkdown(data.body, color = Clear30Colors.text.copy(alpha = 0.75f))
        }
        Spacer(Modifier.size(Dimens.cardSpacing))
        partners.chunked(2).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    row.forEach { name ->
                        Clear30Card(modifier = Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    sfSymbol("checkmark.seal.fill"),
                                    contentDescription = null,
                                    tint = Clear30Colors.green,
                                    modifier = Modifier.size(32.dp).padding(bottom = 4.dp),
                                )
                                SmallText(name, color = Clear30Colors.text)
                            }
                        }
                    }
                }
        }
        Spacer(Modifier.weight(1f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/** Affirmation slide — a centered emoji + line of encouraging copy. */
@Composable
private fun AffirmationSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        GiganticText("💚")
        Heading2(
                data.title.ifBlank { "We see you" },
                color = Clear30Colors.text,
            )
        if (data.body.isNotBlank()) {
            SmallTextMarkdown(data.body, color = Clear30Colors.text.copy(alpha = 0.75f))
        }
        Spacer(Modifier.weight(0.6f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/** Greeting — large hello + name context. */
@Composable
private fun GreetingSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Spacer(Modifier.weight(0.4f))
        GiganticText("👋")
        Heading1(data.title.ifBlank { "Welcome" })
        if (data.body.isNotBlank()) {
            SmallTextMarkdown(data.body, color = Clear30Colors.text.copy(alpha = 0.75f))
        }
        Spacer(Modifier.weight(0.6f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/** "Where you are / where you're going" — two-row contrast card. */
@Composable
private fun WhereSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    val isFuture = data.id == AssessmentInfoDataID.whereYouGoing
    val gradient: Brush = if (isFuture) Clear30Gradients.clear30 else Clear30Gradients.gray
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Spacer(Modifier.size(Dimens.cardSpacing))
        Heading1(data.title)
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = gradient) {
                // White on the green "future" card; dark on the light-gray "now" card.
                SmallTextMarkdown(data.body, color = if (isFuture) Color.White else Clear30Colors.text)
            }
        Spacer(Modifier.weight(1f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/**
 * DreamOutcome — the "30 Days From Now" projection (clear30Recommendation): a green
 * card with one outcome tile per chosen break reason + a monthly-savings tile.
 * Ported from AssessmentDreamOutcome.swift.
 */
@Composable
private fun DreamOutcomeSlide(data: AssessmentInfoData, onPrimary: () -> Unit) {
    val nouns = data.dreamOutcomeNouns.orEmpty()
    val savings = data.dreamOutcomeSavings ?: 0
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.3f))
        Heading1(data.title.ifBlank { "Your Clear30" })
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SmallText("30 Days From Now", color = Color.White)
                if (nouns.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        nouns.take(3).forEach { noun -> OutcomeTile(noun, Modifier.weight(1f)) }
                    }
                }
                if (savings > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Dimens.cornerRadius))
                            .background(Color.White)
                            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                    ) {
                        SmallText("💰 +$" + savings, color = Clear30Colors.text)
                    }
                }
            }
        }
        Spacer(Modifier.weight(0.7f))
        StretchedButton(
            data.primaryButtonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
    }
}

/** One "future you" outcome tile — emoji over its noun (e.g. "💡" / "Mental Clarity"). */
@Composable
private fun OutcomeTile(noun: String, modifier: Modifier = Modifier) {
    val emoji = noun.substringBefore(' ')
    val label = noun.substringAfter(' ', "")
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Color.White)
            .padding(Dimens.cardSpacing / 2),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            Heading3(emoji)
            TinyText(label, color = Clear30Colors.text)
        }
    }
}
