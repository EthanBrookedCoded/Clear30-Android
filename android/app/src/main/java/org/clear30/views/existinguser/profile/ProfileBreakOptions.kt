package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.ProgramTimelineHandler
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Alert
import org.clear30.views.components.Clear30CardDialog
import org.clear30.views.components.TextIconButton
import org.clear30.views.theme.Dimens
import org.clear30.util.adding
import org.clear30.util.justDay
import org.clear30.util.now

/**
 * ProfileBreakOptions — the in-break management actions (iOS `ProfileBreakOptions`
 * / the Day-0 countdown "Start Break Now"). Renders nothing in Life (no current
 * break); otherwise surfaces the [ProgramTimelineHandler] mutators so break day
 * logic is reachable end-to-end:
 *
 *  - **Preparation / Day 0** (break hasn't "started"): *Start break now*
 *    (`day0StartNow`) + *Change start date* (`adjustBreakTime`).
 *  - **Active break** (Day 1+): *Restart break* (`restartBreak`), *Change start
 *    date* (`adjustBreakTime`, or `restartBreak` when the new date is today), and
 *    *End break* (`endBreak` → drops into Life).
 *
 * Every action saves locally and pushes to Supabase inside the handler.
 */
@Composable
fun ProfileBreakOptions(program: Program, userInfo: UserInfo, revision: Int = 0, onChanged: () -> Unit) {
    val current = program.currentBreak ?: return
    // Deliberately NOT rememberCoroutineScope: endBreak nulls currentBreak
    // in place before its network work (life-short submit, content refetch)
    // finishes, so this composable leaves composition mid-flight on the next
    // recomposition — a composition-tied scope would cancel the mutation
    // halfway through. The app-lifetime scope lets it run to completion.
    val scope = org.clear30.Clear30Application.appScope
    var busy by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Keyed on `revision` so break mutations re-derive the Day-0 branch — and so
    // the param stays genuinely used (unused params are excluded from skipping).
    val today = remember(revision) { now().justDay }
    // Day 0 = the countdown day; start-soon = the preparation bridge before Day 0.
    val notStarted = current.isStartSoon || current.getBreakDay(today) <= 0

    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        // appScope has no CoroutineExceptionHandler — an uncaught throw would
        // crash the app AND leave `busy` stuck true.
        scope.launch {
            try {
                runCatching { block() }
                    .onFailure { android.util.Log.e("ProfileBreakOptions", "break action failed", it) }
            } finally {
                busy = false
                onChanged()
            }
        }
    }

    // iOS ProfileBreakOptions: the standard white CARD buttons (background +
    // soft shadow — TextIconButton with the default clear30Button fill).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        if (notStarted) {
            TextIconButton("Start Break Now", icon = "timer") {
                perform { ProgramTimelineHandler.day0StartNow(program) }
            }
            TextIconButton("Change Break Start Date", icon = "calendar") { showDatePicker = true }
        } else {
            TextIconButton("Restart Break", icon = "arrow.counterclockwise") { showRestart = true }
            TextIconButton("Change Break Start Date", icon = "calendar") { showDatePicker = true }
            TextIconButton("End Break", icon = "xmark") { showEnd = true }
        }
    }

    if (showRestart) {
        ConfirmDialog(
            title = "Restart break?",
            body = "This resets your break so today becomes Day 1. Your progress since the start date is cleared.",
            confirm = "Restart",
            onDismiss = { showRestart = false },
            onConfirm = { showRestart = false; perform { ProgramTimelineHandler.restartBreak(program) } },
        )
    }

    if (showEnd) {
        ConfirmDialog(
            title = "End break?",
            body = "Your break ends today and you'll move to the ongoing Life program. You can start a new Clear30 any time.",
            confirm = "End break",
            onDismiss = { showEnd = false },
            onConfirm = { showEnd = false; perform { ProgramTimelineHandler.endBreak(program) } },
        )
    }

    if (showDatePicker) {
        Clear30CardDialog(onDismiss = { showDatePicker = false }) {
            // The user picks their new Day 1 (the day the break "starts"); the
            // current Day 1 = break start (Day 0) + 1.
            StartDateCalendarPicker(currentStartDate = PlainDate.from(current.startDate.adding(days = 1))) { picked ->
                showDatePicker = false
                if (picked == null) return@StartDateCalendarPicker
                Logger.logEvent(userInfo.loggingID, LogEventType.changedProgramStartDate)
                val todayPlain = PlainDate.from(now())
                perform {
                    // New Day 1 = today → restart (Day 1 = today); else shift the
                    // whole break by (new Day 0 − old Day 0).
                    if (picked == todayPlain) {
                        ProgramTimelineHandler.restartBreak(program)
                    } else {
                        val newDay0 = picked.adding(days = -1)
                        val delta = PlainDate.from(current.startDate).daysTo(newDay0)
                        if (delta != 0) ProgramTimelineHandler.adjustBreakTime(program, delta)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Clear30Alert(
        onDismissRequest = onDismiss,
        title = title,
        message = body,
        confirmLabel = confirm,
        onConfirm = onConfirm,
        dismissLabel = "Cancel",
    )
}
