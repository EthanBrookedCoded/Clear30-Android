package org.clear30.views.existinguser.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.clear30.data.DaysWithoutWeedType
import org.clear30.data.SmokedStatsCard
import org.clear30.data.StaticReward
import org.clear30.data.StaticRewardData
import org.clear30.data.VariableReward
import org.clear30.data.VariableRewardData
import org.clear30.data.model.DateSpan
import org.clear30.util.adding
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import org.clear30.views.theme.Lexend

/**
 * CheckInRewardViews — ported from iOS CheckInRewardViews.swift +
 * CheckInRewardVariableContainer.swift + CheckInRewardStaticContainer.swift.
 *
 * The animated post-check-in reward cards: counters that step up on an
 * ease-out schedule (with per-step haptics), per-reward confetti pops
 * ([ConfettiOverlay]), live ticking timers (weed-free timer, milestone
 * countdown, personal best), and the affirmation chips that push in at the
 * end. Each view fires `onAnimationComplete` once its sequence settles so the
 * reward screen can chain static → variable → continue.
 *
 * Intentional divergences from iOS (noted inline):
 *  • `RewardPigWithDollars` used image assets that don't exist in the Android
 *    resource set — replaced with an emoji pig + staged emoji pop-ins.
 *  • Emoji confetti (💵) is rendered as colored-rect confetti.
 *  • Custom check-in reward chips fall back to the brand blue accent (iOS
 *    extracts the first color of the custom gradient).
 */

// MARK: - Containers

/** iOS CheckInRewardVariableContainer — routes a [VariableReward] to its view. */
@Composable
fun CheckInRewardVariableContainer(
    variableReward: VariableReward,
    gradientBackground: Boolean,
    userName: String,
    onCompletion: (() -> Unit)? = null,
) {
    when (val d = variableReward.data) {
        is VariableRewardData.DaysWithoutWeed -> RewardDaysWithoutWeedLarge(
            days = d.days, type = d.type, affirmationText = d.affirmation,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.BreakProgress -> RewardThroughBreakLarge(
            current = d.current, max = d.max, name = d.name, affirmationText = d.affirmation,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.PersonalBest -> RewardPersonalBest(
            best = d.best, current = d.current, affirmationText = d.affirmation,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.MilestoneCountdown -> RewardMilestone(
            dateSpan = d.timeRemaining, milestone = d.milestone, unit = d.unit,
            affirmationText = d.affirmation, gradientBackground = gradientBackground,
            onAnimationComplete = onCompletion,
        )
        is VariableRewardData.AchievementBasic -> RewardAchievementBasic(
            number = d.number, label = d.label, introText = d.intro, titleText = d.title,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.CalendarAnimation -> RewardCalendarAnimation(
            day = d.day, month = d.month,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.DecreaseInUse -> RewardDecreaseInUse(
            percentage = d.percentage, name = userName,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.Growth -> RewardGrowth(
            percent = d.percent, title = d.title, subtitle = d.subtitle,
            gradientBackground = gradientBackground, onAnimationComplete = onCompletion,
        )
        is VariableRewardData.MotivationalQuote -> {
            RewardQuote(emoji = d.emoji, quote = d.quote, gradientBackground = gradientBackground)
            // RewardQuote has no animation, so complete immediately (iOS onAppear).
            LaunchedEffect(Unit) { onCompletion?.invoke() }
        }
        is VariableRewardData.ReminderOfWhy -> {
            RewardYourWhy(whys = d.whys, affirmationText = d.affirmation, gradientBackground = gradientBackground)
            LaunchedEffect(Unit) { onCompletion?.invoke() }
        }
    }
}

/** iOS CheckInRewardStaticContainer — routes a [StaticReward] to its view(s). */
@Composable
fun CheckInRewardStaticContainer(
    staticReward: StaticReward,
    onCompletion: (() -> Unit)? = null,
) {
    when (val d = staticReward.data) {
        is StaticRewardData.WeedFreeTimer -> RewardTimeSinceLastSmoked(
            targetDate = d.lastSmoked, showConfetti = true, onAnimationComplete = onCompletion,
        )
        is StaticRewardData.MoneySaved -> RewardMoneySavedLarge(
            saved = d.amount, showConfetti = true, onAnimationComplete = onCompletion,
        )
        is StaticRewardData.TimerAndMoney -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            RewardTimeSinceLastSmoked(targetDate = d.lastSmoked, showConfetti = true, onAnimationComplete = onCompletion)
            RewardMoneySavedLarge(saved = d.amount, showConfetti = false)
        }
        is StaticRewardData.SmokedStats -> Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            d.cards.forEachIndexed { index, card ->
                val isLast = index == d.cards.size - 1
                Box(Modifier.weight(1f)) {
                    when (card) {
                        is SmokedStatsCard.MoneySaved -> RewardMoneySavedSmall(
                            moneySaved = card.amount,
                            onAnimationComplete = if (isLast) onCompletion else null,
                        )
                        is SmokedStatsCard.DaysWithoutWeed -> RewardDaysWithoutWeedSmall(
                            days = card.days,
                            onAnimationComplete = if (isLast) onCompletion else null,
                        )
                        is SmokedStatsCard.ProgressThroughBreak -> RewardThroughBreakSmall(
                            percentage = card.current.toDouble() / card.max.coerceAtLeast(1),
                            onAnimationComplete = if (isLast) onCompletion else null,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Sober static rewards

/**
 * RewardTimeSinceLastSmoked — the "Weed free for" live timer card. Each unit
 * bar pops in staggered (0.35s apart), then a white confetti burst fires and
 * the whole stack ticks live once per second.
 */
@Composable
fun RewardTimeSinceLastSmoked(
    targetDate: Instant,
    showConfetti: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    // Which unit rows exist is decided once from the initial span (iOS `animate()`).
    val units = remember {
        val c = DateSpan(targetDate, now()).components
        buildList {
            if (c.months > 0) add("Month" to maxOf(c.months, 12))
            if (c.days > 0) add("Day" to 30)
            if (c.hours > 0) add("Hour" to 24)
            add("Minute" to 60)
            add("Second" to 60)
        }
    }
    val displayValues = remember { mutableStateListOf<Int>().apply { repeat(units.size) { add(0) } } }
    val revealed = remember { mutableStateListOf<Boolean>().apply { repeat(units.size) { add(false) } } }
    var confetti by remember { mutableIntStateOf(0) }
    var ticking by remember { mutableStateOf(false) }

    fun valueFor(unit: String): Int {
        val c = DateSpan(targetDate, now()).components
        return when (unit) {
            "Month" -> c.months; "Day" -> c.days; "Hour" -> c.hours
            "Minute" -> c.minutes; else -> c.seconds
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        units.forEachIndexed { index, (unit, _) ->
            Haptics.mediumImpact()
            displayValues[index] = valueFor(unit)
            revealed[index] = true
            delay(350)
        }
        if (showConfetti) confetti++
        Haptics.mediumImpact()
        onAnimationComplete?.invoke()
        ticking = true
    }

    // Live tick — keeps every bar honest once the reveal is done (iOS startTimer).
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(1_000)
            units.forEachIndexed { index, (unit, _) -> displayValues[index] = valueFor(unit) }
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            shadowColor = Clear30Colors.blue,
            gradient = Clear30Gradients.clear30,
            outlineGradient = Clear30Gradients.white,
            outlineOpacity = 0.5f,
        ) {
            Column(Modifier.fillMaxWidth()) {
                SmallText("Weed free for", color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(Dimens.cardSpacing))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    units.forEachIndexed { index, (unit, max) ->
                        val barAlpha by animateFloatAsState(
                            if (revealed[index]) 1f else 0.25f, Anim.rewardSpring(), label = "timerBarAlpha$index",
                        )
                        Box(Modifier.graphicsLayer { alpha = barAlpha }) {
                            RewardTimerBar(value = displayValues[index], max = max, unit = unit)
                        }
                    }
                }
            }
        }
        if (confetti > 0) {
            key(confetti) { ConfettiOverlay(Modifier.matchParentSize(), colors = listOf(Color.White)) }
        }
    }
}

/**
 * RewardMoneySavedLarge — the "$X saved" card: gradient counter that steps up
 * on an ease-out curve, a piggy that collects dollars as progress grows, a
 * progress bar toward the next $ breakpoint, and a confetti pop at the end.
 */
@Composable
fun RewardMoneySavedLarge(
    saved: Int,
    showConfetti: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    val max = remember(saved) { moneyBreakpoint(saved) }
    var displaySaved by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    val progress = displaySaved.toDouble() / max

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(saved, totalMillis = 1_500) { displaySaved = it }
        if (showConfetti) confetti++
        Haptics.mediumImpact()
        onAnimationComplete?.invoke()
    }

    Box(Modifier.fillMaxWidth()) {
        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            shadowColor = Clear30Colors.blue,
            outlineGradient = Clear30Gradients.clear30,
            outlineOpacity = 0.5f,
        ) {
            Column(Modifier.fillMaxWidth()) {
                SmallText("Money Saved", color = Clear30Colors.text.copy(alpha = 0.25f))
                Spacer(Modifier.height(Dimens.cardSpacing))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GradientGiganticText("$$displaySaved")
                    Spacer(Modifier.weight(1f))
                    RewardPigWithDollars(
                        showSingleDollar = progress > 0.225,
                        showMultiDollar = progress > 0.45,
                        showCoins = progress > 0.675,
                        modifier = Modifier.padding(end = Dimens.cardSpacing, bottom = Dimens.cardSpacing),
                    )
                }
                Spacer(Modifier.height(Dimens.cardSpacing))
                RewardBar(progress = progress, checked = confetti > 0, size = 18.dp)
                Spacer(Modifier.height(Dimens.cardSpacing / 2))
                Row(Modifier.fillMaxWidth()) {
                    SmallText("$0", color = Clear30Colors.text.copy(alpha = 0.25f))
                    Spacer(Modifier.weight(1f))
                    SmallText("$$max", color = Clear30Colors.text.copy(alpha = 0.25f))
                }
            }
        }
        if (confetti > 0) {
            key(confetti) {
                // iOS pops 💵 emoji confetti — colored-rect confetti here.
                ConfettiOverlay(Modifier.matchParentSize(), colors = listOf(Clear30Colors.green, Color.White))
            }
        }
    }
}

// MARK: - Smoked static rewards (stat cards)

/** RewardMoneySavedSmall — pig + "$X saved" stat card (smoked-day recap). */
@Composable
fun RewardMoneySavedSmall(
    moneySaved: Int,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var showSingleDollar by remember { mutableStateOf(false) }
    var showMultiDollar by remember { mutableStateOf(false) }
    var showCoins by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(250); showSingleDollar = true
        delay(125); showMultiDollar = true
        delay(125); showCoins = true
        onAnimationComplete?.invoke()
    }

    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = Clear30Colors.blue.copy(alpha = 0.5f),
        outlineGradient = Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            RewardPigWithDollars(
                showSingleDollar = showSingleDollar,
                showMultiDollar = showMultiDollar,
                showCoins = showCoins,
                size = 46.dp,
                modifier = Modifier.padding(end = Dimens.cardSpacing),
            )
            Column {
                SmallText("$$moneySaved")
                TinyText("saved", color = Clear30Colors.text.copy(alpha = 0.25f))
            }
        }
    }
}

/** RewardThroughBreakSmall — "% through break" stat card with a bottom progress strip. */
@Composable
fun RewardThroughBreakSmall(
    percentage: Double,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayValue by remember { mutableStateOf(0.0) }
    val animatedFill by animateFloatAsState(displayValue.toFloat(), Anim.rewardSpring(), label = "breakSmallFill")

    LaunchedEffect(Unit) {
        delay(500)
        displayValue = percentage
        onAnimationComplete?.invoke()
    }

    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = Clear30Colors.blue.copy(alpha = 0.5f),
        outlineGradient = Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SmallText("${(displayValue * 100).toInt()}%")
            TinyText("through break", color = Clear30Colors.text.copy(alpha = 0.25f))
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            // Bottom progress strip (iOS overlays it along the card's bottom edge).
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray),
            ) {
                Box(
                    Modifier.fillMaxWidth(animatedFill.coerceIn(0f, 1f)).fillMaxHeight()
                        .background(Clear30Gradients.clear30),
                )
            }
        }
    }
}

/** RewardDaysWithoutWeedSmall — "N days without weed" stat card. */
@Composable
fun RewardDaysWithoutWeedSmall(
    days: Int,
    nodeSize: Dp = 50.dp,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayDays by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(500)
        displayDays = days
        onAnimationComplete?.invoke()
    }

    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = Clear30Colors.blue.copy(alpha = 0.5f),
        outlineGradient = Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(nodeSize)
                    .clip(RoundedCornerShape(Dimens.cornerRadius * 0.75f))
                    .background(if (displayDays == 0) Clear30Gradients.grayFlat else Clear30Gradients.clear30),
                contentAlignment = Alignment.Center,
            ) {
                Heading2(
                    "$displayDays",
                    color = if (displayDays == 0) Clear30Colors.text.copy(alpha = 0.5f) else Color.White,
                )
            }
            Spacer(Modifier.width(Dimens.cardSpacing / 2))
            Column {
                TinyText("day${if (displayDays == 1) "" else "s"}")
                TinyText("without", color = Clear30Colors.text.copy(alpha = 0.25f))
                TinyText("weed", color = Clear30Colors.text.copy(alpha = 0.25f))
            }
        }
    }
}

// MARK: - Variable rewards

/**
 * RewardDaysWithoutWeedLarge — "N Days without weed this week" (also the
 * check-ins / streak / custom variants). One checkmark pops in per counted
 * day, a capsule slides behind them, then the affirmation chip pushes in.
 */
@Composable
fun RewardDaysWithoutWeedLarge(
    days: Int,
    type: DaysWithoutWeedType,
    affirmationText: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayDays by remember { mutableIntStateOf(0) }
    val normalized = minOf(days, displayDays)

    val title = when (type) {
        DaysWithoutWeedType.Streak, is DaysWithoutWeedType.CustomStreak ->
            "🔥 $normalized Day${if (normalized == 1) "" else "s"} straight"
        else -> "$normalized Day${if (normalized == 1) "" else "s"}"
    }
    val subtitle = when (type) {
        DaysWithoutWeedType.Sober, DaysWithoutWeedType.Streak -> "without weed this week"
        DaysWithoutWeedType.CheckIns -> "checked in this week"
        is DaysWithoutWeedType.Custom -> "${type.label} this week"
        is DaysWithoutWeedType.CustomStreak -> "${type.label} this week"
    }
    val gradient = when (type) {
        is DaysWithoutWeedType.Custom -> type.gradient
        is DaysWithoutWeedType.CustomStreak -> type.gradient
        else -> Clear30Gradients.clear30
    }

    LaunchedEffect(Unit) {
        val totalSteps = days + 3
        for (i in 0..totalSteps) {
            delay(350)
            displayDays = i
            if (i in 1..days) Haptics.mediumImpact()
            if (i == totalSteps) onAnimationComplete?.invoke()
        }
    }

    RewardCardShell(gradientBackground = gradientBackground, gradient = gradient) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Heading3(title)
            SmallText(subtitle, color = LocalContentColor.current.copy(alpha = 0.5f))
            Spacer(Modifier.height(Dimens.cardSpacing))

            // The day checkmarks, wrapped in a capsule once the count settles.
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (displayDays > days) {
                            if (gradientBackground) Color.White.copy(alpha = 0.25f)
                            else Clear30Colors.blue.copy(alpha = 0.25f)
                        } else Color.Transparent,
                    )
                    .padding(Dimens.cardSpacing / 2),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    repeat(days) { index ->
                        Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = normalized >= index + 1,
                                enter = scaleIn(Anim.rewardSpring(), initialScale = 3f) + fadeIn(Anim.rewardSpring()),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                Box(
                                    Modifier.size(25.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(if (gradientBackground) Clear30Gradients.white else gradient),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        sfSymbol("checkmark"),
                                        contentDescription = null,
                                        tint = if (gradientBackground) Clear30Colors.green else Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = displayDays > days + 2,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(Modifier.padding(top = Dimens.cardSpacing)) {
                    RewardAffirmationChip(affirmationText, gradientBackground = gradientBackground)
                }
            }
        }
    }
}

/** RewardThroughBreakLarge — "Name's Progress" break-progress bar with counter. */
@Composable
fun RewardThroughBreakLarge(
    current: Int,
    max: Int,
    name: String,
    affirmationText: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayCurrent by remember { mutableIntStateOf(0) }
    val normalized = minOf(current, displayCurrent)

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(current, totalMillis = 600) { displayCurrent = it }
        delay(150)
        displayCurrent = current + 1 // reveal the affirmation
        onAnimationComplete?.invoke()
    }

    RewardCardShell(gradientBackground = gradientBackground) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SmallText("$name's Progress")
            Spacer(Modifier.height(Dimens.cardSpacing))
            RewardBreakBar(
                fraction = normalized.toFloat() / max.coerceAtLeast(1),
                text = "$normalized of $max days",
                onGradient = gradientBackground,
            )
            AnimatedVisibility(
                visible = displayCurrent > current,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(Modifier.padding(top = Dimens.cardSpacing)) {
                    RewardAffirmationChip(affirmationText, stretch = true, gradientBackground = gradientBackground)
                }
            }
        }
    }
}

/** RewardYourWhy — pager over the user's break reasons + affirmation chip. */
@Composable
fun RewardYourWhy(
    whys: List<String>,
    affirmationText: String,
    gradientBackground: Boolean = true,
) {
    var currentWhyIndex by remember { mutableIntStateOf(0) }

    RewardCardShell(gradientBackground = gradientBackground) {
        Column(Modifier.fillMaxWidth()) {
            SmallText("Your Whys", color = LocalContentColor.current.copy(alpha = 0.5f))
            Spacer(Modifier.height(Dimens.cardSpacing))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                if (whys.size > 1) {
                    val leftDisabled = currentWhyIndex <= 0
                    IconButton(
                        icon = "chevron.left",
                        tint = LocalContentColor.current.copy(alpha = if (leftDisabled) 0.25f else 0.75f),
                    ) { if (!leftDisabled) currentWhyIndex-- }
                }
                // The inner "why" card — inverted colors vs the outer card.
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(if (gradientBackground) Clear30Gradients.white else Clear30Gradients.clear30)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DefaultText(
                        whys.getOrElse(currentWhyIndex) { whys.firstOrNull() ?: "" },
                        color = if (gradientBackground) Clear30Colors.blue else Color.White,
                    )
                }
                if (whys.size > 1) {
                    val rightDisabled = currentWhyIndex >= whys.size - 1
                    IconButton(
                        icon = "chevron.right",
                        tint = LocalContentColor.current.copy(alpha = if (rightDisabled) 0.25f else 0.75f),
                    ) { if (!rightDisabled) currentWhyIndex++ }
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
            RewardAffirmationChip(affirmationText, stretch = true, gradientBackground = gradientBackground)
        }
    }
}

/**
 * RewardPersonalBest — the personal-best vs current run comparison: two bars
 * with crown/timer tags, the current one counting up eased and then ticking
 * live every second.
 */
@Composable
fun RewardPersonalBest(
    best: DateSpan,
    current: DateSpan,
    affirmationText: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayBest by remember { mutableStateOf(DateSpan(best.startDate, best.startDate)) }
    var displayCurrent by remember { mutableStateOf(DateSpan(current.startDate, current.startDate)) }
    var showAffirmation by remember { mutableStateOf(false) }
    var ticking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        displayBest = best
        Haptics.mediumImpact()
        delay(500)
        // Eased count-up of the current span in 11 chunks (iOS handleAnimate).
        val seconds = current.totalSeconds
        val chunks = 11
        var elapsed = 0L
        for (num in 0 until chunks) {
            val progress = num.toDouble() / chunks
            val at = ((progress * progress) * 1_000).toLong()
            if (at > elapsed) { delay(at - elapsed); elapsed = at }
            displayCurrent = DateSpan(
                current.startDate,
                current.startDate.adding(seconds = (progress * progress * seconds).toLong()),
            )
            Haptics.lightImpact()
        }
        delay(150)
        displayCurrent = current
        showAffirmation = true
        onAnimationComplete?.invoke()
        ticking = true
    }
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(1_000)
            displayCurrent = DateSpan(current.startDate, now())
        }
    }

    RewardCardShell(gradientBackground = gradientBackground) {
        Column(Modifier.fillMaxWidth()) {
            RewardBarTag(icon = "crown.fill", text = "Personal Best", gradientBackground = gradientBackground)
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            RewardBreakBar(
                fraction = displayBest.totalSeconds.toFloat() / (best.totalSeconds * 1.2f).coerceAtLeast(1f),
                text = displayBest.textRepresentation,
                onGradient = gradientBackground,
            )
            Spacer(Modifier.height(Dimens.cardSpacing))
            RewardBarTag(icon = "timer", text = "Current", gradientBackground = gradientBackground)
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            RewardBreakBar(
                fraction = displayCurrent.totalSeconds.toFloat() / (current.totalSeconds * 6f).coerceAtLeast(1f),
                text = displayCurrent.textRepresentation,
                onGradient = gradientBackground,
            )
            AnimatedVisibility(
                visible = showAffirmation,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(Modifier.padding(top = Dimens.cardSpacing)) {
                    RewardAffirmationChip(affirmationText, stretch = true, gradientBackground = gradientBackground)
                }
            }
        }
    }
}

/** RewardAchievementBasic — animated number chip + "Wow!" copy reveal. */
@Composable
fun RewardAchievementBasic(
    number: Int,
    label: String,
    introText: String,
    titleText: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayNumber by remember { mutableIntStateOf(0) }
    val normalized = minOf(displayNumber, number)

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(number, totalMillis = 600) { displayNumber = it }
        delay(500)
        displayNumber = number + 1
        Haptics.successHeavy()
        onAnimationComplete?.invoke()
    }

    RewardCardShell(gradientBackground = gradientBackground) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            // Number chip — flips between flat and gradient as the count lands.
            val gradientChip = !gradientBackground && displayNumber >= number ||
                gradientBackground && displayNumber < number
            Column(
                Modifier
                    .clip(RoundedCornerShape(Dimens.cornerRadius * 1.2f))
                    .background(if (gradientChip) Clear30Gradients.clear30 else Clear30Gradients.white)
                    .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Heading2("$normalized", color = if (gradientChip) Color.White else Clear30Colors.blue)
                TinyText(
                    label,
                    color = (if (gradientChip) Color.White else Clear30Colors.text).copy(alpha = 0.5f),
                )
            }
            AnimatedVisibility(
                visible = displayNumber > number,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                Column {
                    SmallText(introText, color = LocalContentColor.current.copy(alpha = 0.5f))
                    SmallText(titleText)
                }
            }
        }
    }
}

/**
 * RewardMilestone — "You're almost at N Days": a live HH:MM:SS countdown to
 * the next weed-free milestone with a star tag marking it.
 */
@Composable
fun RewardMilestone(
    dateSpan: DateSpan,
    milestone: Int,
    unit: String,
    affirmationText: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var nowState by remember { mutableStateOf(dateSpan.startDate) }
    var ticking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        val currentSeconds = DateSpan(dateSpan.startDate, now()).totalSeconds.coerceAtLeast(0)
        if (currentSeconds > 0) {
            // Eased sweep of the elapsed portion, then live ticking (iOS chunks).
            val chunks = 11
            var elapsed = 0L
            for (num in 0 until chunks) {
                val progress = num.toDouble() / chunks
                val at = ((progress * progress) * 1_000).toLong()
                if (at > elapsed) { delay(at - elapsed); elapsed = at }
                nowState = dateSpan.startDate.adding(seconds = (progress * progress * currentSeconds).toLong())
                Haptics.lightImpact()
            }
        }
        nowState = now()
        onAnimationComplete?.invoke()
        ticking = true
    }
    LaunchedEffect(ticking) {
        while (ticking) { delay(1_000); nowState = now() }
    }

    val currentValue = (DateSpan(dateSpan.startDate, nowState).totalSeconds * 0.86).toInt()
    val maxValue = dateSpan.totalSeconds.coerceAtLeast(1)

    RewardCardShell(gradientBackground = gradientBackground) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Heading3("You're almost at $milestone $unit${if (milestone == 1) "" else "s"}")
            TinyText(affirmationText, color = LocalContentColor.current.copy(alpha = 0.5f))
            Spacer(Modifier.height(Dimens.cardSpacing))
            RewardBreakBar(
                fraction = currentValue.toFloat() / maxValue,
                text = if (nowState == dateSpan.startDate) "" else countdownString(nowState, dateSpan.endDate),
                onGradient = gradientBackground,
                icon = "timer",
            )
            // Milestone tag hanging off the end of the bar.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(7.dp).height(15.dp)
                            .background(
                                if (gradientBackground) Color.White.copy(alpha = 0.25f)
                                else Clear30Colors.blue,
                            ),
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
                            .background(
                                if (gradientBackground) Color.White.copy(alpha = 0.25f)
                                else Clear30Colors.opacityGray,
                            )
                            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        Icon(
                            sfSymbol("star.fill"),
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(18.dp),
                        )
                        TinyText("$milestone $unit${if (milestone == 1) "" else "s"}")
                    }
                }
            }
        }
    }
}

/** RewardCalendarAnimation — day-of-month counter chip with a confetti finale. */
@Composable
fun RewardCalendarAnimation(
    day: Int,
    month: String,
    gradientBackground: Boolean = false,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayDay by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(day, totalMillis = 1_000) { displayDay = it }
        confetti++
        Haptics.successHeavy()
        onAnimationComplete?.invoke()
    }

    Box(Modifier.fillMaxWidth()) {
        RewardCardShell(gradientBackground = gradientBackground) {
            Box(Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing), contentAlignment = Alignment.Center) {
                val gradientChip = !gradientBackground && displayDay >= day ||
                    gradientBackground && displayDay < day
                val chipScale by animateFloatAsState(if (confetti > 0) 1.2f else 1f, Anim.rewardSpring(), label = "calChip")
                Column(
                    Modifier
                        .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                        .clip(RoundedCornerShape(Dimens.cornerRadius * 1.2f))
                        .background(if (gradientChip) Clear30Gradients.clear30 else Clear30Gradients.white)
                        .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Heading2("$displayDay", color = if (gradientChip) Color.White else Clear30Colors.blue)
                    TinyText(month, color = (if (gradientChip) Color.White else Color.Black).copy(alpha = 0.5f))
                }
            }
        }
        if (confetti > 0) {
            key(confetti) {
                ConfettiOverlay(
                    Modifier.matchParentSize(),
                    colors = if (gradientBackground) listOf(Color.White) else listOf(Clear30Colors.blue, Clear30Colors.green),
                )
            }
        }
    }
}

/** RewardDecreaseInUse — circular % gauge counting up + "Wow!" copy reveal. */
@Composable
fun RewardDecreaseInUse(
    percentage: Int,
    name: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayNumber by remember { mutableIntStateOf(0) }
    val normalized = minOf(displayNumber, percentage)

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(percentage, totalMillis = 600) { displayNumber = it }
        delay(500)
        displayNumber = percentage + 1
        Haptics.successHeavy()
        onAnimationComplete?.invoke()
    }

    RewardCardShell(gradientBackground = gradientBackground) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                RewardCircularProgressBar(
                    progress = normalized / 100.0,
                    lineWidth = 10.dp,
                    strokeBrush = if (gradientBackground) Clear30Gradients.white else Clear30Gradients.clear30,
                    trackColor = if (gradientBackground) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray,
                    modifier = Modifier.fillMaxSize(),
                )
                Heading3("$normalized%")
            }
            AnimatedVisibility(
                visible = displayNumber > percentage,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                Column {
                    SmallText("Wow $name 🥹", color = LocalContentColor.current.copy(alpha = 0.5f))
                    SmallText("You've decreased your use by $percentage%")
                }
            }
        }
    }
}

/** RewardGrowth — "% Growth" bar for a slip framed as non-linear progress. */
@Composable
fun RewardGrowth(
    percent: Int,
    title: String,
    subtitle: String,
    gradientBackground: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null,
) {
    var displayPercent by remember { mutableIntStateOf(0) }
    val normalized = minOf(percent, displayPercent)

    LaunchedEffect(Unit) {
        delay(500)
        animateEasedCount(percent, totalMillis = 600) { displayPercent = it }
        delay(150)
        displayPercent = percent + 1
        onAnimationComplete?.invoke()
    }

    RewardCardShell(gradientBackground = gradientBackground) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SmallText(title)
            AnimatedVisibility(
                visible = displayPercent > percent,
                enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
                exit = scaleOut() + fadeOut(),
            ) {
                SmallText(subtitle, color = LocalContentColor.current.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
            RewardBreakBar(
                fraction = normalized.coerceAtMost(100) / 100f,
                text = "$normalized% Growth",
                onGradient = gradientBackground,
            )
        }
    }
}

/** RewardQuote — emoji chip + encouraging quote (no animation). */
@Composable
fun RewardQuote(
    emoji: String,
    quote: String,
    gradientBackground: Boolean = true,
) {
    RewardCardShell(gradientBackground = gradientBackground) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(Dimens.cornerRadius * 1.2f))
                    .background(if (gradientBackground) Clear30Gradients.white else Clear30Gradients.clear30)
                    .padding(horizontal = Dimens.cardSpacing / 4 + Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 2),
            ) {
                Heading1(emoji)
            }
            SmallText(quote, modifier = Modifier.weight(1f))
        }
    }
}

// MARK: - Components

/**
 * The standard reward-card treatment (iOS CardStyle usage across every reward
 * view): gradient fill + white outline when [gradientBackground], else a flat
 * card with the gradient outline. Content color follows the fill.
 */
@Composable
private fun RewardCardShell(
    gradientBackground: Boolean,
    gradient: Brush = Clear30Gradients.clear30,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Clear30Card(
        modifier = modifier.fillMaxWidth(),
        shadowColor = Clear30Colors.blue.copy(alpha = 0.5f),
        gradient = if (gradientBackground) gradient else null,
        outlineGradient = if (gradientBackground) Clear30Gradients.white else gradient,
        outlineOpacity = 0.5f,
    ) { content() }
}

/** RewardAffirmationChip — the tiny capsule affirmation at the foot of a reward. */
@Composable
fun RewardAffirmationChip(
    affirmationText: String,
    stretch: Boolean = false,
    gradientBackground: Boolean = true,
) {
    val bg = if (gradientBackground) Color.White.copy(alpha = 0.25f) else Clear30Colors.blue.copy(alpha = 0.25f)
    val fg = if (gradientBackground) Color.White else Clear30Colors.blue
    Box(
        Modifier
            .then(if (stretch) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
        contentAlignment = Alignment.Center,
    ) {
        TinyText(affirmationText, color = fg)
    }
}

/** The crown/timer label pill above a personal-best bar. */
@Composable
private fun RewardBarTag(icon: String, text: String, gradientBackground: Boolean) {
    Row(
        Modifier
            .padding(start = Dimens.cardSpacing)
            .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
            .background(if (gradientBackground) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray)
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Icon(sfSymbol(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(18.dp))
        SmallText(text)
    }
}

/**
 * RewardBreakBar — the 40dp reward progress bar (iOS `BreakProgressBar`): the
 * label reads in the fill color over the track and flips as the fill sweeps
 * under it. [onGradient] = the bar sits on a gradient card (white fill), else
 * a flat card (gradient fill).
 */
@Composable
fun RewardBreakBar(
    fraction: Float,
    text: String,
    onGradient: Boolean,
    icon: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Anim.rewardSpring(), label = "rewardBreakBar")

    // Container clipped ONCE; the fill is a plain left-aligned rect — clipping
    // the fill independently collapses its corner radii on narrow fills.
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp).clip(shape)) {
        val full = maxWidth
        val fillW = full * animated

        val trackColor = if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray
        val fillBrush = if (onGradient) Clear30Gradients.white else Clear30Gradients.clear30
        val labelOnTrack = if (onGradient) Color.White else Clear30Colors.text
        val labelOnFill = if (onGradient) Clear30Colors.blue else Color.White

        Box(Modifier.fillMaxSize().background(trackColor))
        Box(Modifier.width(fillW).fillMaxHeight().background(fillBrush))

        @Composable
        fun label(color: Color) {
            Row(
                Modifier.fillMaxHeight().padding(start = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                if (icon != null) {
                    Icon(sfSymbol(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Text(
                    text,
                    color = color,
                    fontFamily = Lexend,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }

        // Base label over the track, then the fill-colored copy clipped to the fill.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { label(labelOnTrack) }
        Box(Modifier.width(fillW).fillMaxHeight().clipToBounds()) {
            Box(Modifier.width(full).fillMaxHeight(), contentAlignment = Alignment.CenterStart) { label(labelOnFill) }
        }
    }
}

/** RewardTimerBar — one unit row of the weed-free timer (white fill over 25% track). */
@Composable
private fun RewardTimerBar(value: Int, max: Int, unit: String) {
    val label = "$value $unit${if (value == 1) "" else "s"}"
    val shape = RoundedCornerShape(14.dp)
    val fraction by animateFloatAsState(
        (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f),
        Anim.rewardSpring(),
        label = "rewardTimer-$unit",
    )

    // Container clipped once; fill = plain rect (same fix as RewardBreakBar).
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp).clip(shape)) {
        val full = maxWidth
        val fillW = full * fraction
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.width(fillW).fillMaxHeight().background(Color.White))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            TimerBarLabel(label, Color.White)
        }
        Box(Modifier.width(fillW).fillMaxHeight().clipToBounds()) {
            Box(Modifier.width(full).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                TimerBarLabel(label, Clear30Colors.blue)
            }
        }
    }
}

@Composable
private fun TimerBarLabel(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier.padding(start = 11.dp),
        color = color,
        fontFamily = Lexend,
        fontSize = 19.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}

/** RewardBar — money-saved progress bar with the check knob at the fill's edge. */
@Composable
private fun RewardBar(progress: Double, checked: Boolean, size: Dp = 18.dp) {
    val normalized = progress.coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(normalized, Anim.rewardSpring(), label = "rewardBar")

    // Container clipped once; fill = plain rect (same fix as RewardBreakBar).
    BoxWithConstraints(Modifier.fillMaxWidth().height(size).clip(RoundedCornerShape(99.dp))) {
        val full = maxWidth
        Box(Modifier.fillMaxSize().background(Clear30Colors.opacityGray))
        Box(Modifier.width(full * animated).fillMaxHeight().background(Clear30Gradients.clear30))
        // Knob riding the fill front.
        Box(
            Modifier
                .offset(x = (full * animated - size).coerceAtLeast(0.dp))
                .size(size)
                .padding(size * 0.1f)
                .clip(RoundedCornerShape(percent = 50))
                .background(Clear30Colors.button),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    sfSymbol("checkmark"),
                    contentDescription = null,
                    tint = Clear30Colors.blue,
                    modifier = Modifier.size(size * 0.6f),
                )
            }
        }
    }
}

/**
 * RewardCircularProgressBar — the 270° arc gauge (iOS trims a circle to 0.75
 * and rotates it so the gap sits at the bottom).
 */
@Composable
fun RewardCircularProgressBar(
    progress: Double,
    lineWidth: Dp,
    strokeBrush: Brush,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val fillAmount = 0.75f
    val sweepMax = 360f * fillAmount
    val startAngle = 90f + (360f * (1f - fillAmount)) / 2f // gap centered at the bottom
    Canvas(modifier) {
        val strokePx = lineWidth.toPx()
        val inset = strokePx / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepMax,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        drawArc(
            brush = strokeBrush,
            startAngle = startAngle,
            sweepAngle = (sweepMax * progress.coerceIn(0.0, 1.0)).toFloat(),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

/**
 * RewardPigWithDollars — iOS layers pig/dollar image assets; those assets
 * aren't in the Android drawable set, so this stacks the same beats with
 * emoji: the pig with a dollar, a wad, and coins popping in as savings grow.
 */
@Composable
private fun RewardPigWithDollars(
    showSingleDollar: Boolean,
    showMultiDollar: Boolean,
    showCoins: Boolean,
    size: Dp = 55.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size)) {
        Text("🐷", fontSize = (size.value * 0.62f).sp, modifier = Modifier.align(Alignment.Center))
        PopEmoji("💵", visible = showSingleDollar, Modifier.align(Alignment.BottomStart), size)
        PopEmoji("💰", visible = showMultiDollar, Modifier.align(Alignment.BottomEnd), size)
        PopEmoji("🪙", visible = showCoins, Modifier.align(Alignment.TopStart), size)
    }
}

@Composable
private fun PopEmoji(emoji: String, visible: Boolean, modifier: Modifier, parentSize: Dp) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(Anim.rewardSpring()) + fadeIn(Anim.rewardSpring()),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        Text(emoji, fontSize = (parentSize.value * 0.3f).sp)
    }
}

/** Gigantic (50sp) text filled with the brand gradient (iOS GiganticText + gradient). */
@Composable
private fun GradientGiganticText(text: String) {
    Text(
        text,
        style = TextStyle(brush = Clear30Gradients.clear30),
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 50.sp,
        maxLines = 1,
    )
}

// MARK: - Helpers

/**
 * Steps a counter 1..[target] on the iOS quadratic ease-out schedule
 * (fast at first, slowing toward the end) over [totalMillis], with a light
 * haptic per step.
 */
private suspend fun animateEasedCount(target: Int, totalMillis: Long, onStep: (Int) -> Unit) {
    if (target <= 0) return
    var elapsed = 0L
    for (num in 0 until target) {
        val progress = num.toDouble() / target
        val at = ((progress * progress) * totalMillis).toLong()
        if (at > elapsed) {
            delay(at - elapsed)
            elapsed = at
        }
        onStep(num + 1)
        Haptics.lightImpact()
    }
}

/** iOS `RewardMoneySavedLarge.getNextBreakpoint`: $5s to $25, then $25s from $50. */
private fun moneyBreakpoint(savedAmount: Int): Int {
    for (amount in 5..25 step 5) {
        if (amount > savedAmount) return amount
    }
    var current = 50
    while (current <= savedAmount + 25) {
        if (current > savedAmount) return current
        current += 25
    }
    return current
}

/** "DD:HH:MM:SS" (or "HH:MM:SS") countdown between two instants (iOS `timeDifference`). */
private fun countdownString(from: Instant, to: Instant): String {
    val totalSeconds = ((to.toEpochMilliseconds() - from.toEpochMilliseconds()) / 1000L).coerceAtLeast(0L)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (days > 0) {
        "%02d:%02d:%02d:%02d".format(days, hours, minutes, seconds)
    } else {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
