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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.clear30.views.theme.Clear30Gradients

/**
 * WeekCalendar — week-view variant of [MonthCalendar]. Renders a single Mon-Sun
 * row with the day number + colored circle, plus prev/next chevrons to page
 * through previous/next weeks. Designed for the compact "calendar" sheet that
 * the user can swap into on the Today tab when they don't want the full month
 * grid; the iOS app exposed both views as togglable cards.
 *
 * Tapping a day fires [onDayTap] so the parent can open the day-detail sheet —
 * pass null if the consumer doesn't want tap-to-open behavior.
 */
@Composable
fun WeekCalendar(
    program: Program,
    modifier: Modifier = Modifier,
    onDayTap: ((PlainDate) -> Unit)? = null,
) {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    var anchor by remember { mutableStateOf(monday(today)) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Heading3(weekRangeLabel(anchor))
            }
            Box(Modifier.weight(1f))
            ChevronButton("chevron.left") { anchor = anchor.minus(DatePeriod(days = 7)) }
            ChevronButton("chevron.right") { anchor = anchor.plus(DatePeriod(days = 7)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { d ->
                val date = anchor.plus(DatePeriod(days = d))
                val plain = PlainDate(date.year, date.monthNumber, date.dayOfMonth)
                val sober = program.dayInfo[plain]?.sober
                val isToday = date == today
                DayChip(date.dayOfMonth, sober, isToday) { onDayTap?.invoke(plain) }
            }
        }
    }
}

@Composable
private fun ChevronButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(Clear30Colors.opacityGray.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sfSymbol(symbol),
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun DayChip(day: Int, sober: Boolean?, isToday: Boolean, onTap: () -> Unit) {
    val fill = when (sober) {
        true -> Clear30Colors.green
        false -> Clear30Colors.red2
        null -> Clear30Colors.opacityGrayFlattened
    }
    Column(
        Modifier.clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TinyText(
            day.toString(),
            color = Clear30Colors.text.copy(alpha = if (isToday) 1f else 0.5f),
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isToday) Clear30Gradients.clear30 else Clear30Gradients.gray)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                if (sober == true) MiniText("✓", color = Color.White)
                else if (sober == false) MiniText("•", color = Color.White)
            }
        }
    }
}

private fun monday(date: LocalDate): LocalDate =
    date.minus(DatePeriod(days = (date.dayOfWeek.value - 1)))

private fun weekRangeLabel(monday: LocalDate): String {
    val sunday = monday.plus(DatePeriod(days = 6))
    val mLabel = "${monday.monthNumber}/${monday.dayOfMonth}"
    val sLabel = if (sunday.month == monday.month) "${sunday.dayOfMonth}" else "${sunday.monthNumber}/${sunday.dayOfMonth}"
    return "$mLabel – $sLabel"
}

@Suppress("unused") private fun RoundedHints() = RoundedCornerShape(0.dp)
