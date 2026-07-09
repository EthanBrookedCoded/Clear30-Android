package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * ProgramStartDatePicker — ported from the iOS settings "change program start
 * date" affordance. Shows the current start date and opens the on-brand
 * [StartDateCalendarPicker] (iOS `ProgramStartDatePicker` calendar card) to pick a
 * new one. Persists via [Clear30Store] and fires the `changedProgramStartDate`
 * analytic.
 *
 * Why we lift `startDate` into a separate composable: the picker mutates the
 * Program in place (matching SwiftData), but the parent needs to recompose to
 * reflect the new headline date — easier to manage that here than thread a
 * refresh counter through every parent.
 */
@Composable
fun ProgramStartDatePicker(program: Program, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") refresh

    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.padding(end = Dimens.cardSpacing)) {
                Heading3("Program start date")
                SmallText(program.startDate.formatted(), color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            org.clear30.views.components.DefaultButton(
                "Change",
                gradient = org.clear30.views.theme.Clear30Gradients.clear30,
            ) { showPicker = true }
        }
    }

    if (showPicker) {
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding)) {
                StartDateCalendarPicker(currentStartDate = PlainDate.from(program.startDate)) { picked ->
                    showPicker = false
                    if (picked == null) return@StartDateCalendarPicker
                    program.startDate = picked.dateObject
                    scope.launch { Clear30Store.save(program) }
                    Logger.logEvent(userInfo.loggingID, LogEventType.changedProgramStartDate)
                    refresh++
                }
            }
        }
    }
}

private fun Instant.formatted(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    val monthName = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[d.monthNumber - 1]
    return "$monthName ${d.dayOfMonth}, ${d.year}"
}
