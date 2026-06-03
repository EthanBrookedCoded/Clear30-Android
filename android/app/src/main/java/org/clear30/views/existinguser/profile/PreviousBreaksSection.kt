package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.previous
import org.clear30.data.model.sorted
import org.clear30.util.daysTo
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * PreviousBreaksSection — ported from the Profile "Previous Breaks" view.
 * Shows every break the user has done: the current/active one on top (in the
 * brand gradient with "Day N of M") and the completed ones below in date order.
 */
@Composable
fun PreviousBreaksSection(program: Program) {
    val sorted = program.breaks.sorted
    if (sorted.isEmpty()) return

    val current = program.currentBreak
    val previous = program.breaks.previous

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Your breaks")
        current?.let { BreakCard(it, isActive = true) }
        previous.reversed().forEach { BreakCard(it, isActive = false) }
    }
}

@Composable
private fun BreakCard(b: ProgramBreak, isActive: Boolean) {
    val totalDays = b.type.raw
    val day = b.currentBreakDay.coerceIn(0, totalDays)
    val dateRange = "${b.startDate.dateLabel()} – ${b.endDate.dateLabel()}"

    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        gradient = if (isActive) Clear30Gradients.clear30 else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                SmallText(
                    text = b.name.ifBlank { b.type.typeName },
                    color = if (isActive) Color.White else Clear30Colors.text,
                )
                TinyText(
                    dateRange,
                    color = if (isActive) Color.White.copy(alpha = 0.5f) else Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.weight(1f))
            // Active: "Day N of M"; completed: just the total length so the user
            // can see at a glance how long it was.
            val tail = if (isActive) "Day $day of $totalDays" else "$totalDays days"
            SmallText(tail, color = if (isActive) Color.White else Clear30Colors.green)
        }
    }
}

private fun kotlinx.datetime.Instant.dateLabel(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.monthNumber}/${d.dayOfMonth}/${d.year % 100}"
}

@Suppress("unused")
private fun ProgramBreak.daysRemaining(): Int = endDate.daysTo(now()).let { (type.raw - currentBreakDay).coerceAtLeast(0) }
