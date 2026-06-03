package org.clear30.util

/**
 * String helpers ported from the Swift `String` extensions used by the program
 * models (e.g. `title.emojis.first`, emoji stripping for topic titles).
 */
private val emojiRegex = Regex(
    "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F000}-\\x{1F0FF}\\x{1F1E6}-\\x{1F1FF}\\x{2190}-\\x{21FF}\\x{2B00}-\\x{2BFF}\\x{FE00}-\\x{FE0F}\\x{200D}]"
)

/** Equivalent of Swift `String.emojis` — emoji clusters found in the string. */
val String.emojis: List<String>
    get() = emojiRegex.findAll(this).map { it.value }.toList()

/** First emoji in the string, or null (Swift `title.emojis.first`). */
val String.firstEmoji: String? get() = emojis.firstOrNull()
