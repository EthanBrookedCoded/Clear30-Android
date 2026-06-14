package org.clear30.views.existinguser.today

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.clear30.data.CheckInLogger
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.unlocked
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * TodayTab — faithful port of Home.swift. Three-row layout:
 *
 *   1. Header — relative-day heading ("Today" / "Wednesday") + Share button
 *   2. Weekday row + Calendar (Month ↔ Week toggle, taps open day detail)
 *   3. Feed — scrolling list whose first card is always [CheckInDayCard],
 *      followed by today's content messages (taps open MessageDetail)
 *
 * Everything I had layered between the calendar and the feed earlier
 * (program-timer card, this-week strip, end-of-week card, custom-check-in row,
 * month-stats triple) has been removed — the iOS Home shows none of these as
 * top-level sections. The clock and the streak surface IN the CheckInDayCard;
 * the custom-check-in entry point moves to Profile settings; the per-week
 * stats live behind the calendar grid where you'd expect them.
 */
@Composable
fun TodayTab(program: Program, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    val logger = remember { CheckInLogger(program, userInfo, scope) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCheckInSheet by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<PlainDate?>(null) }
    var selectedDay by remember { mutableStateOf(PlainDate.from(now())) }

    LaunchedEffect(Unit) {
        if (program.contentInfo.isEmpty() || org.clear30.data.ProgramMessageHandler.isStale(program)) {
            val added = org.clear30.data.ProgramMessageHandler.fetchAndApply(program, program.startDate)
            if (added > 0) refresh++
        }
        val allMessages = program.contentInfo.values.flatMap { it.messages }
        org.clear30.data.NotificationHandler.scheduleContent(userInfo, allMessages)
        org.clear30.data.Logger.logEvent(
            userInfo.loggingID,
            org.clear30.data.LogEventType.openedCalendar,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.horizontalPadding),
    ) {
        @Suppress("UNUSED_EXPRESSION") refresh

        // 1. Header — relative day + Share button.
        TodayHeader(selectedDay = selectedDay, program = program)

        // 2. Calendar with Month↔Week toggle. Day taps update `selectedDay`
        //    so the feed below re-renders against that date.
        CalendarCard(
            program = program,
            onDayTap = { date ->
                selectedDay = date
                if (date != PlainDate.from(now())) {
                    // Past day → also show the bottom-sheet quick view.
                    dayDetail = date
                }
            },
            modifier = Modifier.padding(vertical = Dimens.cardSpacing / 2),
        )

        FeedDivider()

        // 3. Feed.
        TodayFeed(
            program = program,
            userInfo = userInfo,
            selectedDay = selectedDay,
            onCheckIn = { showCheckInSheet = true },
        )
    }

    if (showCheckInSheet) {
        CheckInSheet(
            logger = logger,
            userInfo = userInfo,
            onDismiss = {
                showCheckInSheet = false
                refresh++
            },
        )
    }

    dayDetail?.let { d ->
        DayDetailSheet(
            date = d,
            program = program,
            userInfo = userInfo,
            logger = logger,
            onDismiss = { dayDetail = null; refresh++ },
        )
    }
}

/**
 * TodayHeader — port of the iOS Home header row. Heading1 with the relative
 * day description on the left, Share pill on the right. Padding matches
 * `GlobalData.shared.headingTopPadding`.
 */
@Composable
private fun TodayHeader(selectedDay: PlainDate, program: Program) {
    val context = LocalContext.current
    val today = PlainDate.from(now())
    val title = remember(selectedDay, today) { relativeDayDescription(selectedDay, today) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.headingTopPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Heading1(title)
        Spacer(Modifier.weight(1f))
        SharePill {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildShareSummary(program))
            }
            context.startActivity(Intent.createChooser(intent, "Share progress").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

/**
 * SharePill — iOS-style TinyTextButton with the share icon. Rounded pill
 * background (10% opacity) so it sits quietly next to the headline.
 */
@Composable
private fun SharePill(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Clear30Colors.opacityGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            sfSymbol("square.and.arrow.up"),
            contentDescription = "Share",
            tint = Clear30Colors.text,
            modifier = Modifier.size(14.dp),
        )
        TinyText("Share", color = Clear30Colors.text)
    }
}

/**
 * FeedDivider — slim separator between the calendar block and the scrolling
 * feed. iOS uses an interactive divider you can drag to toggle week/month;
 * we ship a simple visual line for now and revisit the gesture as a follow-up.
 */
@Composable
private fun FeedDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Clear30Colors.opacityGrayFlattened.copy(alpha = 0.5f)),
    )
}

/**
 * TodayFeed — scrolling feed of cards for [selectedDay]. First card is always
 * the day's CheckInDayCard; subsequent cards are the unlocked program message
 * cards (taps open MessageDetail).
 */
@Composable
private fun TodayFeed(
    program: Program,
    userInfo: UserInfo,
    selectedDay: PlainDate,
    onCheckIn: () -> Unit,
) {
    var openMessage by remember { mutableStateOf<ProgramMessage?>(null) }

    openMessage?.let { msg ->
        androidx.activity.compose.BackHandler { openMessage = null }
        MessageDetail(program, msg, userInfo, onBack = { openMessage = null })
        return
    }

    val messages = program.contentInfo.values
        .flatMap { it.messages }
        .unlocked
        .reversed()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        item {
            CheckInDayCard(
                selectedDay = selectedDay,
                program = program,
                showStreak = selectedDay == PlainDate.from(now()),
                onCheckIn = onCheckIn,
            )
        }
        if (messages.isEmpty()) {
            item {
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    SmallText(
                        "Your daily content will appear here.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            items(messages, key = { it.unlockOn.toEpochMilliseconds() }) { msg ->
                MessageFeedCard(msg) { openMessage = msg }
            }
        }
    }
}

@Composable
private fun MessageFeedCard(msg: ProgramMessage, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                msg.topicEmoji?.let { SmallText(it) }
                Heading3(msg.topicTitle)
            }
            if (msg.subtitle.isNotBlank()) {
                MiniText(msg.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Relative day description — port of `Date.relativeDayDescription()` (used by
 * the iOS Home header). Today renders as "Today", yesterday as "Yesterday",
 * within ±7 days as the weekday name, and beyond that as "M/D".
 */
private fun relativeDayDescription(date: PlainDate, today: PlainDate): String {
    val diff = date.toEpochDays() - today.toEpochDays()
    return when (diff) {
        0 -> "Today"
        -1 -> "Yesterday"
        1 -> "Tomorrow"
        in -6..6 -> {
            val ld = kotlinx.datetime.LocalDate(date.year, date.month, date.day)
            ld.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        else -> "${date.month}/${date.day}"
    }
}

private fun PlainDate.toEpochDays(): Int =
    kotlinx.datetime.LocalDate(year, month, day).toEpochDays()

private fun buildShareSummary(program: Program): String {
    val sober = program.dayInfo.values.count { it.sober == true }
    return "Clear30 progress: $sober days clear\n\nhttps://clear30.org"
}
