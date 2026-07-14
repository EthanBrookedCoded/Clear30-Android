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
import org.clear30.data.AlertHandler
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.CreatePost
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.createCommunityPost
import org.clear30.data.supabase.getUserID
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.util.now
import java.io.File
import java.util.UUID

/**
 * JournalSection — ported from the Profile journal views. Lists journal entries
 * (newest first), supports text + video entries, and persists through
 * [Clear30Store]. Videos are captured via the system camera (intent), saved to
 * `filesDir/videos/<id>.mov` (the path `JournalEntry.videoFile()` looks at),
 * and play back through the same system intent on tap.
 *
 * Text entries open the full-screen [TextEntryEditor] (iOS `TextEntry`), which
 * supports editing, deleting, and sharing the entry to the community feed.
 * Video entries are named on save via a prompt picker (iOS names them by the
 * selected prompt, NewVideoEntryViewModel.swift).
 */
@Composable
fun JournalSection(
    journalEntries: JournalEntries,
    userInfo: UserInfo,
    program: Program,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }

    // Full-screen text editor state — `creatingNew` hosts a fresh entry,
    // `editing` an existing one (iOS pushes TextEntry onto the nav stack).
    var creatingNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JournalEntry?>(null) }
    // A freshly recorded video waiting to be named (iOS: title = selected prompt).
    var namingVideoId by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_EXPRESSION") version
    val entries = journalEntries.entries.sortedByDescending { it.date }

    // Journal prompts from the unlocked program content — offered as video
    // titles (iOS selects the prompt before recording).
    val prompts = remember(program.contentInfo, version) {
        JournalEntries.getPrompts(program.contentInfo.values.flatMap { it.messages }).distinct()
    }

    fun saveEntries() = scope.launch { Clear30Store.save(journalEntries) }

    /** Post a text entry to the community feed (iOS `communityAlert()` flow). */
    fun shareToCommunity(entry: JournalEntry?, title: String, body: String) {
        scope.launch {
            val uid = SupabaseController.getUserID() ?: userInfo.userID
            Logger.logEvent(userInfo.loggingID, LogEventType.createdCommunityPost)
            val err = SupabaseController.createCommunityPost(
                CreatePost(
                    p_title = title,
                    p_content_type = "text",
                    p_body = body,
                    p_user_id = uid,
                    p_tags = listOf(program.coreProgramName),
                    p_journal_entry_id = entry?.serverID,
                ),
            )
            if (err != null) {
                AlertHandler.info("Couldn't share", err.message)
            } else if (entry != null) {
                // The create RPC doesn't return the post id, so a local marker
                // stands in for iOS's `communityPostId` (it only gates the
                // share button from showing again).
                entry.communityPostId = "local-${UUID.randomUUID()}"
                Clear30Store.save(journalEntries)
                version++
            }
        }
    }

    // Video capture pipeline:
    //   1. We allocate a destination file id BEFORE launching the camera so we
    //      know where the bytes will land and we can build the matching journal
    //      entry on success.
    //   2. The camera writes into the FileProvider URI we pass it.
    //   3. On success (result == true) we ask for a title (prompt picker) and
    //      materialise the JournalEntry.
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
                namingVideoId = id
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
            IconButton("plus") { creatingNew = true }
        }

        if (entries.isEmpty()) {
            SmallText("No entries yet. Tap + to write one or 🎥 to record one.", color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            entries.forEach { entry ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (entry.isVideo == true) {
                            playingVideo = entry.videoFile() ?: return@clickable
                        } else {
                            editing = entry
                            Logger.logEvent(
                                userInfo.loggingID,
                                LogEventType.viewedJournalEntry,
                                mapOf(LogEventExtraDataType.TYPE to "text"),
                            )
                        }
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

    // New text entry — full-screen editor (iOS NewTextEntry → TextEntry). The
    // entry is committed on close, and only when both fields are filled.
    if (creatingNew) {
        var sharedDuringCreation by remember { mutableStateOf(false) }
        TextEntryEditor(
            initialTitle = "",
            initialContent = "",
            alreadyShared = false,
            onShare = { title, body ->
                sharedDuringCreation = true
                shareToCommunity(null, title, body)
            },
            onClose = { title, body ->
                creatingNew = false
                if (title.isNotBlank() && body.isNotBlank()) {
                    journalEntries.entries.add(
                        JournalEntry(
                            title = title,
                            content = body,
                            date = now(),
                            communityPostId = if (sharedDuringCreation) "local-${UUID.randomUUID()}" else null,
                        ),
                    )
                    saveEntries()
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.createdJournalEntry,
                        mapOf(LogEventExtraDataType.TYPE to "text"),
                    )
                    version++
                }
            },
        )
    }

    // Existing text entry — edit / delete / share (iOS TextEntry with the
    // delete + update closures).
    editing?.let { entry ->
        TextEntryEditor(
            initialTitle = entry.title,
            initialContent = entry.content,
            alreadyShared = entry.communityPostId != null,
            onDelete = {
                editing = null
                journalEntries.entries.remove(entry)
                saveEntries()
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.deletedJournalEntry,
                    mapOf(LogEventExtraDataType.TYPE to "text"),
                )
                version++
            },
            onShare = { title, body -> shareToCommunity(entry, title, body) },
            onClose = { title, body ->
                editing = null
                if (title.isNotBlank() && body.isNotBlank() && (title != entry.title || body != entry.content)) {
                    entry.title = title
                    entry.content = body
                    saveEntries()
                    version++
                }
            },
        )
    }

    // Name a freshly recorded video (iOS: `title = selectedPrompt`).
    namingVideoId?.let { id ->
        VideoNameDialog(
            prompts = prompts,
            onSave = { title ->
                namingVideoId = null
                journalEntries.entries.add(
                    JournalEntry(title = title, content = id, isVideo = true, date = now()),
                )
                saveEntries()
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.createdJournalEntry,
                    mapOf(LogEventExtraDataType.TYPE to "video"),
                )
                version++
            },
        )
    }
}

/**
 * Title picker for a new video journal — free-text title plus the unlocked
 * journal prompts as one-tap suggestions (iOS records against a selected
 * prompt and names the entry with it).
 */
@Composable
private fun VideoNameDialog(prompts: List<String>, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        // The clip is already recorded — dismissing still keeps it, just unnamed.
        onDismissRequest = { onSave(title.trim()) },
        title = { Text("Name your video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                OutlinedTextField(
                    title, { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                )
                if (prompts.isNotEmpty()) {
                    TinyText("Or pick a prompt:", color = Clear30Colors.text.copy(alpha = 0.5f))
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        prompts.take(5).forEach { prompt ->
                            TinyText(
                                prompt,
                                modifier = Modifier.fillMaxWidth().clickable { title = prompt },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim()) }) { Text("Save") } },
    )
}

/** Resolve the on-disk file for a video journal id (matches `JournalEntry.videoFile()`). */
private fun videoFile(id: String): File =
    File(File(Clear30Application.instance.filesDir, "videos"), "$id.mov")

private fun kotlinx.datetime.Instant.dateLabel(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.monthNumber}/${d.dayOfMonth}/${d.year % 100}"
}
