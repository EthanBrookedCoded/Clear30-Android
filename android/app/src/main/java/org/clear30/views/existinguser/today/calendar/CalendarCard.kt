package org.clear30.views.existinguser.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.util.now
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.StandardCalendarNode
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val weekdayInitials = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * CalendarCard — the current month as a 7-column grid of [StandardCalendarNode]s:
 * sober days fill with the brand gradient, slips fill red, untracked days stay
 * gray, and future days are disabled. Tapping a day calls [onDayTap].
 *
 * This is the first-cut Today calendar. Month/week paging, the Month↔Week toggle,
 * stage markers, and the CalendarViewModel (iOS `Today/Calendar`) are Batch 3.
 */
@Composable
fun CalendarCard(
    program: Program,
    onDayTap: (PlainDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = PlainDate.from(now())
    val year = today.year
    val month = today.month
    val firstOfMonth = PlainDate(year, month, 1)
    val daysInMonth = firstOfMonth.daysTo(firstOfMonth.adding(months = 1))
    val leadingBlanks = LocalDate(year, month, 1).dayOfWeek.isoDayNumber % 7 // Sunday-first

    Clear30Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Heading3("${monthNames[month - 1]} $year")

            Row(Modifier.fillMaxWidth()) {
                weekdayInitials.forEach { initial ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyText(initial, color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                }
            }

            val totalCells = leadingBlanks + daysInMonth
            val rows = (totalCells + 6) / 7
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = row * 7 + col - leadingBlanks + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = PlainDate(year, month, dayNum)
                            val fill = when (program.dayInfo[date]?.sober) {
                                true -> CalendarNodeFillStyle.Gradient(Clear30Gradients.clear30)
                                false -> CalendarNodeFillStyle.Gradient(Clear30Gradients.red)
                                null -> CalendarNodeFillStyle.Gray
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                StandardCalendarNode(
                                    label = dayNum,
                                    today = date == today,
                                    enabled = date <= today,
                                    fillStyle = fill,
                                    onTap = { onDayTap(date) },
                                )
                            }
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
