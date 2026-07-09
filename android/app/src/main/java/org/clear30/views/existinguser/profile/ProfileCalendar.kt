package org.clear30.views.existinguser.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.util.now
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.StandardCalendarNode
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

private val PROFILE_CAL_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * ProfileCalendar — port of iOS `ProfileRomanCalendar`. A card showing a month
 * grid of the user's days: sober days fill with the clear30 gradient, every other
 * past day reads gray-flat, today is outlined, future days are dimmed. Left/right
 * chevrons page between the program's first month and the current month.
 *
 * Placed in the Profile tab above the "days without weed" / "money saved" stats,
 * matching iOS `lifeLayout`.
 */
@Composable
fun ProfileCalendar(program: Program) {
    val today = remember { PlainDate.from(now()) }
    val startMonth = remember { firstOfMonth(PlainDate.from(program.startDate)) }
    val currentMonth = remember { firstOfMonth(today) }
    // Months are 0-indexed from the program's first month; default to the latest.
    val totalMonths = remember { startMonth.monthsTo(currentMonth).coerceAtLeast(0) }
    var index by remember { mutableStateOf(totalMonths) }

    val anchor = startMonth.adding(months = index)
    val canPrev = index > 0
    val canNext = index < totalMonths

    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            // Month heading
            AnimatedContent(
                targetState = anchor,
                transitionSpec = { monthSlide(targetState > initialState) },
                label = "profileCalHeading",
            ) { a ->
                SmallText(monthLabel(a, today), color = Clear30Colors.text.copy(alpha = 0.5f))
            }

            // Month grid (slides left/right as you page).
            AnimatedContent(
                targetState = anchor,
                transitionSpec = { monthSlide(targetState > initialState) },
                label = "profileCalGrid",
            ) { a ->
                MonthNodes(program, a, today)
            }

            // Nav chevrons (only when there's more than one month of history).
            if (totalMonths > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.End),
                ) {
                    NavChevron("chevron.left", enabled = canPrev) { if (canPrev) { Haptics.lightImpact(); index-- } }
                    NavChevron("chevron.right", enabled = canNext) { if (canNext) { Haptics.lightImpact(); index++ } }
                }
            }
        }
    }
}

@Composable
private fun NavChevron(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(7.dp))
            .background(Clear30Colors.opacityGray)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
    }
}

/** Month grid of day nodes for [anchor]'s month, Sunday-first with leading blanks. */
@Composable
private fun MonthNodes(program: Program, anchor: PlainDate, today: PlainDate) {
    val first = firstOfMonth(anchor)
    val daysInMonth = first.daysTo(first.adding(months = 1))
    val leadingBlanks = LocalDate(first.year, first.month, 1).dayOfWeek.isoDayNumber % 7 // Sunday-first
    val rows = (leadingBlanks + daysInMonth + 6) / 7
    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - leadingBlanks + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = PlainDate(first.year, first.month, dayNum)
                        val sober = program.dayInfo[date]?.sober == true
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            StandardCalendarNode(
                                label = dayNum,
                                enabled = date <= today,
                                today = date == today,
                                fillStyle = if (sober) {
                                    CalendarNodeFillStyle.Gradient(Clear30Gradients.clear30)
                                } else {
                                    CalendarNodeFillStyle.GrayFlat
                                },
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Date helpers ─────────────────────────────────────────────────────────────

private fun firstOfMonth(d: PlainDate): PlainDate = PlainDate(d.year, d.month, 1)

/** Whole months from [this] to [other] (both first-of-month). */
private fun PlainDate.monthsTo(other: PlainDate): Int =
    (other.year - year) * 12 + (other.month - month)

/** "June" in the current year, else "June 2025". */
private fun monthLabel(anchor: PlainDate, today: PlainDate): String {
    val name = PROFILE_CAL_MONTHS[anchor.month - 1]
    return if (anchor.year == today.year) name else "$name ${anchor.year}"
}

private fun monthSlide(forward: Boolean): androidx.compose.animation.ContentTransform =
    if (forward) {
        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
    } else {
        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
    }
