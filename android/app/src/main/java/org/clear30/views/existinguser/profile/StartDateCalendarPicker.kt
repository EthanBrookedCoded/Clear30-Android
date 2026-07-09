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
import androidx.compose.foundation.layout.padding
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
import org.clear30.util.now
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.StandardCalendarNode
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * StartDateCalendarPicker — port of iOS `ProgramStartDatePicker`: a self-contained
 * calendar card for picking a break's start date. A month header + close button,
 * weekday labels, a swipeable month grid where selectable days (range
 * [today−[minOffset], today+[maxOffset]]) read as gray nodes and the picked day
 * gets the clear30 gradient ring, and a confirm button that shows the relative-day
 * label ("In 3 days" / "Today") once a new date is chosen. Replaces the plain
 * Material `DatePicker` dialog for a friendlier, on-brand UX.
 *
 * [onResult] fires with the chosen new start date, or null on cancel/close.
 */
@Composable
fun StartDateCalendarPicker(
    currentStartDate: PlainDate,
    minOffsetDays: Int = -28,
    maxOffsetDays: Int = 12,
    onResult: (PlainDate?) -> Unit,
) {
    val today = remember { PlainDate.from(now()) }
    val minDate = remember { today.adding(days = minOffsetDays) }
    val maxDate = remember { today.adding(days = maxOffsetDays) }
    val startMonth = remember { firstOfMonth(minDate) }
    val totalMonths = remember { startMonth.monthsTo(firstOfMonth(maxDate)).coerceAtLeast(0) }
    var index by remember { mutableStateOf(startMonth.monthsTo(firstOfMonth(today)).coerceIn(0, totalMonths)) }
    var selected by remember { mutableStateOf<PlainDate?>(null) }

    val anchor = startMonth.adding(months = index)
    val changed = selected != null && selected != currentStartDate
    val canPrev = index > 0
    val canNext = index < totalMonths

    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            // Header: month name + close.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(targetState = anchor, transitionSpec = { monthSlide(targetState > initialState) }, label = "sdpHeading") { a ->
                    SmallText(monthLabel(a, today), color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                Spacer(Modifier.weight(1f))
                CloseButton { onResult(null) }
            }

            // Weekday labels, centered over each day column.
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyText(d, color = Clear30Colors.text.copy(alpha = 0.25f))
                    }
                }
            }

            // Month grid.
            AnimatedContent(targetState = anchor, transitionSpec = { monthSlide(targetState > initialState) }, label = "sdpGrid") { a ->
                MonthGrid(a, today, minDate, maxDate, selected) { d ->
                    selected = if (selected == d) null else d
                }
            }

            // Month navigation (only when the range spans multiple months).
            if (totalMonths > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.End)) {
                    NavChevron("chevron.left", canPrev) { if (canPrev) { Haptics.lightImpact(); index-- } }
                    NavChevron("chevron.right", canNext) { if (canNext) { Haptics.lightImpact(); index++ } }
                }
            }

            // Confirm — disabled/dimmed until a new date is chosen.
            val delta = selected?.let { today.daysTo(it) } ?: 0
            StretchedButton(
                title = if (changed) relativeDayDescription(delta) else "Select new start date",
                gradient = if (changed) Clear30Gradients.clear30 else null,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing).alpha(if (changed) 1f else 0.5f),
            ) { if (changed) selected?.let(onResult) }
        }
    }
}

@Composable
private fun MonthGrid(
    anchor: PlainDate,
    today: PlainDate,
    minDate: PlainDate,
    maxDate: PlainDate,
    selected: PlainDate?,
    onSelect: (PlainDate) -> Unit,
) {
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
                        val selectable = date in minDate..maxDate
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            StandardCalendarNode(
                                label = dayNum,
                                enabled = selectable,
                                selected = selected == date,
                                today = date == today,
                                fillStyle = if (selectable) CalendarNodeFillStyle.Gray else CalendarNodeFillStyle.LowOpacity,
                                selectedGradient = Clear30Gradients.clear30,
                                onTap = if (selectable) ({ onSelect(date) }) else null,
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

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Clear30Colors.opacityGray).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol("xmark"), contentDescription = "Close", tint = Clear30Colors.text.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun NavChevron(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(7.dp))
            .background(Clear30Colors.opacityGray).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
    }
}

// iOS `Date.relativeDayDescription()` — "Today" / "Tomorrow" / "In N days" / "N days ago".
private fun relativeDayDescription(deltaDays: Int): String = when {
    deltaDays == 0 -> "Today"
    deltaDays == 1 -> "Tomorrow"
    deltaDays == -1 -> "Yesterday"
    deltaDays > 1 -> "In $deltaDays days"
    else -> "${-deltaDays} days ago"
}

private fun firstOfMonth(d: PlainDate): PlainDate = PlainDate(d.year, d.month, 1)

private fun PlainDate.monthsTo(other: PlainDate): Int = (other.year - year) * 12 + (other.month - month)

private fun monthLabel(anchor: PlainDate, today: PlainDate): String {
    val name = MONTHS[anchor.month - 1]
    return if (anchor.year == today.year) name else "$name ${anchor.year}"
}

private fun monthSlide(forward: Boolean): androidx.compose.animation.ContentTransform =
    if (forward) {
        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
    } else {
        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
    }
