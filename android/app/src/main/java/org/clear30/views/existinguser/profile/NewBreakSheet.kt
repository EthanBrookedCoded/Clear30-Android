package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.clear30.data.ProgramTimelineHandler
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.UserInfo
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * NewBreakSheet — "Start a new break" entry from the Profile.
 *
 * iOS opens a full Break Assessment flow (`viewModel.activeSheet = .newBreak()`)
 * that personalizes the 30 days. Until that assessment is ported, this starts a
 * named 30-day Clear30 break beginning today — but now routes through the real
 * [ProgramTimelineHandler.newClear30] mechanic (iOS `Program.newClear30`) instead
 * of clobbering the whole timeline: it removes content from today forward, pulls
 * the Clear30 topic set from the backend, merges it in, resets health, and pushes
 * `program_breaks` + `content_info` to Supabase so the break survives reinstall
 * and syncs across devices. The break-assessment personalization is the follow-up.
 */
@Composable
fun NewBreakSheet(
    program: Program,
    userInfo: UserInfo,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Clear30") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Start a new break") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                SmallText(
                    "A 30-day Clear30 break starting today — daily content and check-ins for the next 30 days.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !busy,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        // Day 0 = today, no start-soon bridge (live onboarding never
                        // asks for a start date). handleBreaks builds the break;
                        // newClear30 fetches + schedules + saves + pushes.
                        val (startSoon, main) = ProgramTimelineHandler.handleBreaks(
                            ProgramBreakType.CLEAR30, emptyList(), nameOverride = name.trim(),
                        )
                        ProgramTimelineHandler.newClear30(program, main, startSoon, clientName = userInfo.name)
                        busy = false
                        onCreated()
                    }
                },
            ) { Text(if (busy) "Starting…" else "Start break") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
