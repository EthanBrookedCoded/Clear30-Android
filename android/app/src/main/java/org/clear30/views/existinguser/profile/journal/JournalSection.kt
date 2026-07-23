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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    @Suppress("UNUSED_EXPRESSION") version
    // Video journaling is removed on Android (J47) — text entries only. Any legacy
    // video entries are filtered out of the list.
    val entries = journalEntries.entries.filter { it.isVideo != true }.sortedByDescending { it.date }

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

    // No statusBarsPadding: this page renders inside AllTabs' Scaffold content,
    // which is already inset below the status bar.
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton("chevron.backward") { onBack() }
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            Heading1("Journal")
            Spacer(Modifier.weight(1f))
            IconButton("plus") { creatingNew = true }
        }

        if (entries.isEmpty()) {
            SmallText("No entries yet. Tap + to write one.", color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            entries.forEach { entry ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        editing = entry
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.viewedJournalEntry,
                            mapOf(LogEventExtraDataType.TYPE to "text"),
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 3)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                        ) {
                            // Measure the date first and give the remaining width
                            // to the title, so long titles wrap/ellipsize instead
                            // of squeezing the date into a narrow column.
                            SmallText(
                                entry.title.ifBlank { "Untitled" },
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                            )
                            TinyText(
                                entry.date.dateLabel(),
                                color = Clear30Colors.text.copy(alpha = 0.5f),
                                maxLines = 1,
                            )
                        }
                        if (entry.content.isNotBlank()) {
                            TinyText(
                                entry.content,
                                modifier = Modifier.fillMaxWidth(),
                                color = Clear30Colors.text.copy(alpha = 0.65f),
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }
    }
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

}

private fun kotlinx.datetime.Instant.dateLabel(): String {
    val d = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.monthNumber}/${d.dayOfMonth}/${d.year % 100}"
}
