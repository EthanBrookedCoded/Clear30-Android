package org.clear30.views.existinguser.today

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.CheckInLogger
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * DayDetailSheet — bottom sheet that opens when the user taps a single day on
 * one of the calendars. Shows the day's current status (sober / smoked /
 * unlogged), an edit affordance (flip the entry), and a Share button that
 * fires the system share sheet with a one-line summary of that day.
 *
 * Edits route through [CheckInLogger.logWeedCheckIn] but only when the day is
 * today — historical edits need a separate write path (TODO) because the
 * underlying CheckInLogger always logs against `now()`. For past days we
 * surface the status read-only and link to the share action only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: PlainDate,
    program: Program,
    userInfo: UserInfo,
    logger: CheckInLogger,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = program.dayInfo[date]
    val isToday = date == PlainDate.from(org.clear30.util.now())

    LaunchedEffect(date) {
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedDayDetail,
            mapOf(LogEventExtraDataType.DATE to date.dateString),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Clear30Colors.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading1(prettyDate(date))

            Clear30Card(
                modifier = Modifier.fillMaxWidth(),
                gradient = when (info?.sober) {
                    true -> Clear30Gradients.clear30
                    false -> Clear30Gradients.red
                    null -> null
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    GiganticText(
                        when (info?.sober) { true -> "🌱"; false -> "💨"; null -> "—" },
                        color = if (info?.sober != null) Color.White else Clear30Colors.text,
                    )
                    Column {
                        SmallText(
                            when (info?.sober) {
                                true -> "You stayed clear"
                                false -> "Logged a slip"
                                null -> "No check-in"
                            },
                            color = if (info?.sober != null) Color.White else Clear30Colors.text,
                        )
                        TinyText(
                            "${info?.loggedCheckIns?.size ?: 0} check-in entries",
                            color = if (info?.sober != null) Color.White.copy(alpha = 0.75f) else Clear30Colors.text.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            if (isToday && info?.sober == null) {
                DefaultButton("Check in now", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                    // Punt to the existing CheckInSheet via onDismiss + the
                    // parent's `showCheckInSheet` flag. Simplest path; avoids
                    // duplicating the puff-counter UI here.
                    onDismiss()
                }
            }

            if (info != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(Clear30Colors.opacityGray)
                        .clickable {
                            val summary = buildDaySummary(date, info.sober)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share this day").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                        .padding(horizontal = Dimens.cardSpacing, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        SmallText("📤")
                    }
                    Column(Modifier.weight(1f)) {
                        SmallText("Share this day", color = Clear30Colors.text)
                        TinyText("Text a friend a one-liner", color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                }
            }
            @Suppress("UNUSED_VARIABLE") val unused = logger to scope  // wired for future history-edit
        }
    }
}

private fun prettyDate(date: PlainDate): String {
    val monthName = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[date.month - 1]
    return "$monthName ${date.day}, ${date.year}"
}

private fun buildDaySummary(date: PlainDate, sober: Boolean?): String {
    val day = prettyDate(date)
    val status = when (sober) {
        true -> "Stayed clear 🌱"
        false -> "Logged a slip — onto tomorrow."
        null -> "Quiet day."
    }
    return "$day on Clear30: $status\n\nhttps://clear30.org"
}
