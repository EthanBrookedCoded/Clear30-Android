package org.clear30.views.newuser.assessment

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.clear30.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.clear30.data.model.BreakReasonType
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * AssessmentDreamOutcome — the "30 Days From Now" projection slide. Ported 1:1
 * from AssessmentDreamOutcome2.swift (the variant the dispatcher actually wires
 * up), minus the `longTermGoal` card the live iOS dispatcher does not pass.
 *
 * IMPORTANT — background/content color: the parent info slide sets
 * `overrideBackgroundGradient: false`, so this view renders on a WHITE background
 * with DARK content. The parent provides `LocalContentColor` = dark, so the
 * un-carded labels (the intro subtitle, "How we're going to get you there") are
 * dimmed dark text, NOT white. White is only used INSIDE the gradient cards,
 * where [Clear30Card] flips `LocalContentColor` to white.
 *
 * Layout (matches the iOS VStack order, spacing 0 with explicit padding):
 *   1. intro       — name (dark Heading3) + dimmed "30 Days From Now"
 *   2. goalsCard   — clear30 gradient: "Goals Hit" + a row of outcome tiles
 *                    (one per break reason: emoji over its short noun)
 *   3. moneyCard   — meditation gradient: "Money Saved" + a white savings tile
 *                    (pig + $amount) + a Day/Week/Month/Year segmented control
 *   4. emojiCards  — "How we're going to get you there:" + three EmojiCards
 */
private enum class MoneySavedType { Day, Week, Month, Year }

@Composable
fun AssessmentDreamOutcome(
    name: String,
    breakReasons: List<BreakReasonType>,
    weeklySpend: Int,
    modifier: Modifier = Modifier,
) {
    val monthlySaved = (weeklySpend.toDouble() * 4.0).toInt()

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            // Parent AssessmentInfoSlide already applies Dimens.horizontalPadding
            // (25dp); omit the extra scrollShadowFix so we don't double-inset.
            .padding(vertical = Dimens.headingTopPadding),
    ) {
        // --- one green hero card (matches iOS): centered name + "30 Days From Now",
        // the outcome tiles, and a single "+$<monthly>" savings card inside it ---
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Heading3(name, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    SmallText(
                        "30 Days From Now",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    breakReasons.forEach { reason ->
                        // weight + fillMaxHeight equalizes tile heights (iOS maxHeight: .infinity).
                        OutcomeTile(reason, Modifier.weight(1f).fillMaxHeight())
                    }
                }

                // White savings card: money-bag + the monthly amount (iOS "💰 +$200").
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
                    ) {
                        SmallText("💰")
                        SmallText("+$$monthlySaved")
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.cardSpacing * 2))

        // --- emojiCards (on white background, dark content) ---
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            SmallText("How we’re going to get you there:", color = LocalContentColor.current.copy(alpha = 0.5f))

            EmojiCard("😌", "We do the work.", "Fall into our fail-proof program and actually enjoy the process.")
            EmojiCard("🫂", "Proven by 70,000+", "The same science-backed system has led thousands just like you to their goals.")
            EmojiCard("🗓️", "Feel it fast.", "Most users report major improvements in just the first 3 days.")
        }
    }
}

/**
 * One "Goals Hit" tile — emoji over its short noun, in a white card.
 * iOS: `Heading3(emoji)` over `TinyText(noun)`, centered, vertical spacing
 * cardSpacing/2, horizontal padding cardSpacing/2, vertical padding cardSpacing,
 * `CardStyle(padding: false)`.
 */
@Composable
private fun OutcomeTile(reason: BreakReasonType, modifier: Modifier = Modifier) {
    val noun = reason.asNoun
    val emoji = noun.substringBefore(' ', "🤩")
    val label = noun.substringAfter(' ', "").trim()
    Clear30Card(modifier = modifier, padding = false) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterVertically),
        ) {
            Heading3(emoji)
            TinyText(
                label,
                modifier = Modifier.fillMaxWidth().alpha(0.5f),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Day/Week/Month/Year toggle pill — ports iOS `MoneyTypeButton`, which uses
 * `TinyTextButton(background: true, stretch: true)`:
 *   - corner radius 12 (TinyTextButton's CardStyle, NOT Dimens.cornerRadius)
 *   - inner padding horizontal 10 / vertical 5
 *   - selected: white fill, meditation1 text; else white@0.25 fill, white text
 *   - TinyTextButton's whole-button opacity is 0.7 (rounded to 0.75 per the
 *     project's allowed-alpha rule).
 */
@Composable
private fun MoneyTypeButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color.White else Color.White.copy(alpha = 0.25f)
    val fg = if (selected) Clear30Colors.meditation1 else Color.White
    Box(
        modifier
            // iOS TinyTextButton applies opacity 0.7 to the WHOLE pill (fill + text);
            // rounded to 0.75 per the project's allowed-alpha rule.
            .alpha(0.75f)
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        TinyText(title, color = fg, maxLines = 1)
    }
}

/**
 * RewardPigWithDollars — the small money illustration beside the savings figure.
 * iOS layers `.singleDollar` / `.multiDollar` / `.coins` images around a happy
 * pig (`.pigHappy`), revealing more as the projection grows. Those brand vector
 * assets are not yet imported into Android, so this renders an emoji equivalent
 * (more coins/cash shown as the savings window widens) with a crossfade.
 */
// TODO(port): replace emoji with the imported singleDollar / multiDollar / coins
//             / pigHappy vector assets + the SwiftUI push transitions/offsets.
@Composable
private fun RewardPigWithDollars(
    showSingleDollar: Boolean,
    showMultiDollar: Boolean,
    showCoins: Boolean,
    modifier: Modifier = Modifier,
) {
    // Reveal more money as the projection window widens (iOS layers the dollar
    // assets around the pig); imported from the iOS Rewards PNGs.
    val res = when {
        showCoins -> R.drawable.coins
        showMultiDollar -> R.drawable.multi_dollar
        showSingleDollar -> R.drawable.single_dollar
        else -> R.drawable.pig_happy
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Crossfade(targetState = res, label = "rewardPig") { r ->
            Image(
                painter = painterResource(r),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * "How we're going to get you there" item — gradient emoji chip + bold-ish title
 * row, then a dimmed subtitle, all inside a white card (iOS `EmojiCard`).
 * The subtitle inherits the card's dark text and is dimmed to 0.5.
 */
@Composable
private fun EmojiCard(emoji: String, title: String, subtitle: String) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
                        .background(Clear30Gradients.clear30),
                    contentAlignment = Alignment.Center,
                ) {
                    DefaultText(emoji)
                }
                SmallText(title, modifier = Modifier.fillMaxWidth())
            }
            TinyText(subtitle, modifier = Modifier.fillMaxWidth(), color = LocalContentColor.current.copy(alpha = 0.5f))
        }
    }
}
