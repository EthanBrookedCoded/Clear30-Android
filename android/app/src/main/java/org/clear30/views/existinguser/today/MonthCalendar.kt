package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.views.components.MiniText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors

/**
 * MonthCalendar — a faithful-in-spirit port of MonthCalendarView (Home.swift).
 * Renders the current month as a 7-column grid, coloring each day by its logged
 * check-in: green = didn't smoke, red = smoked, neutral = unlogged/future.
 * (The full iOS calendar — paging months, streaks, custom check-in modes — is
 * layered on in follow-ups.)
 */
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun MonthCalendar(program: Program, modifier: Modifier = Modifier) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
    val daysInMonth = firstOfMonth.daysInMonth()
    // kotlinx DayOfWeek: MON=1..SUN=7 -> grid starts Sunday
    val leadingBlanks = firstOfMonth.dayOfWeek.isoDayNumber % 7

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { d -> Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) { MiniText(d, color = Clear30Colors.text.copy(alpha = 0.5f)) } }
        }

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
                            val date = PlainDate(today.year, today.monthNumber, day)
                            val sober = program.dayInfo[date]?.sober
                            DayCircle(day = day, sober = sober, isToday = day == today.dayOfMonth)
                            dayCounter++
                        }
                    }
                }
            }
        }
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
