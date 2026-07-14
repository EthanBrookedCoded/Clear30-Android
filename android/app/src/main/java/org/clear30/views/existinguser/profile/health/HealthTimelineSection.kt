package org.clear30.views.existinguser.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.HealthStep
import org.clear30.data.model.ProgramHealthProgress
import org.clear30.data.model.UserInfo
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.PI
import kotlin.math.sin

/**
 * HealthTimelinePage — ported 1:1 from iOS `HealthTimeline.swift`: the
 * per-category health detail opened by tapping a profile gauge card.
 *
 * Flow: when the tap caught a fresh unlock ([highlightCurrent], iOS `hasNew`
 * captured before `lastVisited` is stamped), an animated liquid-fill icon
 * counts up to the current percentage first, then transitions to the step
 * list where the newest card pops in with confetti. Otherwise the list shows
 * immediately. Above the current card sit the personal-best future steps (or
 * a skeleton placeholder for the next unlock); below it, every previous step;
 * at the foot, the category's FDA disclaimer. A category with no unlocked
 * steps yet renders the backend `intro_content` cards instead.
 *
 * Divergence noted: the progress-ring's triple glow shadow (iOS
 * HealthTimeline.swift:254-271) is skipped — Compose has no cheap analogue of
 * layered soft shadows on vector content; scale + liquid fill carry the moment.
 */
@Composable
fun HealthTimelinePage(
    healthProgress: ProgramHealthProgress,
    userInfo: UserInfo,
    highlightCurrent: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val gradient = remember(healthProgress.category.name) { healthProgress.gradientBrush() }
    val color1 = healthProgress.color1

    // Progress ring state (iOS @State mirror)
    var showProgressRing by remember { mutableStateOf(highlightCurrent) }
    var progressRingPercentage by remember { mutableIntStateOf(0) }
    // Current step reveal
    var showCurrentStep by remember { mutableStateOf(!highlightCurrent) }
    var confetti by remember { mutableIntStateOf(0) }
    // Debug: 10 taps on the title replays the ring animation (iOS handleTitleTap)
    var titleTapCount by remember { mutableIntStateOf(0) }
    var ringAnimationKey by remember { mutableIntStateOf(0) }

    val stepsWithDates = healthProgress.stepsWithDates
    val currentStepDate = healthProgress.currentStepDate
    val previousAndCurrentSteps = remember(stepsWithDates, currentStepDate) {
        if (currentStepDate == null) emptyList()
        else stepsWithDates.filter { it.second <= currentStepDate }.map { it.first }.reversed()
    }
    val displayedNextSteps = remember(stepsWithDates, currentStepDate) {
        val bestDate = healthProgress.bestStepDate
        if (currentStepDate == null || bestDate == null) emptyList()
        else stepsWithDates.filter { currentStepDate < it.second && it.second <= bestDate }.reversed()
    }
    val showIntro = previousAndCurrentSteps.isEmpty()
    val targetPercentage = healthProgress.currentStep?.percentage ?: 0

    // handleAppear + animateProgressRing (re-runs on the debug replay)
    LaunchedEffect(ringAnimationKey) {
        if (ringAnimationKey == 0) {
            Logger.logEvent(
                userInfo.loggingID,
                LogEventType.openedHealthTimeline,
                mapOf(
                    LogEventExtraDataType.HEALTH_CATEGORY to healthProgress.category.name,
                    LogEventExtraDataType.WAS_ANIMATED to if (highlightCurrent) "true" else "false",
                ),
            )
        }
        if (showProgressRing && targetPercentage > 0) {
            progressRingPercentage = 0
            delay(500)
            // Quadratic ease-out count-up over 0.5s with a light impact per tick.
            var elapsed = 0L
            for (num in 0..targetPercentage) {
                val progress = num.toDouble() / targetPercentage
                val at = ((progress * progress) * 500).toLong()
                if (at > elapsed) { delay(at - elapsed); elapsed = at }
                progressRingPercentage = num
                Haptics.lightImpact()
            }
            delay(500)
            showProgressRing = false
        } else {
            showProgressRing = false
        }
    }

    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(top = Dimens.headingTopPadding),
        ) {
            // Heading — back · icon + "Name's" over the category long name · % + small ring
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton("chevron.backward", onClick = onBack)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        titleTapCount++
                        if (titleTapCount >= 10) {
                            titleTapCount = 0
                            showProgressRing = true
                            showCurrentStep = true
                            ringAnimationKey++
                        }
                    },
                ) {
                    GradientIcon(healthProgress.category.sfSymbol, gradient, size = 25.dp)
                    Column {
                        SmallText("${userInfo.name}'s", color = Clear30Colors.text.copy(alpha = 0.5f))
                        Heading1(healthProgress.category.longName ?: healthProgress.category.name)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!showProgressRing && healthProgress.currentStep != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        SmallText("$targetPercentage%", color = Clear30Colors.text.copy(alpha = 0.5f))
                        HealthProgressRingSmall(targetPercentage, gradient, size = 20.dp)
                    }
                }
            }

            when {
                showIntro -> HealthIntroList(healthProgress, gradient, color1)
                showProgressRing -> HealthProgressRingLarge(
                    sfSymbol = healthProgress.category.sfSymbol,
                    gradient = gradient,
                    displayPercentage = minOf(progressRingPercentage, targetPercentage),
                    targetPercentage = targetPercentage,
                )
                else -> HealthTimelineList(
                    healthProgress = healthProgress,
                    gradient = gradient,
                    color1 = color1,
                    previousAndCurrentSteps = previousAndCurrentSteps,
                    displayedNextSteps = displayedNextSteps,
                    highlightCurrent = highlightCurrent,
                    showCurrentStep = showCurrentStep,
                    confetti = confetti,
                    onReveal = { showCurrentStep = true },
                    onConfetti = {
                        confetti++
                        Haptics.successHeavy()
                    },
                )
            }
        }
    }
}

// MARK: - List (iOS `list` + `animateList`)

@Composable
private fun HealthTimelineList(
    healthProgress: ProgramHealthProgress,
    gradient: Brush,
    color1: Color,
    previousAndCurrentSteps: List<HealthStep>,
    displayedNextSteps: List<Pair<HealthStep, Instant>>,
    highlightCurrent: Boolean,
    showCurrentStep: Boolean,
    confetti: Int,
    onReveal: () -> Unit,
    onConfetti: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Index of the current step's card: after the future cards (each card+link)
    // or after the single placeholder+link pair.
    val itemsBeforeCurrent =
        if (displayedNextSteps.isNotEmpty()) displayedNextSteps.size * 2
        else if (healthProgress.nextStepDate != null) 2 else 0

    LaunchedEffect(Unit) {
        // 2. Scroll the current step into view, then 3. animate it in (iOS animateList:
        // the card springs in immediately, confetti + heavy haptic land 0.25s later).
        if (previousAndCurrentSteps.isNotEmpty()) listState.scrollToItem(itemsBeforeCurrent)
        if (highlightCurrent && !showCurrentStep) {
            onReveal()
            delay(250)
            onConfetti()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.headingTopPadding),
    ) {
        // Future steps up to the personal best (non-placeholder), newest first.
        if (displayedNextSteps.isNotEmpty()) {
            displayedNextSteps.forEach { (step, date) ->
                item {
                    Box(Modifier.graphicsLayer { alpha = 0.5f }) {
                        HealthInfoCard(step = step, futureDate = date, gradient = gradient, color1 = color1)
                    }
                }
                item { HealthCardLink(alpha = 0.5f) }
            }
        } else {
            healthProgress.nextStepDate?.let { nextDate ->
                item { HealthInfoCardPlaceholder(nextDate) }
                item { HealthCardLink() }
            }
        }

        // Previous & current steps (newest first) — the first is the current one.
        previousAndCurrentSteps.forEachIndexed { index, step ->
            val first = index == 0
            val last = index == previousAndCurrentSteps.lastIndex
            val highlighted = highlightCurrent && first
            item(key = step.id) {
                if (first) {
                    Box {
                        AnimatedVisibility(
                            visible = showCurrentStep,
                            enter = scaleIn(Anim.rewardSpring(), initialScale = 5f) + fadeIn(Anim.rewardSpring()),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            HealthInfoCard(step = step, gradient = gradient, color1 = color1, highlighted = highlighted)
                        }
                        if (highlighted && confetti > 0) {
                            key(confetti) {
                                ConfettiOverlay(
                                    Modifier.matchParentSize(),
                                    count = 100,
                                    colors = listOf(color1, healthProgress.color2),
                                )
                            }
                        }
                    }
                } else {
                    HealthInfoCard(step = step, gradient = gradient, color1 = color1)
                }
            }
            if (!last) item { HealthCardLink() }
        }

        item {
            MiniText(
                healthProgress.category.fdaDisclaimer,
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

// MARK: - Intro (iOS `introView`)

@Composable
private fun HealthIntroList(healthProgress: ProgramHealthProgress, gradient: Brush, color1: Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.headingTopPadding),
    ) {
        healthProgress.nextStepDate?.let { nextDate ->
            item { HealthInfoCardPlaceholder(nextDate) }
            item { HealthCardLink() }
        }
        healthProgress.category.introContent.forEachIndexed { index, item ->
            if (item.type == "card") {
                if (index > 0) item { HealthCardLink() }
                item {
                    HealthInfoCardCustom(
                        badgeText = item.title,
                        badgeSymbol = item.sfSymbol ?: "questionmark.circle.fill",
                        mainText = item.body,
                        gradient = gradient,
                    )
                }
            } else {
                item {
                    Column {
                        MiniText(item.title, color = Clear30Colors.text.copy(alpha = 0.25f))
                        MiniText(item.body, color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// MARK: - Progress ring (iOS `progressRing` + WavyLiquidMask)

@Composable
private fun HealthProgressRingLarge(
    sfSymbol: String,
    gradient: Brush,
    displayPercentage: Int,
    targetPercentage: Int,
) {
    val currentProgress = if (targetPercentage > 0) displayPercentage.toFloat() / targetPercentage else 0f
    val scaleAmount = 1f + currentProgress * 0.1f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val iconSize = maxWidth * 0.45f
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .graphicsLayer { scaleX = scaleAmount; scaleY = scaleAmount },
                contentAlignment = Alignment.Center,
            ) {
                // Base icon at 25% opacity…
                GradientIcon(sfSymbol, gradient, size = iconSize, alpha = 0.25f)
                // …with the colored copy revealed by the rising liquid fill.
                val fillProgress = currentProgress * targetPercentage / 100f
                Box(Modifier.size(iconSize).clip(wavyLiquidShape(fillProgress))) {
                    GradientIcon(sfSymbol, gradient, size = iconSize)
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
            Heading1("$displayPercentage%")
        }
    }
}

/**
 * iOS `WavyLiquidMask` — fills left→right with a two-sine wavy leading edge
 * whose phase advances with progress.
 */
private fun wavyLiquidShape(progress: Float, waveHeight: Float = 5f, waveFrequency: Float = 2f): GenericShape =
    GenericShape { size, _ ->
        val fillWidth = size.width * progress.coerceIn(0f, 1f)
        val phase = progress * PI.toFloat() * 2f
        moveTo(0f, 0f)
        lineTo(fillWidth, 0f)
        val segments = 50
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val y = size.height * t
            val wave1 = sin(t * PI.toFloat() * waveFrequency + phase) * waveHeight
            val wave2 = sin(t * PI.toFloat() * waveFrequency * 1.5f - phase * 0.7f) * (waveHeight * 0.5f)
            lineTo(fillWidth + wave1 + wave2, y)
        }
        lineTo(0f, size.height)
        close()
    }

/** An SF-symbol icon tinted with a gradient (iOS `foregroundStyle(gradient)`). */
@Composable
private fun GradientIcon(
    symbol: String,
    gradient: Brush,
    size: androidx.compose.ui.unit.Dp,
    alpha: Float = 1f,
) {
    Icon(
        sfSymbol(symbol),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(size)
            .graphicsLayer(alpha = alpha, compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
            },
    )
}

// MARK: - Cards (iOS `infoCard` / `infoCardPlaceholder` / `infoCardCustom` / `cardLink`)

@Composable
private fun HealthInfoCard(
    step: HealthStep,
    gradient: Brush,
    color1: Color,
    futureDate: Instant? = null,
    highlighted: Boolean = false,
) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        gradient = if (highlighted) gradient else null,
        outlineGradient = if (highlighted) Clear30Gradients.white else gradient,
        outlineOpacity = 0.5f,
    ) {
        Column(Modifier.fillMaxWidth()) {
            HealthCardBadge(
                text = if (futureDate != null) relativeFutureString(futureDate) else "Day ${step.daysWithoutWeed}",
                symbol = if (futureDate != null) "clock.fill" else "checkmark",
                gradient = gradient,
                color1 = color1,
                highlighted = highlighted,
            )
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            SmallText(step.text)
            Spacer(Modifier.height(Dimens.cardSpacing / 4))
            // iOS TinyTextWithLinks — markdown/link rendering is backlogged (D5).
            if (step.citation.isNotBlank()) {
                TinyText(
                    step.citation,
                    color = (if (highlighted) Color.White else Clear30Colors.text).copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun HealthInfoCardPlaceholder(date: Instant) {
    Box(Modifier.graphicsLayer { alpha = 0.5f }) {
        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            color = Clear30Colors.opacityGray,
            shadowColor = Color.Transparent,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .cardStyle(shadowColor = Color.Transparent, padding = false)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    TinyText(relativeFutureString(date), color = Clear30Colors.text)
                    Icon(
                        sfSymbol("clock.fill"),
                        contentDescription = null,
                        tint = Clear30Colors.text,
                        modifier = Modifier.size(10.dp),
                    )
                }
                Spacer(Modifier.height(Dimens.cardSpacing / 2))
                Box(
                    Modifier.fillMaxWidth().height(25.dp)
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(Clear30Colors.opacityGray),
                )
            }
        }
    }
}

@Composable
private fun HealthInfoCardCustom(
    badgeText: String,
    badgeSymbol: String,
    mainText: String,
    gradient: Brush,
) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        outlineGradient = gradient,
        outlineOpacity = 0.5f,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .cardStyle(shadowColor = Color.Transparent, gradient = gradient, outlineGradient = Clear30Gradients.white, outlineOpacity = 0.5f, padding = false)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                TinyText(badgeText, color = Color.White)
                Icon(
                    sfSymbol(badgeSymbol),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(10.dp),
                )
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            SmallText(mainText)
        }
    }
}

/** The "Day N ✓" / relative-time badge chip at the top of an info card. */
@Composable
private fun HealthCardBadge(
    text: String,
    symbol: String,
    gradient: Brush,
    color1: Color,
    highlighted: Boolean,
) {
    Row(
        Modifier
            .cardStyle(
                shadowColor = Color.Transparent,
                gradient = if (highlighted) null else gradient,
                outlineGradient = if (highlighted) gradient else Clear30Gradients.white,
                outlineOpacity = 0.5f,
                padding = false,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        val fg = if (highlighted) color1 else Color.White
        TinyText(text, color = fg)
        Icon(
            sfSymbol(symbol),
            contentDescription = null,
            tint = fg.copy(alpha = 0.5f),
            modifier = Modifier.size(10.dp),
        )
    }
}

/** The little vertical connector between timeline cards (iOS `cardLink`). */
@Composable
private fun HealthCardLink(alpha: Float = 1f) {
    Box(
        Modifier
            .padding(start = Dimens.cardSpacing / 2)
            .width(10.dp)
            .height(20.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.opacityGray),
    )
}

/** The small trimmed ring next to the heading percentage (iOS `HealthProgressRingSmall`). */
@Composable
private fun HealthProgressRingSmall(percentage: Int, gradient: Brush, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 7.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Clear30Colors.opacityGray,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val sweep = 360f * (percentage / 100f).coerceIn(0f, 1f)
        if (sweep > 0f) drawArc(
            brush = gradient,
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
