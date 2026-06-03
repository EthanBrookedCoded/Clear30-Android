package org.clear30.views.existinguser.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.PlainDate
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * GroupCalendar — the group-aggregate version of [MonthCalendar]. Each
 * day-cell is shaded by the fraction of members who logged a sober check-in
 * (green) vs smoked (red), the cell label is the count of members reporting.
 *
 * Read-only: the user logs *their* check-in via the Today tab; this view
 * surfaces the aggregate so the group can see momentum. Matches the iOS
 * Group calendar's intent without yet drilling into the per-member detail.
 */
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun GroupCalendar(group: Clear30Group, userInfo: UserInfo, modifier: Modifier = Modifier) {
    LaunchedEffect(group.id) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedGroupCalendar)
    }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
    val daysInMonth = firstOfMonth.daysInMonth()
    val leadingBlanks = firstOfMonth.dayOfWeek.isoDayNumber % 7

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
        Heading3("Group calendar")
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { d ->
                Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    MiniText(d, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
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
                            val (sober, smoked) = countForDay(group, today.year, today.monthNumber, day)
                            AggregateCircle(day, sober, smoked, isToday = day == today.dayOfMonth)
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

/** Tally sober/smoked check-ins across the group for a single PlainDate. */
private fun countForDay(group: Clear30Group, year: Int, month: Int, day: Int): Pair<Int, Int> {
    // Member dayInfo is keyed by the canonical PlainDate string (yyyy-MM-dd),
    // not data-class toString. Use .dateString to match the serialized key.
    val key = PlainDate(year, month, day).dateString
    var sober = 0
    var smoked = 0
    group.members.forEach { m ->
        when (m.dayInfo?.get(key)?.sober) {
            true -> sober++
            false -> smoked++
            null -> Unit
        }
    }
    return sober to smoked
}

@Composable
private fun AggregateCircle(day: Int, sober: Int, smoked: Int, isToday: Boolean) {
    val net = sober - smoked
    val fill = when {
        sober == 0 && smoked == 0 -> Clear30Colors.opacityGrayFlattened
        net > 0 -> Clear30Colors.green.copy(alpha = (0.4f + 0.6f * (sober / 5f)).coerceAtMost(1f))
        net < 0 -> Clear30Colors.red2.copy(alpha = (0.4f + 0.6f * (smoked / 5f)).coerceAtMost(1f))
        else -> Clear30Colors.opacityGray
    }
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        TinyText(
            day.toString(),
            color = if (sober > 0 || smoked > 0) Color.White
                else Clear30Colors.text.copy(alpha = if (isToday) 1f else 0.5f),
        )
    }
}

private fun LocalDate.daysInMonth(): Int {
    val nextMonth = if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return nextMonth.toEpochDays() - LocalDate(year, monthNumber, 1).toEpochDays()
}
