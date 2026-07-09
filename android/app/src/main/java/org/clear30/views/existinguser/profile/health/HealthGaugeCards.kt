package org.clear30.views.existinguser.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import org.clear30.data.model.HealthCategory
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.Dp

/**
 * HealthCardsRow — ported 1:1 from the iOS `HealthCard` row in
 * `Profile.swift` `yourProgressView`. A horizontal row of white cards, each a
 * ~270° circular gauge (open at the bottom) showing the recovery percentage,
 * with the category icon + name beneath.
 *
 * The Android program model stores health as per-milestone rows (no backend
 * per-step percentage), so the percentage is derived from how many of a
 * category's milestones have unlocked. When no health data has synced yet we
 * still render Brain / Lungs / Heart so the layout matches iOS visually.
 */
@Composable
fun HealthCardsRow(program: Program, userInfo: UserInfo) {
    val gauges = buildGauges(program)
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        gauges.take(3).forEach { g ->
            HealthGaugeCard(g, Modifier.weight(1f))
        }
    }
}

private data class HealthGauge(
    val name: String,
    val icon: String,
    val percentage: Int,
    val gradient: Brush,
    val tint: Color,
    /** When this gauge next ticks up — drives the iOS-style time badge. */
    val nextIncrease: Instant? = null,
)

private fun buildGauges(program: Program): List<HealthGauge> {
    // Drive everything off DAYS WITHOUT WEED so the gauges climb as the streak grows.
    val days = program.lastSmoked.daysTo(now()).coerceAtLeast(0)
    val categories = program.healthProgress.map { it.category }.distinct()
    if (categories.isNotEmpty()) {
        return categories.sortedBy { it.ordinal }.map { cat ->
            val items = program.healthProgress.filter { it.category == cat }
            val unlocked = items.count { days - it.setbackDays >= it.unlockedOnDay }
            val pct = if (items.isEmpty()) 0 else (unlocked * 100 / items.size)
            // Earliest still-locked milestone → its unlock date (iOS nextStepDate).
            val next = items.filter { days - it.setbackDays < it.unlockedOnDay }
                .minOfOrNull { it.unlockedOnDay + it.setbackDays }
                ?.let { program.lastSmoked.adding(days = it) }
            cat.toGauge(pct, next)
        }
    }
    // No synced data — ramp each organ up over its own recovery window so the
    // percentages rise as days-without-weed accumulate (Brain slowest → Heart fastest),
    // all reaching 100% over time (e.g. 238 days → all 100%).
    fun pctOver(window: Int) = (days * 100 / window).coerceIn(0, 100)
    // The ramp steps once per day, so the next increase is the next day boundary.
    fun nextOver(window: Int): Instant? =
        if (days >= window) null else program.lastSmoked.adding(days = days + 1)
    return listOf(
        HealthCategory.BRAIN.toGauge(pctOver(90), nextOver(90)),
        HealthCategory.LUNGS.toGauge(pctOver(30), nextOver(30)),
        HealthCategory.OTHER.toGauge(pctOver(14), nextOver(14)),
    )
}

private fun HealthCategory.toGauge(pct: Int, next: Instant? = null): HealthGauge = when (this) {
    HealthCategory.BRAIN -> HealthGauge("Brain", "brain.head.profile", pct, pinkGradient, pink1, next)
    HealthCategory.LUNGS -> HealthGauge("Lungs", "lungs.fill", pct, salmonGradient, salmon1, next)
    HealthCategory.SLEEP -> HealthGauge("Sleep", "moon.fill", pct, sleepGradient, sleep1, next)
    HealthCategory.MOOD -> HealthGauge("Mood", "face.smiling", pct, moodGradient, mood1, next)
    HealthCategory.ENERGY -> HealthGauge("Energy", "bolt.fill", pct, energyGradient, energy1, next)
    HealthCategory.MEMORY -> HealthGauge("Memory", "sparkles", pct, memoryGradient, memory1, next)
    HealthCategory.OTHER -> HealthGauge("Heart", "heart.fill", pct, redGradient, red1, next)
}

// Category colors (iOS health categories are backend-defined; these match the
// brand palette in the reference screenshots).
private val pink1 = Color(0xFFEC6AAE)
private val pinkGradient = Brush.linearGradient(listOf(pink1, Color(0xFFF488BC)))
private val salmon1 = Color(0xFFF47C7C)
private val salmonGradient = Brush.linearGradient(listOf(salmon1, Color(0xFFF96B6B)))
private val red1 = Color(0xFFF65555)
private val redGradient = Brush.linearGradient(listOf(red1, Color(0xFFFB5151)))
private val sleep1 = Color(0xFF7C6FF4)
private val sleepGradient = Brush.linearGradient(listOf(sleep1, Color(0xFF9B8BF6)))
private val mood1 = Color(0xFF5BB4A9)
private val moodGradient = Brush.linearGradient(listOf(mood1, Color(0xFF6FD3A0)))
private val energy1 = Color(0xFFF5A623)
private val energyGradient = Brush.linearGradient(listOf(energy1, Color(0xFFF7B84D)))
private val memory1 = Color(0xFF9B59E8)
private val memoryGradient = Brush.linearGradient(listOf(memory1, Color(0xFFB37CF0)))

@Composable
private fun HealthGaugeCard(gauge: HealthGauge, modifier: Modifier = Modifier) {
    Box(modifier) {
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Dimens.cardSpacing / 2)) {
                    GaugeArc(gauge.percentage / 100f, gauge.gradient, size = 70.dp)
                    Heading3("${gauge.percentage}%")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Icon(sfSymbol(gauge.icon), contentDescription = null, tint = gauge.tint, modifier = Modifier.size(15.dp))
                    SmallText(gauge.name)
                }
            }
        }
        // Time-to-next-increase badge — iOS `timeBadge` overlay: a gradient pill
        // rotated -3°, sticking slightly past the bottom-trailing corner.
        gauge.nextIncrease?.let { next ->
            TimeBadge(
                text = relativeFutureString(next),
                gradient = gauge.gradient,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 6.dp)
                    .rotate(-3f),
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

/** iOS `Date.relativeTimeString()` (future half) — "in 3 hours", "in 2 days", … */
private fun relativeFutureString(date: Instant): String {
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

/** Full 360° progress ring (iOS `RewardCircularProgressBar`, lineWidth 12), filling clockwise from the top. */
@Composable
private fun GaugeArc(progress: Float, gradient: Brush, size: Dp) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(Modifier.size(size)) {
        val stroke = 12f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        // Track (full ring)
        drawArc(
            color = Clear30Colors.opacityGray,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        // Fill — clockwise from the top
        if (p > 0f) {
            drawArc(
                brush = gradient,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
