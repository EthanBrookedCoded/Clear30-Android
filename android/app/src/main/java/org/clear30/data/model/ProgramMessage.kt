package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.emojis
import org.clear30.util.firstEmoji
import org.clear30.util.justDay
import org.clear30.util.now

/**
 * ProgramMessage — ported from ProgramMessage.swift (`@Model final class`).
 *
 * The iOS model used a custom Codable that flattened `pageInfo` to `[[String]]`;
 * we store the same flattened shape and expose [programPageInfo] as the typed
 * view, so persisted JSON stays compatible. Mutable (`visited`, `favorited`,
 * `meditation.visited`) like the SwiftData original.
 *
 * Note: the `extension Program { getBadgeInfo }` defined alongside this class in
 * Swift is ported with Program (it depends on Program.getBreak).
 */
@Serializable
class ProgramMessage(
    var unlockOn: Instant,
    var messageID: Int? = null,
    var questionID: String? = null,
    var questionResponse: String? = null,
    var title: String,
    var subtitle: String,
    var message: String,
    var pageLink: String = "",
    /** Flattened [title, body] pairs — see [programPageInfo]. */
    var pageInfo: List<List<String>> = emptyList(),
    var videoURL: String? = null,
    var thumbnailURL: String? = null,
    var instagramVideos: List<ProgramVideo>? = null,
    var carouselImages: List<String>? = null,
    var meditation: ProgramMeditation? = null,
    var allResources: List<ProgramResource> = emptyList(),
    var clairePrompts: List<ProgramClairePrompt> = emptyList(),
    var journalPrompts: List<String>? = null,
    var memberPerks: List<ProgramMemberPerk>? = null,
    var notificationTitle: String? = null,
    var notificationBody: String? = null,
    var longTermID: String? = null,
    var isSchoolMessage: Boolean = false,
    var locked: Boolean = false,
    var visited: Boolean = false,
    var favorited: Boolean = false,
) {
    // MARK: - Derived (ProgramMessage extension)
    val programPageInfo: List<ProgramPageInfo>
        get() = pageInfo.mapNotNull { if (it.size >= 2) ProgramPageInfo(it[0], it[1]) else null }

    val reddits: List<ProgramResource> get() = allResources.filter { it.isReddit }
    val hasReddits: Boolean get() = reddits.isNotEmpty()
    val youTubes: List<ProgramResource> get() = allResources.filter { it.isYouTube }
    val hasYouTubes: Boolean get() = youTubes.isNotEmpty()
    val hasClairePrompts: Boolean get() = clairePrompts.isNotEmpty()
    val onlyResources: List<ProgramResource> get() = allResources.filter { !it.isReddit && !it.isYouTube }
    val hasOnlyResources: Boolean get() = onlyResources.isNotEmpty()

    val topicEmoji: String? get() = title.firstEmoji

    val topicTitle: String
        get() {
            val t = title.trim()
            val emoji = t.emojis.firstOrNull() ?: return t
            return t.replace(emoji, "").trim()
        }

    /** Unlocked once unlockOn is before tomorrow's start of day. */
    val unlocked: Boolean
        get() = unlockOn < now().justDay.adding(days = 1)

    companion object {
        /** Mirrors the Swift init that accepted typed [ProgramPageInfo]. */
        fun create(
            unlockOn: Instant,
            title: String,
            subtitle: String,
            message: String,
            pageInfo: List<ProgramPageInfo> = emptyList(),
            pageLink: String = "",
        ): ProgramMessage = ProgramMessage(
            unlockOn = unlockOn,
            title = title,
            subtitle = subtitle,
            message = message,
            pageLink = pageLink,
            pageInfo = pageInfo.map { listOf(it.title, it.body) },
        )
    }
}

// MARK: - List<ProgramMessage> helpers (ported from `extension [ProgramMessage]`)

/** School messages first, then by unlockOn ascending. */
val List<ProgramMessage>.sorted: List<ProgramMessage>
    get() = sortedWith(compareBy({ it.isSchoolMessage }, { it.unlockOn }))

val List<ProgramMessage>.unlocked: List<ProgramMessage>
    get() = sorted.filter { it.unlocked }

// MARK: - Value types

@Serializable
data class ProgramMeditation(
    val name: String,
    val url: String,
    var visited: Boolean = false,
)

@Serializable
data class ProgramResource(
    val title: String,
    val url: String,
) {
    val isReddit: Boolean get() = url.contains("reddit.com") && url.contains("comments")
    val isYouTube: Boolean get() = url.contains("youtube.com/watch?v=") || url.contains("youtu.be/")
}

@Serializable
data class ProgramClairePrompt(
    val title: String,
    val prompt: String,
    val displayPrompt: String? = null,
    val emojis: List<String>? = null,
) {
    val id: String get() = title + prompt
    val effectiveDisplayPrompt: String get() = displayPrompt ?: prompt
    val effectiveEmojis: List<String> get() = emojis ?: emptyList()
}

@Serializable
data class ProgramPageInfo(
    val title: String,
    val body: String,
) {
    val id: String get() = title + body
}

@Serializable
data class ProgramVideo(
    @SerialName("video_url") val videoURL: String,
    @SerialName("thumbnail_url") val thumbnailURL: String? = null,
)

@Serializable
data class ProgramMemberPerk(
    val title: String? = null,
    val subtitle: String? = null,
    @SerialName("image_url") val imageURL: String? = null,
    @SerialName("button_text") val buttonText: String,
    @SerialName("button_symbol") val buttonSymbol: String? = null,
    @SerialName("button_link") val buttonLink: String? = null,
    @SerialName("sheet_type") val sheetType: String? = null,
)

/*
 * TODO(port, Program segment): `extension Program { getBadgeInfo(for:) }` from
 * ProgramMessage.swift — port once Program.getBreak exists:
 *
 *   fun Program.getBadgeInfo(date: Instant): Pair<String, String> {  // (subtitle, title)
 *       val b = getBreak(date)
 *       if (b != null && b.isStartSoon) {
 *           val daysUntilStart = date.daysTo(b.endDate) + 1
 *           return "Break starts in" to "$daysUntilStart Day${if (daysUntilStart == 1) "" else "s"}"
 *       }
 *       return if (b != null) b.type.name to "Day ${b.getBreakDay(date)}"
 *              else (if (coreModeration) "Moderation" else "Weed free") to "Life"
 *   }
 */
