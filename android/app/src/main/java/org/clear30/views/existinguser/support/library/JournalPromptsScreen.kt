package org.clear30.views.existinguser.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.util.now
import org.clear30.views.components.Clear30Alert
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * JournalPromptsScreen — ported from the iOS Journal Prompts library. Flattens
 * every `journalPrompts` list off the unlocked program messages into a single
 * scrollable list. Tap a prompt → opens a "write a response" sheet that saves
 * the result as a [JournalEntry] (title = prompt, body = user response).
 *
 * Fires `openedJournalPrompts` on entry and `createdJournalEntry` (type=text)
 * when a response is saved.
 */
@Composable
fun JournalPromptsScreen(
    program: Program,
    journalEntries: JournalEntries,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var responding by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedJournalPrompts)
    }

    val prompts = remember(program.contentInfo) {
        val messages = program.contentInfo.values.flatMap { it.messages }
        JournalEntries.getPrompts(messages).distinct()
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Journal prompts")
        }
        if (prompts.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText(
                    "Prompts unlock with your daily content. Come back after you've checked in.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            items(prompts, key = { it }) { prompt ->
                GradientActionButton(
                    iconName = "pencil.and.outline",
                    gradient = Clear30Gradients.journals,
                    title = prompt,
                ) { responding = prompt }
            }
        }
    }

    responding?.let { prompt ->
        WritePromptResponseDialog(
            prompt = prompt,
            onDismiss = { responding = null },
            onSave = { body ->
                if (body.isNotBlank()) {
                    val entry = JournalEntry(
                        title = prompt,
                        content = body.trim(),
                        isVideo = false,
                        date = now(),
                    )
                    journalEntries.entries.add(entry)
                    scope.launch { Clear30Store.save(journalEntries) }
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.createdJournalEntry,
                        mapOf(LogEventExtraDataType.TYPE to "text"),
                    )
                }
                responding = null
            },
        )
    }
}

@Composable
private fun WritePromptResponseDialog(prompt: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var body by remember { mutableStateOf("") }
    Clear30Alert(
        onDismissRequest = onDismiss,
        title = "Journal",
        confirmLabel = "Save",
        onConfirm = { onSave(body) },
        confirmEnabled = body.isNotBlank(),
        dismissLabel = "Cancel",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            TinyText(prompt, color = Clear30Colors.text.copy(alpha = 0.5f))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Take your time…") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

