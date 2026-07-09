package org.clear30.views.existinguser.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramDayInfo
import org.clear30.util.adding
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GiganticText
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * CheckInDayCard — ported from CheckInDayCard.swift (top level shape). Renders
 * the day's check-in state as the hero card of the Today feed:
 *
 *   • Unlogged today  → flat card with "How did today go?" + check-in button
 *   • Sober logged    → brand-gradient card with leaf emoji + status text
 *   • Smoked logged   → flat card with "Logged a slip" + emoji
 *   • Past day        → read-only status display
 *
 * The full per-method puff counter + amount editing UI lives in CheckInSheet
 * — this card is the headline, the sheet is the editor.
 */
@Composable
fun CheckInDayCard(
    selectedDay: PlainDate,
    program: Program,
    showStreak: Boolean,
    onCheckIn: () -> Unit,
) {
    val info: ProgramDayInfo? = program.dayInfo[selectedDay]
    val isToday = selectedDay == PlainDate.from(now())
    val sober = info?.sober

    when (sober) {
        true -> SoberCard(selectedDay, isToday, program, showStreak)
        false -> SmokedCard(selectedDay, isToday)
        null -> UnloggedCard(isToday, onCheckIn)
    }
}

@Composable
private fun SoberCard(date: PlainDate, isToday: Boolean, program: Program, showStreak: Boolean) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        gradient = Clear30Gradients.clear30,
        outlineGradient = Clear30Gradients.white,
        outlineOpacity = 0.5f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            DateBadge(date, isToday)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                GiganticText("🌱", color = Color.White)
                Column(Modifier.weight(1f)) {
                    SmallText(
                        if (isToday) "You stayed clear today" else "You stayed clear",
                        color = Color.White,
                    )
                    if (showStreak) {
                        val streak = currentSoberStreakLocal(program)
                        if (streak > 0) {
                            TinyText(
                                "Day $streak of staying clear",
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmokedCard(date: PlainDate, isToday: Boolean) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        outlineGradient = Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            DateBadge(date, isToday)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                GiganticText("💨")
                Column(Modifier.weight(1f)) {
                    SmallText(if (isToday) "Logged a slip today" else "Logged a slip")
                    TinyText(
                        "A slip is data, not a failure.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnloggedCard(isToday: Boolean, onCheckIn: () -> Unit) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        outlineGradient = Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TinyText(
                        if (isToday) "Today" else "Earlier",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                    SmallText(if (isToday) "How did today go?" else "Not logged")
                }
                if (isToday) {
                    org.clear30.views.components.DefaultButton(
                        title = "Check in",
                        gradient = Clear30Gradients.clear30,
                        onClick = onCheckIn,
                    )
                }
            }
        }
    }
}

/**
 * DateBadge — the date badge at the top-left of the day card. iOS
 * (CheckInDayCard.swift:153-173) renders it as a full CardStyle pill — clear30
 * gradient, white outline at 0.5, shadow — regardless of the day's logged
 * state, with a white SmallText title ("Today" / short date).
 */
@Composable
private fun DateBadge(date: PlainDate, isToday: Boolean) {
    Box(
        Modifier
            .cardStyle(
                gradient = Clear30Gradients.clear30,
                outlineGradient = Clear30Gradients.white,
                outlineOpacity = 0.5f,
                padding = false,
            )
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
    ) {
        SmallText(
            if (isToday) "Today" else "${MONTHS_SHORT[date.month - 1]} ${date.day}",
            color = Color.White,
        )
    }
}

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** Streak helper — kept inside this file so it doesn't leak past the card. */
private fun currentSoberStreakLocal(program: Program): Int {
    var streak = 0
    var d = now()
    if (program.dayInfo[PlainDate.from(d)]?.sober != true) d = d.adding(days = -1)
    while (program.dayInfo[PlainDate.from(d)]?.sober == true) {
        streak++
        d = d.adding(days = -1)
    }
    return streak
}
