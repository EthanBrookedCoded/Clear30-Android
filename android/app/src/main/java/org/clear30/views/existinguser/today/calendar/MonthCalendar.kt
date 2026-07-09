package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors

/**
 * MonthCalendar — month grid for the Today tab, ported in spirit from
 * MonthCalendarView (Home.swift). Now supports paging back/forward through
 * months via header arrows, and a small "Today" pill that returns to the
 * current month after the user has navigated away.
 *
 * Each day cell colors by the logged check-in (green = sober, red = smoked,
 * neutral = unlogged), with today bordered for emphasis.
 */
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@Composable
fun MonthCalendar(program: Program, modifier: Modifier = Modifier, onDayTap: ((PlainDate) -> Unit)? = null) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var anchor by remember { mutableStateOf(LocalDate(today.year, today.monthNumber, 1)) }

    Column(modifier.fillMaxWidth()) {
        // Header — month label + prev/next arrows + "Today" pill when off-month.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading3("${MONTH_NAMES[anchor.monthNumber - 1]} ${anchor.year}")
            Box(Modifier.weight(1f))
            if (anchor.month != today.month || anchor.year != today.year) {
                TinyText(
                    "Today",
                    Modifier
                        .clip(CircleShape)
                        .background(Clear30Colors.green.copy(alpha = 0.25f))
                        .clickable { anchor = LocalDate(today.year, today.monthNumber, 1) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Clear30Colors.green,
                )
            }
            ArrowButton("chevron.left") { anchor = anchor.minus(DatePeriod(months = 1)) }
            ArrowButton("chevron.right") { anchor = anchor.plus(DatePeriod(months = 1)) }
        }

        // Weekday strip.
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { d ->
                Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    MiniText(d, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }

        // Day grid.
        val daysInMonth = anchor.daysInMonth()
        // DayOfWeek.value gives ISO 1..7 (Mon..Sun); shift so Sunday = 0.
        val leadingBlanks = anchor.dayOfWeek.value % 7
        val cells = leadingBlanks + daysInMonth
        val rows = (cells + 6) / 7
        var dayCounter = 1
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (cellIndex >= leadingBlanks && dayCounter <= daysInMonth) {
                            val day = dayCounter
                            val date = PlainDate(anchor.year, anchor.monthNumber, day)
                            val sober = program.dayInfo[date]?.sober
                            val isToday = anchor.year == today.year && anchor.monthNumber == today.monthNumber && day == today.dayOfMonth
                            Box(modifier = androidx.compose.ui.Modifier.clickable(enabled = onDayTap != null) { onDayTap?.invoke(date) }) {
                                DayCircle(day, sober, isToday)
                            }
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Clear30Colors.opacityGray.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sfSymbol(symbol),
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.75f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DayCircle(day: Int, sober: Boolean?, isToday: Boolean) {
    val fill = when (sober) {
        true -> Clear30Colors.green
        false -> Clear30Colors.red2
        null -> Clear30Colors.opacityGrayFlattened
    }
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        TinyText(
            day.toString(),
            color = if (sober != null) Color.White else Clear30Colors.text.copy(alpha = if (isToday) 1f else 0.5f),
        )
    }
}

private fun LocalDate.daysInMonth(): Int {
    val nextMonth = if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return nextMonth.toEpochDays() - LocalDate(year, monthNumber, 1).toEpochDays()
}
