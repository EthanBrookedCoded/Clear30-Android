package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.Clear30Application
import org.clear30.util.isSameDay
import org.clear30.util.now
import java.io.File

/**
 * JournalEntry — ported from JournalEntry.swift (`@Model class JournalEntry`).
 *
 * Video journals store their media in the app files dir under `videos/`
 * (iOS used the documents dir). Thumbnail/video lookups return [File]s; loading
 * the bitmap is a view concern (Coil), unlike iOS's UIImage helpers.
 */
@Serializable
data class JournalEntry(
    var title: String,
    var content: String,
    var isVideo: Boolean? = false,
    var date: Instant = now(),
    var serverID: Int? = null,
    var communityPostId: String? = null,
) {
    val isPlaceholder: Boolean get() = title.isEmpty() && content.isEmpty()

    private fun videosDir(): File =
        File(Clear30Application.instance.filesDir, "videos")

    fun thumbnailFile(): File? =
        File(videosDir(), "$content.png").takeIf { it.exists() }

    fun videoFile(): File? =
        File(videosDir(), "$content.mov").takeIf { it.exists() }

    companion object {
        val placeholder = JournalEntry(title = "", content = "")
    }
}

/**
 * JournalEntries — ported from JournalEntries.swift (`@Model class`). The single
 * persisted container of journal entries, loaded/saved via [org.clear30.data.LocalStore].
 */
@Serializable
class JournalEntries(
    var entries: MutableList<JournalEntry> = mutableListOf(),
) {
    /** Entries on [date], ascending by time. */
    fun entries(forDate: Instant): List<JournalEntry> =
        entries.filter { it.date.isSameDay(forDate) }.sortedBy { it.date }

    companion object {
        const val STORE_KEY = "journal_entries"

        /** Flattened journal prompts across the given messages. */
        fun getPrompts(messages: List<ProgramMessage>): List<String> =
            messages.mapNotNull { it.journalPrompts }.filter { it.isNotEmpty() }.flatten()

        /**
         * Whether an entry [title] ANSWERS [prompt] (iOS matches raw:
         * `journalEntries.contains { $0.title == prompt }`, TodayFeedViews.swift:1131).
         *
         * We compare on trimmed/case-insensitive text because a raw `==` never
         * matched in practice: ~40% of the live `journal_prompts` values carry
         * trailing whitespace while `TextEntryEditor` trims the title it saves,
         * so an answered prompt kept its "New Journal" card AND its entry fell
         * into the free-form top-of-feed card.
         */
        fun answersPrompt(title: String, prompt: String): Boolean =
            title.trim().equals(prompt.trim(), ignoreCase = true)

        /** Whether an entry [title] answers ANY of [prompts]. */
        fun answersAnyPrompt(title: String, prompts: List<String>): Boolean =
            prompts.any { answersPrompt(title, it) }
    }
}
