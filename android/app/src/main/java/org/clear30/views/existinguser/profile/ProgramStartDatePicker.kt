package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * ProgramStartDatePicker — ported from the iOS settings "change program start
 * date" affordance. Shows the current start date and opens a Material3
 * DatePicker dialog to pick a new one. Persists via [Clear30Store] and fires
 * the `changedProgramStartDate` analytic.
 *
 * Why we lift `startDate` into a separate composable: the picker mutates the
 * Program in place (matching SwiftData), but the parent needs to recompose to
 * reflect the new headline date — easier to manage that here than thread a
 * refresh counter through every parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        val state = rememberDatePickerState(initialSelectedDateMillis = program.startDate.toEpochMilliseconds())
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Program start date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    TinyText(
                        "Shifting your start date re-anchors the daily content and check-in cadence.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                    DatePicker(state = state, showModeToggle = false)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        val ms = state.selectedDateMillis ?: return@TextButton
                        val newStart = Instant.fromEpochMilliseconds(ms)
                        program.startDate = newStart
                        scope.launch { Clear30Store.save(program) }
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.changedProgramStartDate,
                        )
                        refresh++
                        showPicker = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
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
