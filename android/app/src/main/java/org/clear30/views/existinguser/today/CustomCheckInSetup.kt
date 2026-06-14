package org.clear30.views.existinguser.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.CustomCheckInOption
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * CustomCheckInSetup — ported from the iOS "Add custom check-in" flow. Lists the
 * program's [Program.customCheckIns] and exposes Add / Delete operations.
 * Editing nests through the AddOrEditDialog, which captures a name + two
 * emoji+label options (e.g. "Skipped caffeine" 🍵 / "Drank coffee" ☕). New
 * entries get a fresh random hue so their card gradient is distinct.
 *
 * Why this lives under `today/`: the affordance is reached from the Today tab's
 * check-in card, and the resulting list flows back into TodayTab's check-in
 * sheet for the user to log against.
 */
@Composable
fun CustomCheckInSetup(
    program: Program,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CustomCheckIn?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var rev by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") rev

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Column {
                Heading1("Custom check-ins")
            }
        }

        SmallText(
            "Track anything daily — caffeine, alcohol, screen-free hours. Your custom check-ins appear next to the main one on Today.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        if (program.customCheckIns.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText(
                    "No custom check-ins yet. Tap below to add one.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                items(program.customCheckIns, key = { it.id }) { c ->
                    Clear30Card(
                        modifier = Modifier.fillMaxWidth().clickable { editing = c },
                        gradient = c.gradient,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallText(c.completeOption.emoji, color = Color.White)
                            Column(Modifier.padding(horizontal = Dimens.cardSpacing)) {
                                Heading3(c.name, color = Color.White)
                                TinyText(
                                    "${c.incompleteOption.name} · ${c.completeOption.name}",
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
            }
        }

        DefaultButton("Add a custom check-in", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
            showNew = true
        }
    }

    if (showNew) {
        AddOrEditDialog(
            existing = null,
            onDismiss = { showNew = false },
            onSave = { name, incomplete, complete ->
                val newOne = CustomCheckIn(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    incompleteOption = incomplete,
                    completeOption = complete,
                    hue = Math.random(),
                )
                program.customCheckIns.add(newOne)
                scope.launch { Clear30Store.save(program) }
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.openedCustomCheckIn,
                    mapOf(LogEventExtraDataType.CUSTOM_CHECK_IN_ID to newOne.id),
                )
                rev++
                showNew = false
            },
        )
    }

    editing?.let { current ->
        AddOrEditDialog(
            existing = current,
            onDismiss = { editing = null },
            onDelete = {
                program.customCheckIns.removeAll { it.id == current.id }
                scope.launch { Clear30Store.save(program) }
                rev++
                editing = null
            },
            onSave = { name, incomplete, complete ->
                val idx = program.customCheckIns.indexOfFirst { it.id == current.id }
                if (idx >= 0) {
                    program.customCheckIns[idx] = current.copy(
                        name = name,
                        incompleteOption = incomplete,
                        completeOption = complete,
                    )
                    scope.launch { Clear30Store.save(program) }
                    rev++
                }
                editing = null
            },
        )
    }
}

@Composable
private fun AddOrEditDialog(
    existing: CustomCheckIn?,
    onDismiss: () -> Unit,
    onSave: (name: String, incomplete: CustomCheckInOption, complete: CustomCheckInOption) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var incompleteEmoji by remember { mutableStateOf(existing?.incompleteOption?.emoji ?: "👎") }
    var incompleteName by remember { mutableStateOf(existing?.incompleteOption?.name ?: "Did it") }
    var completeEmoji by remember { mutableStateOf(existing?.completeOption?.emoji ?: "👍") }
    var completeName by remember { mutableStateOf(existing?.completeOption?.name ?: "Skipped") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New custom check-in" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                TinyText("\"NO\" option")
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    OutlinedTextField(incompleteEmoji, { incompleteEmoji = it }, modifier = Modifier.padding(end = 4.dp), singleLine = true)
                    OutlinedTextField(incompleteName, { incompleteName = it }, label = { Text("Label") }, singleLine = true)
                }
                TinyText("\"YES\" option")
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    OutlinedTextField(completeEmoji, { completeEmoji = it }, modifier = Modifier.padding(end = 4.dp), singleLine = true)
                    OutlinedTextField(completeName, { completeName = it }, label = { Text("Label") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && incompleteName.isNotBlank() && completeName.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        CustomCheckInOption(emoji = incompleteEmoji.trim().ifBlank { "👎" }, name = incompleteName.trim()),
                        CustomCheckInOption(emoji = completeEmoji.trim().ifBlank { "👍" }, name = completeName.trim()),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                onDelete?.let {
                    TextButton(onClick = it) { Text("Delete", color = Clear30Colors.red2) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
