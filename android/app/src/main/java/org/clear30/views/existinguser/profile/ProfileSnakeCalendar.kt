package org.clear30.views.existinguser.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreak
import org.clear30.util.adding
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Haptics

/**
 * ProfileSnakeCalendar — ported 1:1 from iOS `ProfileSnakeCalendar.swift`.
 *
 * The 30-day break calendar: a 5×6 "snake" of day nodes (rows alternate
 * left→right / right→left, with the row-ends connected vertically) drawn on a
 * card. Sober days fill with the blue→green gradient and their connectors
 * highlight blue; other past days stay gray; future days dim; today's node
 * glows. [animateNodes] plays the staggered pop-in used by the day-30
 * celebration (iOS `startAnimation`).
 */
@Composable
fun ProfileSnakeCalendar(
    height: Dp,
    program: Program,
    programBreak: ProgramBreak,
    modifier: Modifier = Modifier,
    animateNodes: Boolean = false,
    onAnimationComplete: (() -> Unit)? = null,
    revision: Int = 0,
) {
    val currentDay = programBreak.currentBreakDay
    val breakStart = programBreak.startDate
    // `revision` invalidates this calendar after in-place break mutations
    // (restart / change start date) — keying the read on it keeps the param
    // genuinely used (unused params are excluded from the skip comparison).
    val startPlain = remember(breakStart, revision) { PlainDate.from(breakStart) }

    // iOS `dayMarked(_:)` — the break-relative day is sober AND not in the future.
    fun dayMarked(day: Int): Boolean {
        val target = PlainDate.from(breakStart.adding(days = day))
        if (startPlain.daysTo(target) > currentDay) return false
        return program.dayInfo[target]?.sober == true
    }

    val color1 = Clear30Colors.blue
    val gradient = remember {
        Clear30Gradients.linear(
            listOf(Clear30Colors.blue, Clear30Colors.green),
            Clear30Gradients.bottomLeading,
            Clear30Gradients.topTrailing,
        )
    }

    fun connectorColor(highlighted: Boolean, active: Boolean): Color =
        if (highlighted) color1
        else Clear30Colors.opacityGray.copy(alpha = Clear30Colors.opacityGray.alpha * (if (active) 1f else 0.5f))

    // Leading edge (j == 0) horizontal highlight pairs by row (iOS switch tables).
    fun leadingEdgeHorizontalHighlighted(i: Int): Boolean = when (i) {
        1 -> dayMarked(1) && dayMarked(2)
        2 -> dayMarked(11) && dayMarked(12)
        3 -> dayMarked(13) && dayMarked(14)
        4 -> dayMarked(23) && dayMarked(24)
        5 -> dayMarked(25) && dayMarked(26)
        else -> false
    }

    // Trailing edge (j == 5) horizontal highlight pairs by row.
    fun trailingEdgeHorizontalHighlighted(i: Int): Boolean = when (i) {
        1 -> dayMarked(5) && dayMarked(6)
        2 -> dayMarked(7) && dayMarked(8)
        3 -> dayMarked(17) && dayMarked(18)
        4 -> dayMarked(19) && dayMarked(20)
        5 -> dayMarked(29) && dayMarked(30)
        else -> false
    }

    fun leadingEdgeHorizontalActive(i: Int, current: Int, prev: Int, next: Int): Boolean =
        ((if (i % 2 == 0) prev else next) <= currentDay) && (if (i % 2 == 0) current <= currentDay else true)

    fun trailingEdgeHorizontalActive(i: Int, current: Int, prev: Int, next: Int): Boolean =
        ((if (i % 2 == 0) next else prev) <= currentDay) && (if (i % 2 == 0) true else current <= currentDay)

    fun leadingEdgeVerticalColor(i: Int, current: Int, currentMarked: Boolean, prev: Int, next: Int): Color {
        val highlighted = (i % 2 == 0 && currentMarked && dayMarked(next)) ||
            (i % 2 == 1 && currentMarked && dayMarked(prev))
        val active = ((if (i % 2 == 0) next else prev) <= currentDay) && (current <= currentDay)
        return connectorColor(highlighted, active)
    }

    fun trailingEdgeVerticalColor(i: Int, current: Int, currentMarked: Boolean, prev: Int, next: Int): Color {
        val highlighted = (i % 2 == 0 && currentMarked && dayMarked(prev)) ||
            (i % 2 == 1 && currentMarked && dayMarked(next))
        val active = ((if (i % 2 == 0) prev else next) <= currentDay) && (current <= currentDay)
        return connectorColor(highlighted, active)
    }

    fun middleLeftColor(current: Int, prev: Int): Color =
        connectorColor(dayMarked(current) && dayMarked(prev), current <= currentDay && prev <= currentDay)

    fun middleRightColor(current: Int, next: Int): Color =
        connectorColor(dayMarked(current) && dayMarked(next), next <= currentDay)

    // Staggered pop-in (iOS `startAnimation`) — days 1..31 revealed over 1s.
    var animatedDays by remember { mutableStateOf(setOf<Int>()) }
    if (animateNodes) {
        LaunchedEffect(Unit) {
            val totalMs = 1_000L
            for (day in 1..31) {
                delay(totalMs / 31)
                animatedDays = animatedDays + day
                if (day == 30) Haptics.successHeavy() else Haptics.lightImpact()
            }
            delay(500)
            onAnimationComplete?.invoke()
        }
    }
    fun connectorAlpha(current: Int): Float =
        if (animateNodes && (current + 1) !in animatedDays) 0f else 1f

    // iOS: `.frame(height: 275).modifier(CardStyle3D(...))` — statically the 3D
    // modifier renders a plain card, so we use the standard cardStyle here.
    Box(
        modifier.fillMaxWidth().cardStyle(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().height(height), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            for (i in 1..5) {
                Row(Modifier.fillMaxWidth().height(SNAKE_ROW_HEIGHT)) {
                    for (j in 0..5) {
                        val current = snakeDay(i, j)
                        val prev = snakePreviousDay(i, j)
                        val next = snakeNextDay(i, j)
                        val currentMarked = dayMarked(current)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when (j) {
                                0 -> {
                                    // Horizontal line (leading edge) — right half only.
                                    HorizontalConnector(
                                        color = connectorColor(
                                            leadingEdgeHorizontalHighlighted(i),
                                            leadingEdgeHorizontalActive(i, current, prev, next),
                                        ),
                                        leftHalf = false,
                                        alpha = connectorAlpha(current),
                                    )
                                    // Vertical line (leading edge) — masked per row (iOS VStack mask).
                                    VerticalConnector(
                                        color = leadingEdgeVerticalColor(i, current, currentMarked, prev, next),
                                        topClear = i == 1 || i == 2 || i == 4,
                                        bottomClear = i == 1 || i == 3 || i == 5,
                                        alpha = connectorAlpha(current),
                                    )
                                }
                                5 -> {
                                    // Horizontal line (trailing edge) — left half only.
                                    HorizontalConnector(
                                        color = connectorColor(
                                            trailingEdgeHorizontalHighlighted(i),
                                            trailingEdgeHorizontalActive(i, current, prev, next),
                                        ),
                                        leftHalf = true,
                                        alpha = connectorAlpha(current),
                                    )
                                    VerticalConnector(
                                        color = trailingEdgeVerticalColor(i, current, currentMarked, prev, next),
                                        topClear = i == 1 || i == 3 || i == 5,
                                        bottomClear = i == 2 || i == 4 || i == 5,
                                        alpha = connectorAlpha(current),
                                    )
                                }
                                else -> {
                                    // Middle connectors — iOS rotates the pair 180° on even
                                    // rows, which just swaps which half shows which color.
                                    val leftColor = if (i % 2 == 0) middleRightColor(current, next) else middleLeftColor(current, prev)
                                    val rightColor = if (i % 2 == 0) middleLeftColor(current, prev) else middleRightColor(current, next)
                                    HorizontalConnector(leftColor, leftHalf = true, alpha = connectorAlpha(current))
                                    HorizontalConnector(rightColor, leftHalf = false, alpha = connectorAlpha(current))
                                }
                            }

                            ProfileSnakeCalendarNode(
                                label = current,
                                marked = currentMarked,
                                enabled = current <= currentDay,
                                gradient = gradient,
                                isCurrentDay = current == currentDay,
                                animateIn = animateNodes,
                                isAnimated = current in animatedDays,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }
    }
}

// iOS node metrics: nodeSize 30, strokeSize 2.75, cornerRadiusIntensity 2.875.
private val NODE_SIZE = 30.dp
private val STROKE_SIZE = 2.75.dp
private val NODE_CORNER = (30f / 2.875f).dp
private val NODE_PADDING = 2.75.dp * 1.875f
private val SNAKE_ROW_HEIGHT = NODE_SIZE + NODE_PADDING * 2

/** Half-width horizontal connector segment at the vertical center of the cell. */
@Composable
private fun BoxScope.HorizontalConnector(color: Color, leftHalf: Boolean, alpha: Float) {
    Box(
        Modifier
            .align(if (leftHalf) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxWidth(0.5f)
            .height(STROKE_SIZE)
            .alpha(alpha)
            .background(color),
    )
}

/**
 * Vertical connector segment — the iOS VStack mask splits the cell height
 * equally between the optional clear thirds and the visible run.
 */
@Composable
private fun BoxScope.VerticalConnector(color: Color, topClear: Boolean, bottomClear: Boolean, alpha: Float) {
    Column(Modifier.align(Alignment.Center).fillMaxHeight().width(STROKE_SIZE).alpha(alpha)) {
        if (topClear) Spacer(Modifier.weight(1f))
        Box(Modifier.weight(1f).fillMaxWidth().background(color))
        if (bottomClear) Spacer(Modifier.weight(1f))
    }
}

/** One day node (iOS `ProfileSnakeCalendarNode`). */
@Composable
private fun ProfileSnakeCalendarNode(
    label: Int,
    marked: Boolean,
    enabled: Boolean,
    gradient: Brush,
    isCurrentDay: Boolean,
    animateIn: Boolean,
    isAnimated: Boolean,
    modifier: Modifier = Modifier,
) {
    // Pop-in (iOS `.opacity/.scaleEffect` with a bouncy spring).
    val visible = !animateIn || isAnimated
    val popScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "snakeNodePop",
    )
    val popAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "snakeNodeAlpha")

    val outline: Brush =
        if (marked || isCurrentDay) gradient
        // iOS strokes the box `clear30OpacityGray.opacity(enabled ? 1 : 0.5)`.
        else SolidColor(Clear30Colors.opacityGray.copy(alpha = Clear30Colors.opacityGray.alpha * (if (enabled) 1f else 0.5f)))

    Box(
        // iOS pads the node by strokeSize*1.875 and backs the padded frame with
        // `clear30Button` so the connectors never touch the node.
        modifier
            .size(NODE_SIZE + NODE_PADDING * 2)
            .scale(popScale)
            .alpha(popAlpha)
            .background(Clear30Colors.button),
        contentAlignment = Alignment.Center,
    ) {
        // Glow if today (iOS: gradient-filled rect blurred 6pt behind the node).
        if (isCurrentDay) {
            Box(
                Modifier.size(NODE_SIZE)
                    .blur(6.dp)
                    .background(gradient, RoundedCornerShape(NODE_CORNER)),
            )
        }
        // Outline
        Box(
            Modifier.size(NODE_SIZE)
                .clip(RoundedCornerShape(NODE_CORNER))
                .background(Clear30Colors.button)
                .border(STROKE_SIZE, outline, RoundedCornerShape(NODE_CORNER)),
        )
        // Gradient fill
        if (marked) {
            Box(
                Modifier.size(NODE_SIZE - STROKE_SIZE * 2.5f)
                    .clip(RoundedCornerShape(NODE_CORNER - STROKE_SIZE))
                    .background(gradient),
            )
        }
        // Label — the 0.1 disabled alpha is off the project scale but is the
        // exact iOS value (light scheme), kept for 1:1 parity like CalendarNode.
        SmallText(
            "$label",
            color = if (marked) Color.White else Clear30Colors.text.copy(alpha = if (enabled) 0.5f else 0.1f),
        )
    }
}

// ── Snake day mapping (iOS `day/previousDay/nextDay`) ────────────────────────

private fun snakeDay(i: Int, j: Int): Int {
    if (j == 0) {
        when (i) {
            1 -> return 1
            2 -> return 12
            3 -> return 13
            4 -> return 24
            5 -> return 25
        }
    } else if (j == 5) {
        when (i) {
            1 -> return 6
            2 -> return 7
            3 -> return 18
            4 -> return 19
            5 -> return 30
        }
    }
    return if (i % 2 == 0) (i - 1) * 6 + 6 - j else (i - 1) * 6 + j + 1
}

private fun snakePreviousDay(i: Int, j: Int): Int {
    if (j == 0 && i <= 1) return 1
    return if (i % 2 == 0) snakeDay(i, j + 1) else snakeDay(i, j - 1)
}

private fun snakeNextDay(i: Int, j: Int): Int =
    if (i % 2 != 0) snakeDay(i, j + 1) else snakeDay(i, j - 1)
