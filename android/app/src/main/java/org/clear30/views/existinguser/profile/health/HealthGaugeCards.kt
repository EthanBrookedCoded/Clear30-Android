package org.clear30.views.existinguser.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramHealthProgress
import org.clear30.data.model.UserInfo
import org.clear30.data.model.nextIncreaseCategory
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * HealthCardsRow + HealthGaugeCard — ported 1:1 from iOS `HealthCard.swift`
 * and the `yourProgressView` row in `Profile.swift:260-272`.
 *
 * Each category renders a ~270° gauge (gap at the bottom) at the backend
 * `currentStep.percentage`, with a ghost ring at the personal-best percentage,
 * the category icon + name beneath, and one of two overlay badges: the `hasNew`
 * "+X% >" pill (new step unlocked since last visit — card flips to the category
 * gradient) or the "in X hours" time badge on the single soonest-updating
 * category. Always tappable, even at 0% — routes to that category's timeline.
 */
@Composable
fun HealthCardsRow(
    program: Program,
    userInfo: UserInfo,
    revision: Int = 0,
    onOpenTimeline: (ProgramHealthProgress) -> Unit,
) {
    // Keyed on `revision`: the health models are mutated in place (break
    // mutations shift step dates, opening a timeline stamps lastVisited), so
    // the caller's counter is what invalidates this row — and the remember key
    // keeps the param genuinely used (unused params are excluded from skipping).
    val sorted = remember(revision) { program.healthProgress.sortedBy { it.category.order } }
    if (sorted.isEmpty()) return
    val soonest = program.healthProgress.nextIncreaseCategory
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        sorted.forEach { hp ->
            HealthGaugeCard(
                hp,
                showTimeLeft = soonest?.category?.name == hp.category.name,
                revision = revision,
                onClick = { onOpenTimeline(hp) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Category gradient from the backend hex pair (iOS `ProgramHealthProgress.gradient`). */
internal fun ProgramHealthProgress.gradientBrush(): Brush = Clear30Gradients.linear(
    listOf(colorFromHex(category.color1), colorFromHex(category.color2)),
    Clear30Gradients.bottomLeading,
    Clear30Gradients.topTrailing,
)

internal val ProgramHealthProgress.color1: Color get() = colorFromHex(category.color1)
internal val ProgramHealthProgress.color2: Color get() = colorFromHex(category.color2)

@Composable
private fun HealthGaugeCard(
    healthProgress: ProgramHealthProgress,
    showTimeLeft: Boolean,
    revision: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `healthProgress` is mutated in place — `revision` invalidates the card;
    // the remember key keeps the param genuinely used (unused params are
    // excluded from the skip comparison).
    val percentage = remember(revision) { healthProgress.currentStep?.percentage ?: 0 }
    val gradient = healthProgress.gradientBrush()
    val hasNew = healthProgress.hasNew
    val bestPercentage = healthProgress.bestStep?.percentage

    // iOS wraps the whole card (badge included) in a Button — always tappable,
    // even at 0% (HealthCard.swift:26). pressScale fires the medium haptic.
    Box(modifier.pressScale { onClick() }) {
        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            shadowColor = if (hasNew) darker(healthProgress.color1, 7.5f).copy(alpha = 0.75f) else Clear30Colors.shadow,
            gradient = if (hasNew) gradient else null,
            outlineGradient = if (hasNew) Clear30Gradients.white else null,
            outlineOpacity = 0.5f,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Dimens.cardSpacing / 2)) {
                    HealthGaugeArc(
                        progress = percentage / 100f,
                        strokeBrush = if (hasNew) Clear30Gradients.white else gradient,
                        trackColor = if (hasNew) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray,
                        secondaryProgress = bestPercentage,
                        secondaryColor = if (hasNew) Color.White.copy(alpha = 0.25f)
                        else healthProgress.color1.copy(alpha = 0.25f),
                        secondaryBaseColor = if (hasNew) Color.Transparent else Clear30Colors.button,
                        size = 70.dp,
                    )
                    Heading3("$percentage%")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    Icon(
                        sfSymbol(healthProgress.category.sfSymbol),
                        contentDescription = null,
                        tint = if (hasNew) Color.White else healthProgress.color1,
                        modifier = Modifier.size(15.dp),
                    )
                    SmallText(healthProgress.category.name)
                }
            }
        }
        // Badge overlays — iOS HealthCard.swift:68-84: hasNew → the "+X% >" new
        // badge top-trailing; else the "in X hours" time badge bottom-trailing on
        // the soonest-updating category only. Both rotated -3°, nudged past the corner.
        val newDelta = healthProgress.newDelta
        val nextStepDate = healthProgress.nextStepDate
        if (hasNew && newDelta != null) {
            NewBadge(
                delta = newDelta,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-6).dp).rotate(-3f),
            )
        } else if (!hasNew && showTimeLeft && nextStepDate != null) {
            TimeBadge(
                text = relativeFutureString(nextStepDate),
                gradient = gradient,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 6.dp).rotate(-3f),
            )
        }
    }
}

/** iOS `timeBadge` — bold white ↑ + relative time on the category gradient. */
@Composable
private fun TimeBadge(text: String, gradient: Brush, modifier: Modifier = Modifier) {
    Row(
        modifier
            .cardStyle(gradient = gradient, shadowColor = Color.Transparent, padding = false)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        Icon(sfSymbol("arrow.up"), contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
        MiniText(text, color = Color.White, modifier = Modifier.padding(end = Dimens.cardSpacing / 4))
    }
}

/** iOS `newBadge` — plain pill: ↑ (brand blue; iOS tints with the clear30 gradient) + "+X%" + chevron. */
@Composable
private fun NewBadge(delta: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .cardStyle(shadowColor = Clear30Colors.shadow, padding = false)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            sfSymbol("arrow.up"),
            contentDescription = null,
            tint = Clear30Colors.blue,
            modifier = Modifier.size(9.dp).padding(end = 1.dp),
        )
        MiniText("$delta%", color = Clear30Colors.text, modifier = Modifier.padding(end = Dimens.cardSpacing / 4))
        Icon(
            sfSymbol("chevron.right"),
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.size(7.dp),
        )
    }
}

/** iOS `Date.relativeTimeString()` (future half) — "in 3 hours", "in 2 days", … */
internal fun relativeFutureString(date: Instant): String {
    val seconds = (date - now()).inWholeSeconds.coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365
    return when {
        seconds < 60 -> if (seconds == 1L) "in 1 second" else "in $seconds seconds"
        minutes < 60 -> if (minutes == 1L) "in 1 minute" else "in $minutes minutes"
        hours < 24 -> if (hours == 1L) "in 1 hour" else "in $hours hours"
        days < 7 -> if (days == 1L) "in 1 day" else "in $days days"
        weeks < 4 -> if (weeks == 1L) "in 1 week" else "in $weeks weeks"
        months < 12 -> if (months == 1L) "in 1 month" else "in $months months"
        else -> if (years == 1L) "in 1 year" else "in $years years"
    }
}

/** iOS `Color.darker(by:)` — darken by a percentage. */
private fun darker(color: Color, by: Float): Color {
    val f = 1f - by / 100f
    return Color(color.red * f, color.green * f, color.blue * f, color.alpha)
}

/**
 * iOS `RewardCircularProgressBar` as used by HealthCard: a 270° arc with the
 * gap centered at the bottom, lineWidth 12, round caps, plus the personal-best
 * ghost ring layered between the track and the live fill.
 */
@Composable
private fun HealthGaugeArc(
    progress: Float,
    strokeBrush: Brush,
    trackColor: Color,
    secondaryProgress: Int?,
    secondaryColor: Color,
    secondaryBaseColor: Color,
    size: Dp,
) {
    val fillAmount = 0.75f
    val sweepMax = 360f * fillAmount
    val startAngle = 90f + (360f * (1f - fillAmount)) / 2f // gap centered at the bottom
    Canvas(Modifier.size(size)) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        fun arc(brush: Brush?, color: Color?, sweep: Float) {
            if (sweep <= 0f) return
            if (brush != null) drawArc(
                brush = brush, startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            ) else drawArc(
                color = color!!, startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // Track → personal-best ghost (opaque base + tint) → live fill.
        arc(null, trackColor, sweepMax)
        secondaryProgress?.let { best ->
            val sweep = sweepMax * (best / 100f).coerceIn(0f, 1f)
            arc(null, secondaryBaseColor, sweep)
            arc(null, secondaryColor, sweep)
        }
        arc(strokeBrush, null, sweepMax * progress.coerceIn(0f, 1f))
    }
}
