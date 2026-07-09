package org.clear30.views.existinguser.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.Clear30Application
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.util.now
import java.io.File

/**
 * JournalSection — ported from the Profile journal views. Lists journal entries
 * (newest first), supports text + video entries, and persists through
 * [Clear30Store]. Videos are captured via the system camera (intent), saved to
 * `filesDir/videos/<id>.mov` (the path `JournalEntry.videoFile()` looks at),
 * and play back through the same system intent on tap.
 */
@Composable
fun JournalSection(journalEntries: JournalEntries, userInfo: UserInfo, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION") version
    val entries = journalEntries.entries.sortedByDescending { it.date }

    // Video capture pipeline:
    //   1. We allocate a destination file id BEFORE launching the camera so we
    //      know where the bytes will land and we can build the matching journal
    //      entry on success.
    //   2. The camera writes into the FileProvider URI we pass it.
    //   3. On success (result == true) we materialise the JournalEntry.
    // Video journals play back in-app (ExoPlayer) rather than handing the file
    // to an external player via ACTION_VIEW.
    var playingVideo by remember { mutableStateOf<File?>(null) }
    var pendingVideoId by remember { mutableStateOf<String?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { saved ->
        val id = pendingVideoId
        pendingVideoId = null
        if (saved == true && id != null) {
            val file = videoFile(id)
            if (file.exists() && file.length() > 0) {
                journalEntries.entries.add(
                    JournalEntry(title = "Video journal", content = id, isVideo = true, date = now()),
                )
                scope.launch { Clear30Store.save(journalEntries) }
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.createdJournalEntry,
                    mapOf(LogEventExtraDataType.TYPE to "video"),
                )
                version++
            } else {
                file.delete()  // empty / cancelled write
            }
        } else if (id != null) {
            videoFile(id).delete()
        }
    }

    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton("chevron.backward") { onBack() }
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            Heading1("Journal")
            Spacer(Modifier.weight(1f))
            // Video first so the headline "+" is reserved for the most common
            // (text) action, matching iOS.
            IconButton("video.fill") {
                val id = "video-${System.currentTimeMillis()}"
                pendingVideoId = id
                val dest = videoFile(id)
                dest.parentFile?.mkdirs()
                dest.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dest,
                )
                videoLauncher.launch(uri)
            }
            IconButton("plus") { showAdd = true }
        }

        if (entries.isEmpty()) {
            SmallText("No entries yet. Tap + to write one or 🎥 to record one.", color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            entries.forEach { entry ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = entry.isVideo == true) {
                        playingVideo = entry.videoFile() ?: return@clickable
                    },
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (entry.isVideo == true) {
                                Icon(
                                    sfSymbol("video.fill"),
                                    contentDescription = null,
                                    tint = Clear30Colors.green,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            SmallText(entry.title.ifBlank { "Untitled" })
                            Spacer(Modifier.weight(1f))
                            TinyText(entry.date.dateLabel(), color = Clear30Colors.text.copy(alpha = 0.5f))
                        }
                        if (entry.content.isNotBlank() && entry.isVideo != true) TinyText(entry.content, maxLines = 3)
                    }
                }
            }
        }
    }
    }

    playingVideo?.let { f ->
        org.clear30.views.components.VideoPlayerDialog(
            uri = Uri.fromFile(f).toString(),
            onDismiss = { playingVideo = null },
        )
    }

    if (showAdd) {
        AddEntryDialog(
            onDismiss = { showAdd = false },
            onSave = { title, content ->
                journalEntries.entries.add(JournalEntry(title = title, content = content, date = now()))
                scope.launch { Clear30Store.save(journalEntries) }
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.createdJournalEntry,
                    mapOf(LogEventExtraDataType.TYPE to "text"),
                )
                version++
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New journal entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(content, { content = it }, label = { Text("What's on your mind?") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, content) }, enabled = title.isNotBlank() || content.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Resolve the on-disk file for a video journal id (matches `JournalEntry.videoFile()`). */
private fun videoFile(id: String): File =
    File(File(Clear30Application.instance.filesDir, "videos"), "$id.mov")

private fun kotlinx.datetime.Instant.dateLabel(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.monthNumber}/${d.dayOfMonth}/${d.year % 100}"
}
