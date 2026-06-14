package org.clear30.views.existinguser.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

    // Tapping a row opens a detail panel — full-bleed, system back closes it.
    var detail by remember { mutableStateOf<ProgramBreak?>(null) }
    detail?.let { b ->
        androidx.activity.compose.BackHandler { detail = null }
        BreakDetailScreen(b, isActive = b === current, program = program, onBack = { detail = null })
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Your breaks")
        current?.let { BreakCard(it, isActive = true) { detail = it } }
        previous.reversed().forEach { b -> BreakCard(b, isActive = false) { detail = b } }
    }
}

@Composable
private fun BreakCard(b: ProgramBreak, isActive: Boolean, onClick: () -> Unit) {
    val totalDays = b.type.raw
    val day = b.currentBreakDay.coerceIn(0, totalDays)
    val dateRange = "${b.startDate.dateLabel()} – ${b.endDate.dateLabel()}"

    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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

/**
 * BreakDetailScreen — drill-in for a single [ProgramBreak]. Shows the headline
 * stats (sober days, slip-ups, day length), a progress bar through the break,
 * and a date band. Read-only — editing/deleting a break is a follow-up.
 */
@Composable
private fun BreakDetailScreen(b: ProgramBreak, isActive: Boolean, program: Program, onBack: () -> Unit) {
    val totalDays = b.type.raw
    val day = b.currentBreakDay.coerceIn(0, totalDays)
    // Per-day counts come off the Program's dayInfo, restricted to this
    // break's date window — ProgramBreak doesn't carry its own day map.
    val startPlain = org.clear30.data.model.PlainDate.from(b.startDate)
    val endPlain = org.clear30.data.model.PlainDate.from(b.endDate)
    val inWindow = program.dayInfo.filter { (d, _) -> d in startPlain..endPlain }.values
    val sober = inWindow.count { it.sober == true }
    val smoked = inWindow.count { it.sober == false }
    val progress = if (totalDays > 0) day.toFloat() / totalDays else 0f

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            org.clear30.views.components.IconButton("chevron.backward", onClick = onBack)
            org.clear30.views.components.Heading1(b.name.ifBlank { b.type.typeName })
        }
        TinyText(
            "${b.startDate.dateLabel()} – ${b.endDate.dateLabel()}",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        Clear30Card(
            modifier = Modifier.fillMaxWidth(),
            gradient = if (isActive) Clear30Gradients.clear30 else null,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallText(
                        if (isActive) "Day $day of $totalDays" else "Completed $totalDays-day break",
                        color = if (isActive) Color.White else Clear30Colors.text,
                    )
                    Spacer(Modifier.weight(1f))
                    SmallText(
                        "${(progress * 100).toInt()}%",
                        color = if (isActive) Color.White else Clear30Colors.green,
                    )
                }
                ProgressBar(progress, isActive)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            StatCard("Days clear", sober.toString(), Modifier.weight(1f))
            StatCard("Slip-ups", smoked.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TinyText(title, color = Clear30Colors.text.copy(alpha = 0.5f))
            org.clear30.views.components.GiganticText(value)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, isActive: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(4.dp))) {
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(if (isActive) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray),
        )
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp)
                .background(if (isActive) Color.White else Clear30Colors.green),
        )
    }
}

private fun kotlinx.datetime.Instant.dateLabel(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.monthNumber}/${d.dayOfMonth}/${d.year % 100}"
}

@Suppress("unused")
private fun ProgramBreak.daysRemaining(): Int = endDate.daysTo(now()).let { (type.raw - currentBreakDay).coerceAtLeast(0) }
