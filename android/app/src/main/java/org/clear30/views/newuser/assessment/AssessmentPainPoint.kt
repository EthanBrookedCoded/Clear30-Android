package org.clear30.views.newuser.assessment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.NormativeData
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.getSingleOption
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.markdownBold
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend
import org.clear30.views.theme.colorFromHex

/**
 * AssessmentPainPoint — a faithful 1:1 port of `AssessmentPainPoint2.swift` (the
 * variant actually used by the `currentUseSummary` slide).
 *
 * CRITICAL: the iOS slide sets `overrideBackgroundGradient: false`, so it renders
 * on a WHITE background with DARK content. The parent ([AllAssessment]) supplies
 * `LocalContentColor = Clear30Colors.text` here, so the typed text composables go
 * dark automatically; only the content INSIDE the red gradient hero card is white
 * (provided by [Clear30Card]).
 *
 * The reveal is staged exactly like iOS:
 *   1. `!showCard && !showingLoss` — just the "You vs Others" comparison card on a
 *      bare (transparent, shadowless) wrapper, centered "higher than X%" copy, and
 *      a "Go On" button.
 *   2. `showingLoss && !showCard` — adds the "Losing $/week·month·year" card and
 *      swaps the copy to the yearly-spend projection; button becomes "Anything Else?".
 *   3. `showCard` — wraps everything in the RED gradient hero card (with the soft
 *      drop shadow), adds the name heading + age×help-harm "Impact on Life" card
 *      (with a WORKING Show more / Show less toggle), and replaces the button with
 *      the national-datasets citation footnote.
 *
 * iOS drives the final "Next" through `viewModel.showNextButton`; in the Android
 * dispatcher that parent button is owned by [AssessmentInfoSlide], so this view
 * only owns the staged "Go On" / "Anything Else?" button (stages 1–2). Once
 * `showCard` is reached the parent's primary button advances the flow.
 *
 * // TODO(port): the dispatcher renders [AssessmentInfoSlide]'s primary "Next"
 * pill unconditionally for this slide (its `primaryButtonText` defaults to
 * "Next"). To match iOS's `showNextButton` gating — hidden until `showCard`, and
 * suppressing our own staged button afterwards — the dispatcher needs to set
 * `primaryButtonText = ""` for `currentUseSummary` and let this view own the CTA,
 * or expose a `showNextButton`-style hook. That lives outside this file.
 */
@Composable
fun AssessmentPainPoint(
    name: String,
    responses: Map<String, ProgramAssessmentResponse>,
    modifier: Modifier = Modifier,
) {
    val normativeData = normativeDataFor(responses)
    val weeklySpend = weeklySpendFor(responses)

    var showCard by remember { mutableStateOf(false) }
    var showMoreImpact by remember { mutableStateOf(false) }

    // iOS showNextButton gating: the parent's "Next" stays hidden while our own
    // "Go On" / "Anything Else?" buttons drive stages 1–2; once the hero card is
    // revealed (final stage) the parent CTA advances the flow.
    val infoController = LocalInfoSlideController.current
    androidx.compose.runtime.SideEffect { infoController.showNextButton = showCard }

    val helpHarmEmoji = responses[AssessmentQuestionID.HELP_HARM.raw]?.let { displayedOption(it) }
    val feedback = generateAgeHelpHarmFeedback(responses)

    Column(modifier.fillMaxSize()) {
        // Scrollable, vertically-centered card region (iOS GeometryReader + ScrollView
        // with Spacer()s above/below). `weight(1f)` lets the button/footnote sit below.
        // NOTE: the parent dispatcher ([AssessmentInfoSlide]) already insets the custom
        // view by Dimens.horizontalPadding, so we do NOT re-apply it here.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            // Outer wrapper: bare until `showCard`, then the red gradient hero card
            // with the soft drop shadow (iOS CardStyle gradient: redGradient).
            val outer = if (showCard) {
                Modifier.cardStyle(
                    color = Color.Transparent,
                    shadowColor = Clear30Colors.shadow,
                    gradient = Clear30Gradients.red,
                    padding = true,
                )
            } else {
                Modifier
            }

            // The red card provides white content; before that the parent's dark
            // content color is inherited. Wrap so children pick up the right color.
            CardColorProvider(onRedCard = showCard) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(outer)
                        .animateContentSize(),
                ) {
                    // Name heading — only in the final hero-card state.
                    if (showCard) {
                        Heading3(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Dimens.cardSpacing),
                        )
                    }

                    // Usage comparison card. Extra horizontal inset (cardSpacing*2)
                    // until it lives inside the hero card, where it goes flush.
                    Clear30Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (showCard) 0.dp else Dimens.cardSpacing * 2)
                            .padding(vertical = Dimens.cardSpacing),
                    ) {
                        NormativeDataView(normativeData)
                    }

                    if (!showCard) {
                        // Centered headline (iOS Heading3WithLinks +
                        // multilineTextAlignment(.center)); renders the `__bold__`
                        // emphasis and per-line centers. // TODO(port): numericText
                        // content transition on the showingLoss swap.
                        CenteredHeadline(
                            text = painPointText(false, weeklySpend, normativeData),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Dimens.cardSpacing * 2),
                        )
                    } else if (helpHarmEmoji != null && feedback != null) {
                        // Impact-on-life card with the working Show more / Show less toggle.
                        Clear30Card(modifier = Modifier.fillMaxWidth()) {
                            ImpactOnLifeView(
                                emoji = helpHarmEmoji,
                                title = feedback.first,
                                body = feedback.second,
                                showMore = showMoreImpact,
                                onToggle = { showMoreImpact = !showMoreImpact },
                            )
                        }
                    }
                }
            }
        }

        if (!showCard) {
            // Stage-1 "Go On" button (iOS TextIconButton: a white card pill with
            // centered dark text + trailing arrow) — advances to the red hero card.
            PainPointButton(
                text = "Go On",
                // Parent already applies horizontalPadding; only vertical here.
                modifier = Modifier.padding(vertical = Dimens.cardSpacing),
                onClick = { showCard = true },
            )
        } else {
            // Citation footnote replaces the button once the hero card is revealed —
            // iOS MiniTextWithLinks: the dataset / publication / survey sources are
            // tappable markdown links. Parent already applies horizontalPadding.
            Box(
                Modifier
                    .fillMaxWidth()
                    .alpha(0.5f)
                    .padding(vertical = Dimens.cardSpacing),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    linkedText(
                        "Estimated from [national datasets](https://nap.nationalacademies.org/read/27766/chapter/5#:~:text=prevalence%20fell%20from%2015,fold%20increase), " +
                            "[publications](https://newfrontierdata.com/cannabis-insights/survey-more-americans-than-not-have-consumed-cannabis/#:~:text=A%20majority%20%2853,doing%20so%20in%20the%20future),\n" +
                            "and [surveys](https://www.canada.ca/en/health-canada/services/drugs-medication/cannabis/research-data/canadian-cannabis-survey-2022-summary.html#:~:text=Figure%207%3A%20Frequency%20of%20cannabis,7%207%20Daily%2019%2018) on cannabis use.",
                    ),
                    color = LocalContentColor.current,
                    fontFamily = Lexend,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Subviews
 * ------------------------------------------------------------------------- */

/** iOS comparisonBarHeight = 25. */
private val comparisonBarHeight = 25.dp

/**
 * Provides the right [LocalContentColor] for the children that live OUTSIDE a
 * [Clear30Card] (the name heading + headline copy). On the red hero card they are
 * white; otherwise they inherit the parent's dark content color.
 */
@Composable
private fun CardColorProvider(onRedCard: Boolean, content: @Composable () -> Unit) {
    if (onRedCard) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContentColor provides Color.White,
            content = content,
        )
    } else {
        content()
    }
}

/**
 * Centered Heading3-with-links headline (iOS Heading3WithLinks +
 * multilineTextAlignment(.center)). Mirrors the Heading3 type ramp (22sp Medium,
 * 1.3 line height) and renders `__bold__` emphasis via the shared [markdownBold]
 * builder; inherits the parent's content color.
 */
@Composable
private fun CenteredHeadline(text: String, modifier: Modifier = Modifier) {
    Text(
        markdownBold(text),
        modifier = modifier,
        color = LocalContentColor.current,
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 22.sp * 1.3f,
        textAlign = TextAlign.Center,
    )
}

/** The "Using" — Others / You comparison bars (iOS normativeDataView). */
@Composable
private fun NormativeDataView(normativeData: NormativeData) {
    Column(Modifier.fillMaxWidth()) {
        TinyText(
            "Using",
            modifier = Modifier
                .alpha(0.5f)
                .padding(bottom = Dimens.cardSpacing / 2),
        )

        // Others — clear30 gradient, filled to (100 - percentile)% (min 10%).
        ComparisonBar(
            label = "Others",
            brush = Clear30Gradients.clear30,
            fraction = maxOf(0.1f, (100 - normativeData.percentile_more_than) / 100f),
            modifier = Modifier.padding(bottom = Dimens.cardSpacing),
        )

        // You — red gradient, filled to percentile%.
        ComparisonBar(
            label = "You",
            brush = Clear30Gradients.red,
            fraction = normativeData.percentile_more_than / 100f,
        )
    }
}

/** A dim full-width track (alpha 0.5) + a brand-filled portion, white label inset. */
@Composable
private fun ComparisonBar(
    label: String,
    brush: Brush,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimens.cornerRadius / 2)
    Box(
        modifier
            .fillMaxWidth()
            .height(comparisonBarHeight),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .alpha(0.5f)
                .background(brush),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(brush),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = Dimens.cardSpacing / 2),
            contentAlignment = Alignment.CenterStart,
        ) {
            TinyText(label, color = Color.White)
        }
    }
}

/** The "Losing" weekly/monthly/yearly money projection (iOS moneyLossView). */
@Composable
private fun MoneyLossView(weeklySpend: Int?) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        TinyText("Losing", modifier = Modifier.alpha(0.5f))

        if (weeklySpend != null) {
            val monthlySpend = weeklySpend * 4
            val yearlySpend = weeklySpend * 52

            MoneyRow("\$$weeklySpend", " per week", colorFromHex("#FCB334"))
            MoneyRow("\$$monthlySpend", " per month", colorFromHex("#FD6B20"))
            MoneyRow("\$$yearlySpend", " per year", colorFromHex("#FF0000"))
        }
    }
}

@Composable
private fun MoneyRow(amount: String, suffix: String, amountColor: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Heading3(amount, color = amountColor)
        SmallText(suffix, modifier = Modifier.alpha(0.5f))
    }
}

/**
 * The "Impact on Life" card — emoji + title, an expandable body, and a working
 * Show more / Show less pill (iOS impactOnLifeCard).
 */
@Composable
private fun ImpactOnLifeView(
    emoji: String,
    title: String,
    body: String,
    showMore: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        TinyText("Impact on Life", modifier = Modifier.alpha(0.5f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            DefaultText(emoji)
            SmallText(title, modifier = Modifier.fillMaxWidth())
        }

        // Expanded body — fades in/out with the toggle.
        AnimatedVisibility(visible = showMore, enter = fadeIn(), exit = fadeOut()) {
            TinyText(
                body,
                modifier = Modifier
                    .alpha(0.5f)
                    .padding(top = Dimens.cardSpacing / 4),
            )
        }

        // Show more / Show less pill — iOS TinyTextButton(stretch: true) with the
        // .primary @ 0.5 foreground over a .primary @ 0.1 background.
        ShowMoreButton(
            showMore = showMore,
            modifier = Modifier.padding(top = if (showMore) 0.dp else Dimens.cardSpacing / 4),
            onClick = onToggle,
        )
    }
}

/** Stretched Show more / Show less control with a working toggle. */
@Composable
private fun ShowMoreButton(showMore: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = LocalContentColor.current.copy(alpha = 0.5f)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
            .background(LocalContentColor.current.copy(alpha = 0.1f))
            .pressScale(onClick = onClick)
            .padding(vertical = Dimens.cardSpacing / 2),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            TinyText(if (showMore) "Show less" else "Show more", color = tint)
            Icon(
                sfSymbol(if (showMore) "chevron.up" else "chevron.down"),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.height(14.dp),
            )
        }
    }
}

/**
 * The staged primary button (iOS TextIconButton) — a full-width white card pill
 * with centered dark text + a trailing arrow.
 */
@Composable
private fun PainPointButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.fillMaxWidth().pressScale(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallText(text, maxLines = 1)
            Icon(
                sfSymbol("arrow.right"),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.height(12.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * Derived values (ported from AssessmentPainPoint2.swift)
 * ------------------------------------------------------------------------- */

/** The staged headline copy (iOS `text` computed property). */
private fun painPointText(showingLoss: Boolean, weeklySpend: Int?, normativeData: NormativeData): String =
    if (showingLoss && weeklySpend != null) {
        "Plus, you're on track to spend __$${weeklySpend * 52}__ on weed this year."
    } else {
        "Your cannabis use is higher than __${normativeData.percentile_more_than}%__ of other people."
    }

/**
 * Weekly spend — first selected option of the moneySpent question parsed to Int,
 * stripping the leading "$" symbol (iOS trims `.symbols`).
 */
private fun weeklySpendFor(responses: Map<String, ProgramAssessmentResponse>): Int? {
    val response = responses[AssessmentQuestionID.MONEY_SPENT.raw] ?: return null
    val index = response.responses.firstOrNull() ?: return null
    val raw = response.question.options.getOrNull(index) ?: return null
    return raw.filter { it.isDigit() }.toIntOrNull()
}

/**
 * The displayed help/harm option (emoji). iOS `getSingleOption()` defaults to the
 * DISPLAYED option text (the emoji), so map through displayedOptions.
 */
private fun displayedOption(response: ProgramAssessmentResponse): String? {
    val index = response.responses.firstOrNull() ?: return null
    return response.question.displayedOptions?.getOrNull(index)
        ?: response.question.options.getOrNull(index)
}

/**
 * NormativeData for the slide. The fixed top-level signature does not receive a
 * NormativeData (iOS passes it in), so derive it from the daysUsing response via
 * [NormativeData.weeklyUsageMapping] -> matching [NormativeData.defaultData] entry,
 * falling back to a weekly-user default. // TODO(port): thread the real backend
 * NormativeData (slide's painPointPercentile) through the dispatcher.
 */
private fun normativeDataFor(responses: Map<String, ProgramAssessmentResponse>): NormativeData {
    val fallback = NormativeData.defaultData.first { it.frequency == "1–2 times per week" }
    val response = responses[AssessmentQuestionID.DAYS_USING.raw] ?: return fallback
    val index = response.responses.firstOrNull() ?: return fallback
    // daysUsing options are "1".."7"; map the chosen day-count to a frequency label.
    val days = response.question.options.getOrNull(index)?.toIntOrNull() ?: return fallback
    val frequency = NormativeData.weeklyUsageMapping[days] ?: return fallback
    return NormativeData.defaultData.firstOrNull { it.frequency == frequency } ?: fallback
}

/**
 * Age × help-harm feedback lookup, ported 1:1 from
 * AssessmentPainPoint2.generateAgeHelpHarmFeedback(). Returns (title, body).
 *
 * iOS reads both responses via `getSingleOption(displayOptions: false)` (the raw
 * option strings), so we use the Kotlin [getSingleOption] extension which reads
 * `question.options`.
 */
private fun generateAgeHelpHarmFeedback(
    responses: Map<String, ProgramAssessmentResponse>,
): Pair<String, String>? {
    val ageResponse = responses[AssessmentQuestionID.AGE.raw]?.getSingleOption() ?: return null
    val helpHarmResponse = responses[AssessmentQuestionID.HELP_HARM.raw]?.getSingleOption() ?: return null

    val helpHarmMapping = mapOf(
        "Mostly helping more than harming" to 0,
        "Somewhat helping more than harming" to 1,
        "Equally Helping and Harming (but in different ways)" to 2,
        "Somewhat harming more than helping" to 3,
        "Mostly harming more than helping" to 4,
    )

    val ageMapping = mapOf(
        "18-20" to 0,
        "21-25" to 1,
        "26-30" to 2,
        "31-40" to 3,
        "41-50" to 4,
        "51-64" to 5,
        "65+" to 6,
    )

    val feedbackTable: List<List<Pair<String, String>>> = listOf(
        // 18-20
        listOf(
            "You're seeing benefits—and also recognizing that harm might quietly build without you realizing..." to
                "Especially at your age. Taking a break now is a wise move that shows real self-awareness and long-term thinking to avoid later harms that can be hard to see now. Very smart!",
            "You're seeing benefits—and also recognizing some potential harms." to
                "At this age, even small impacts can add up over time. Taking a break now is a wise move that shows real self-awareness and long-term thinking to avoid later harms that can be hard to see now. Very smart!",
            "You are seeing benefits and harm but in different ways." to
                "Breaking now helps you understand how weed may be shaping things without you realizing, especially at your age. A break now gives you that power to see what is really happening.",
            "You're recognizing harm is starting to outpace benefits." to
                "At your age, heavy weed use can have lasting impacts on your brain without you realizing it so a break really shows wisdom and courage to be your best self!",
            "You're recognizing the harm of weed is outpacing benefits." to
                "At your age, harms can have lasting impacts on your brain so a break really shows wisdom and courage to be your best self. That is what we will do together!",
        ),
        // 21-25
        listOf(
            "You're seeing benefits—and also recognizing that harm might quietly build without you realizing..." to
                "Especially at your age. Taking a break now is a wise move that shows real self-awareness and long-term thinking to avoid later harms that can be hard to see now. Very smart!",
            "You're seeing benefits—and also recognizing some potential harms." to
                "At this age, even small impacts can add up over time. Taking a break now is a wise move that shows real self-awareness and long-term thinking to avoid later harms that can be hard to see now. Very smart!",
            "You are seeing benefits and harm but in different ways." to
                "Breaking now helps you understand how weed may be shaping things without you realizing, especially at your age. A break now gives you that power to see what is really happening.",
            "You're recognizing harm is starting to outpace benefits." to
                "At your age, heavy weed use can have lasting impacts on your brain without you realizing it so a break really shows wisdom and courage to be your best self!",
            "You're recognizing the harm of weed is outpacing benefits." to
                "At your age, weed harms can have lasting impacts on your brain without you realizing it so a break really shows wisdom and courage to be your best self!",
        ),
        // 26-30
        listOf(
            "You are seeing benefits—but a break gives you space to examine what's actually working." to
                "You're in the right place to explore how it's affecting you when you're not using. That kind of clarity is powerful.",
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decide how it is affecting you when you are not using. That kind of insight can make a real difference. You're in the right place.",
            "You are seeing benefits and harm but in different ways." to
                "Breaking now helps you understand how weed may be shaping things without you realizing. A break now gives you that power to see what is really happening.",
            "You're aware that weed is starting to cause harm." to
                "Breaking now helps you stop that pattern before it becomes harder to change. That is wisdom. You're in the right place.",
            "You're recognizing the harm of weed is outpacing benefits." to
                "At your age, weed harms can have lasting impacts on your brain without you realizing it so a break really shows wisdom and courage to be your best self!",
        ),
        // 31-40
        listOf(
            "You are seeing benefits—but this pause helps you reflect more intentionally." to
                "You're in the right place to get clearer on how it's showing up in your life—and whether it still fits.",
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. That kind of honest reflection can shift everything.",
            "You are seeing benefits and harm but in different ways..." to
                "And while it may seem balanced, long-term harm can build silently without us realizing it. A break now gives you that power to see what is really happening.",
            "You're aware that weed is starting to cause harm." to
                "This break is your step toward more clarity, balance, and agency.",
            "You're facing harm—and taking a stand for yourself." to
                "This break is the right step to reclaim control and move forward with purpose.",
        ),
        // 41-50
        listOf(
            "You are seeing benefits—but at this life stage, stepping back lets you evaluate what truly supports your well-being." to
                "This is a wise moment to reflect.",
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. You're showing strength and insight by choosing to step back.",
            "You're feeling torn—but this is the phase when long-term harm can really sneak in." to
                "It builds slowly and becomes harder to change even with some benefits. A break now gives you the perspective to catch it.",
            "You're recognizing harm is growing—and you've chosen to make a shift." to
                "That matters. You're in the right place to take action.",
            "You're clear: the harm is too much to ignore." to
                "This break is your powerful step toward realignment and renewed purpose.",
        ),
        // 51-64
        listOf(
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. That insight is a powerful part of this process to live optimally.",
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. This is a strong and thoughtful step forward.",
            "You're feeling pulled in both directions—but harm tends to build quietly..." to
                "And becomes part of your routine even with benefits. This break lets you interrupt that before it settles in.",
            "You're aware that weed is beginning to cause harm." to
                "This break is your chance to pause, reflect, and choose more intentionally.",
            "You've made the call to step away from harm." to
                "You're in the right place to take back clarity and control.",
        ),
        // 65+
        listOf(
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. That insight is a powerful part of this process to live optimally.",
            "You are seeing benefits—but realize a break can help you get more intention and clarity..." to
                "And decipher how it is affecting you when you are not using. That insight is a powerful part of this process to live optimally.",
            "You're feeling both sides—but long-term harm can sneak in at this stage even with benefits." to
                "A break now gives you the clarity to protect your well-being before it becomes harder to shift so you can live optimally.",
            "You're noticing that weed is starting to cause harm." to
                "You're in the right place to make a thoughtful, intentional shift to age optimally.",
            "You're choosing to step away from harm—and that's a strong move." to
                "You're in the right place to protect your well-being as you age optimally.",
        ),
    )

    val ageIndex = ageMapping[ageResponse] ?: return null
    val helpHarmIndex = helpHarmMapping[helpHarmResponse] ?: return null
    if (ageIndex >= feedbackTable.size) return null
    if (helpHarmIndex >= feedbackTable[ageIndex].size) return null
    return feedbackTable[ageIndex][helpHarmIndex]
}
