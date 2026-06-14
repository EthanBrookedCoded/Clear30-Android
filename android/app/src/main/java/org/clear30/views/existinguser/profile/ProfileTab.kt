package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.clear30.data.model.Program
import org.clear30.data.model.PlainDate
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.util.adding
import org.clear30.util.now

/**
 * ProfileTab — ported from Profile.swift (stats overview). Computes the headline
 * metrics live from [Program.dayInfo]: total sober days, current sober streak,
 * days tracked, and today's sessions. The full Profile (health timeline,
 * achievements grid, journal, previous breaks, settings) is layered on next.
 */
@Composable
fun ProfileTab(
    userInfo: UserInfo,
    program: Program,
    journalEntries: org.clear30.data.model.JournalEntries,
    onSignOut: () -> Unit,
) {
    val soberDays = program.dayInfo.values.count { it.sober == true }
    val smokedDays = program.dayInfo.values.count { it.sober == false }
    val daysTracked = program.dayInfo.size
    val streak = currentSoberStreak(program)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                Heading1(userInfo.emoji ?: "😁")
                Heading1(userInfo.name.ifBlank { "You" })
            }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            StatCard("Current streak", "$streak", "days", Modifier.weight(1f))
            StatCard("Days clear", "$soberDays", "total", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            StatCard("Days tracked", "$daysTracked", "logged", Modifier.weight(1f))
            StatCard("Slip-ups", "$smokedDays", "days", Modifier.weight(1f))
        }

        DopamineTimer(program, userInfo)

        PreviousBreaksSection(program)

        JournalSection(journalEntries, userInfo)

        AchievementsSection(userInfo)

        HealthTimelineSection(program, userInfo)

        SymptomsSection(userInfo)

        ProgramStartDatePicker(program, userInfo)

        ShareCalendarRow(program, userInfo)

        SettingsSection(userInfo, onSignOut)
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SmallText(title, color = Clear30Colors.text.copy(alpha = 0.5f))
            GiganticText(value)
            SmallText(unit, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

/** Consecutive sober days ending today (or yesterday) — like the iOS streak calc. */
private fun currentSoberStreak(program: Program): Int {
    var streak = 0
    var date = now()
    // Allow today to be unlogged without breaking the streak.
    if (program.dayInfo[PlainDate.from(date)]?.sober != true) date = date.adding(days = -1)
    while (program.dayInfo[PlainDate.from(date)]?.sober == true) {
        streak++
        date = date.adding(days = -1)
    }
    return streak
}
