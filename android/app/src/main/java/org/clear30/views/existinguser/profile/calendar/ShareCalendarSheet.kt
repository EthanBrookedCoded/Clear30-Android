package org.clear30.views.existinguser.profile

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * ShareCalendarRow — ported from the iOS "Share calendar" entry on Profile.
 * Tapping fires the system share sheet with a plain-text summary of the
 * user's program: total days clear, current streak, and a per-week sober/slip
 * tally so the recipient can see momentum at a glance.
 *
 * Why plain text rather than a calendar file: the iOS app shared an image of
 * the calendar grid; rendering that PNG on Android would require an offscreen
 * Canvas snapshot. Text is honest about the data and ships now — the image
 * version is a follow-up once the calendar component exposes a snapshot API.
 */
@Composable
fun ShareCalendarRow(program: Program, userInfo: UserInfo) {
    val context = LocalContext.current
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val summary = buildCalendarSummary(program)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "My Clear30 progress")
                putExtra(Intent.EXTRA_TEXT, summary)
            }
            context.startActivity(Intent.createChooser(intent, "Share progress").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Logger.logEvent(userInfo.loggingID, LogEventType.sharedCalendar)
        },
        gradient = Clear30Gradients.clear30,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(
                sfSymbol("square.and.arrow.up"),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(8.dp),
            )
            Column {
                Heading3("Share your progress", color = androidx.compose.ui.graphics.Color.White)
                TinyText("A quick summary you can text or post", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

/**
 * Build the plain-text summary. We avoid week boundaries (timezone math is
 * brittle) and instead emit running totals + the last-7-days strip so it
 * captures momentum without overstating precision.
 */
private fun buildCalendarSummary(program: Program): String {
    val sober = program.dayInfo.values.count { it.sober == true }
    val smoked = program.dayInfo.values.count { it.sober == false }
    val tracked = program.dayInfo.size
    val recent = program.dayInfo.entries.sortedBy { it.key }.takeLast(7)
    val strip = recent.joinToString(separator = " ") {
        when (it.value.sober) {
            true -> "🟢"
            false -> "🔴"
            null -> "⚪"
        }
    }
    return buildString {
        append("Clear30 progress:\n")
        append("• $sober days clear\n")
        append("• $smoked slip-ups\n")
        append("• $tracked days tracked\n")
        if (strip.isNotBlank()) append("Last 7 days: $strip\n")
        append("\nJoin me at https://clear30.org")
    }
}

