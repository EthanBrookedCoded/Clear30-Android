package org.clear30.views.existinguser.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.CheckInLogger
import org.clear30.data.model.Program
import org.clear30.data.model.PlainDate
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.util.now

/**
 * TodayTab — ported from Home.swift (the Today/Home screen). Shows the live day
 * badge (Program.getBadgeInfo), the current program summary, and the check-in /
 * calendar / feed sections.
 *
 * The calendar (MonthCalendarView/CalendarViewModel), check-in flow, and content
 * feed (TodayFeedView) are deep subsystems rendered here as placeholders, to be
 * filled in follow-up turns; the header + program data are real.
 */
@Composable
fun TodayTab(program: Program, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    val logger = remember { CheckInLogger(program, userInfo, scope) }
    var refresh by remember { mutableIntStateOf(0) }
    val (badgeSubtitle, badgeTitle) = program.getBadgeInfo(now())

    // Populate the content feed from the backend if we don't have it yet, then
    // arm local notifications for every future message that carries a
    // notificationTitle/Body (iOS NotificationHandler.scheduleContent).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (program.contentInfo.isEmpty()) {
            org.clear30.data.ProgramMessageHandler.fetchAndApply(program, program.startDate)
            refresh++
        }
        val allMessages = program.contentInfo.values.flatMap { it.messages }
        org.clear30.data.NotificationHandler.scheduleContent(userInfo, allMessages)
        // Calendar visibility analytic — fires once on first entry per
        // composition. The MonthCalendar is rendered unconditionally below so
        // a session-level event is appropriate.
        org.clear30.data.Logger.logEvent(
            userInfo.loggingID,
            org.clear30.data.LogEventType.openedCalendar,
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        // Day badge header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                SmallText(badgeSubtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
                Heading1(badgeTitle)
            }
            Spacer(Modifier.weight(1f))
        }

        // Program summary card
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallText(program.coreProgramName, color = Color.White)
                program.programDescription?.let { TinyText(it, color = Color.White) }
                ClearTimer(program, Modifier.fillMaxWidth())
            }
        }

        // Daily check-in (writes ProgramDayInfo via CheckInLogger)
        @Suppress("UNUSED_EXPRESSION") refresh // read so logging recomposes the card
        val todaySober = program.dayInfo[PlainDate.from(now())]?.sober
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                SmallText(
                    when (todaySober) {
                        true -> "✅ Logged: didn't smoke today"
                        false -> "💨 Logged: smoked today"
                        null -> "How did today go?"
                    },
                )
                if (todaySober == null) {
                    // The "check-in opened" event fires once per session when
                    // the user is presented with the choice — the actual
                    // outcome (logged/smoked) fires from CheckInLogger.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        org.clear30.data.Logger.logEvent(
                            userInfo.loggingID,
                            org.clear30.data.LogEventType.openedCheckInSheet,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                        DefaultButton("Didn't smoke", gradient = Clear30Gradients.clear30) {
                            logger.logWeedCheckIn(true); refresh++
                        }
                        DefaultButton("Smoked", gradient = Clear30Gradients.red) {
                            logger.logWeedCheckIn(false); refresh++
                        }
                    }
                }
            }
        }

        // Month calendar (days colored by logged check-ins)
        @Suppress("UNUSED_EXPRESSION") refresh
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            MonthCalendar(program)
        }

        // Content feed (unlocked program messages — taps open MessageDetail).
        TodayFeed(program, userInfo)
    }
}
