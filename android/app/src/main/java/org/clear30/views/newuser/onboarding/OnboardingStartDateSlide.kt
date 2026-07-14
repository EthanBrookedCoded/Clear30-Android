package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.clear30.data.CheckInLogger
import org.clear30.data.Clear30Store
import org.clear30.data.ProgramTimelineHandler
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.util.adding
import org.clear30.util.now
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.existinguser.profile.StartDateCalendarPicker
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

private val SHORT_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * OnboardingStartDateSlide — the O11 start-date step, a port of iOS
 * `Tutorial2DatePicker` placed right after the notification-permission popup
 * (Thatcher, PARITY §17-Q9; Clear30 users only). Calendar range today−29 …
 * today+14, defaulting to tomorrow (Day 1). On confirm the selected date is
 * treated as Day 1: Day 0 = selected − 1, the break/timeline shifts by the
 * delta via `adjustBreakTime`, a past/today selection back-fills sober
 * check-ins and backdates `lastSmoked`, a future selection resets `lastSmoked`
 * to now (Tutorial2DatePicker.swift:192-258).
 */
@Composable
fun OnboardingStartDateSlide(
    userInfo: UserInfo,
    program: Program,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val today = remember { PlainDate.from(now()) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        Heading3("When is your quit date?")
        SmallText(
            "Pick the first full day of your break. Already started? Choose a past date and we'll count those days.",
            modifier = Modifier.padding(top = Dimens.cardSpacing / 2),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth().alpha(if (busy) 0.5f else 1f)) {
            StartDateCalendarPicker(
                currentStartDate = today,
                minOffsetDays = -29,
                maxOffsetDays = 14,
                initialSelection = today.adding(days = 1),
                requireChange = false,
                showClose = false,
                confirmLabel = { sel -> sel?.let { confirmText(it, today) } ?: "" },
            ) { selected ->
                if (selected != null && !busy) {
                    busy = true
                    scope.launch {
                        handleSelection(selected, today, userInfo, program, scope)
                        onNext()
                    }
                }
            }
        }

        if (busy) {
            Column(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

/** iOS `buttonText` — Today / Yesterday / Tomorrow / "Jul 18". */
private fun confirmText(selected: PlainDate, today: PlainDate): String = when (today.daysTo(selected)) {
    0 -> "Today"
    -1 -> "Yesterday"
    1 -> "Tomorrow"
    else -> "${SHORT_MONTHS[selected.month - 1]} ${selected.day}"
}

/** Port of iOS `Tutorial2DatePicker.handleSelection` + `logPastDaysSober`. */
private suspend fun handleSelection(
    selected: PlainDate,
    today: PlainDate,
    userInfo: UserInfo,
    program: Program,
    scope: CoroutineScope,
) {
    val currentBreak = program.currentBreak ?: return
    val currentStart = PlainDate.from(currentBreak.startDate)

    // The selected date is Day 1, so Day 0 (the break's start) is the day before.
    val newDay0 = selected.adding(days = -1)
    val daysToShift = currentStart.daysTo(newDay0)
    if (daysToShift == 0) return

    ProgramTimelineHandler.adjustBreakTime(program, daysToShift)

    if (selected <= today) {
        // Back-fill: Day 0 smoked, then every day from the start through today sober.
        val pairs = buildList {
            add(newDay0 to false)
            var d = selected
            while (d <= today) {
                add(d to true)
                d = d.adding(days = 1)
            }
        }
        CheckInLogger(program, userInfo, scope).handleMultiCheckIn(pairs)

        // Backdate lastSmoked so the clear-time timer reads N full days
        // (select today → lastSmoked = now; yesterday → now − 1d).
        val daysSinceStart = selected.daysTo(today)
        CheckInLogger.resetLastSmoked(setTo = now().adding(days = -daysSinceStart), program = program)
    } else {
        CheckInLogger.resetLastSmoked(setTo = now(), program = program)
    }

    try {
        Clear30Store.save(program)
    } catch (t: Throwable) {
        android.util.Log.e("OnboardingStartDate", "Failed to persist program after start-date pick", t)
    }
}
