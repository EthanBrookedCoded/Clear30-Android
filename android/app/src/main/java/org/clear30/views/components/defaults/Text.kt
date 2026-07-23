package org.clear30.views.components

import android.util.Patterns
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Lexend

/**
 * Text scale — ported 1:1 from TextSizes.swift. Sizes/weights match the iOS
 * Lexend styles exactly. `color = Color.Unspecified` lets text inherit
 * `LocalContentColor` (e.g. white inside a gradient [Clear30Card]).
 *
 * The SwiftUI `*WithLinks` / Markdown variants are represented by the `markdown`
 * flag (full markdown rendering is a TODO; plain text renders meanwhile).
 */
/**
 * Line-height applied to every Clear30 text style. Compose otherwise falls back
 * to Lexend's intrinsic metrics, which are too tight — multi-line text was
 * overlapping ("going into each other"). 1.3× line height with `Trim.Both` gives
 * clean inter-line spacing while leaving single-line text (and the deliberate
 * negative-spaced word stacks like "day / without / weed") visually unchanged.
 */
private const val LINE_HEIGHT_RATIO = 1.3f
private val ClearTextStyle = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun sized(
    text: String, size: TextUnit, weight: FontWeight,
    modifier: Modifier, color: Color, maxLines: Int,
    textAlign: TextAlign? = null,
) = Text(
    text = text,
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * LINE_HEIGHT_RATIO,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
    style = ClearTextStyle,
)

@Composable fun GiganticText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 50.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE)

@Composable fun HugeText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 40.sp, FontWeight.Bold, modifier, color, Int.MAX_VALUE)

@Composable fun Heading1(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 32.sp, FontWeight.SemiBold, modifier, color, Int.MAX_VALUE)

@Composable fun Heading2(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, textAlign: TextAlign? = null) =
    sized(text, 25.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE, textAlign)

@Composable fun Heading3(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, textAlign: TextAlign? = null) =
    sized(text, 22.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE, textAlign)

@Composable fun DefaultText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 19.sp, FontWeight.Normal, modifier, color, Int.MAX_VALUE)

@Composable fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) =
    sized(text, 15.5.sp, FontWeight.Normal, modifier, color, maxLines, textAlign)

private data class DetectedTextLink(
    val start: Int,
    val endExclusive: Int,
    val label: String,
    val url: String,
)

private fun normalizedWebUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) -> value
        Patterns.WEB_URL.matcher(value).matches() -> "https://$value"
        else -> null
    }
}

/**
 * User-authored text with automatically detected web links. Supports pasted
 * http(s) URLs, www/plain domains, and Markdown-style `[label](url)` links
 * without interpreting any other Markdown in community posts or comments.
 */
fun textWithLinks(
    text: String,
    linkColor: Color = Clear30Colors.accent,
): AnnotatedString {
    val markdownLinks = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")
        .findAll(text)
        .mapNotNull { match ->
            normalizedWebUrl(match.groupValues[2])?.let { url ->
                DetectedTextLink(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    label = match.groupValues[1],
                    url = url,
                )
            }
        }
        .toList()

    val detected = markdownLinks.toMutableList()
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        if (start > 0 && text[start - 1] == '@') continue
        if (markdownLinks.any { start < it.endExclusive && matcher.end() > it.start }) continue

        var end = matcher.end()
        while (end > start && text[end - 1] in ".,!?;:") end--
        while (end > start && text[end - 1] == ')' &&
            text.substring(start, end).count { it == ')' } >
            text.substring(start, end).count { it == '(' }
        ) {
            end--
        }
        if (end <= start) continue

        val label = text.substring(start, end)
        normalizedWebUrl(label)?.let { url ->
            detected += DetectedTextLink(start, end, label, url)
        }
    }

    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        var cursor = 0
        detected.sortedBy { it.start }.forEach { link ->
            if (link.start < cursor) return@forEach
            append(text.substring(cursor, link.start))
            withLink(LinkAnnotation.Url(link.url, linkStyle)) { append(link.label) }
            cursor = link.endExclusive
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

@Composable
fun SmallTextWithLinks(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    linkColor: Color = Clear30Colors.accent,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) = Text(
    textWithLinks(text, linkColor),
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = FontWeight.Normal,
    fontSize = 15.5.sp,
    lineHeight = 15.5.sp * LINE_HEIGHT_RATIO,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
    style = ClearTextStyle,
)

@Composable fun TinyText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) =
    sized(text, 14.sp, FontWeight.Normal, modifier, color, maxLines, textAlign)

@Composable
fun TinyTextWithLinks(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    linkColor: Color = Clear30Colors.accent,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) = Text(
    textWithLinks(text, linkColor),
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 14.sp * LINE_HEIGHT_RATIO,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
    style = ClearTextStyle,
)

@Composable fun MiniText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 10.sp, FontWeight.Normal, modifier, color, Int.MAX_VALUE)

/** One run of text for the highlighted SmallText init (TextSizes.swift). */
data class HighlightedTextFormat(val text: String, val highlighted: Boolean)

/**
 * Highlighted SmallText — gradient-filled bold runs for `highlighted` segments,
 * plain runs otherwise (ported from `SmallText(formats:)`). Compose renders the
 * gradient via a `SpanStyle(brush = ...)`.
 */
@Composable
fun SmallTextHighlighted(
    formats: List<HighlightedTextFormat>,
    modifier: Modifier = Modifier,
    highlightBrush: Brush = Clear30Gradients.clear30,
    nonHighlightColor: Color = Clear30Colors.text,
    highlightBold: Boolean = true,
) {
    val annotated: AnnotatedString = buildAnnotatedString {
        formats.forEach { f ->
            if (f.highlighted) {
                withStyle(SpanStyle(brush = highlightBrush, fontWeight = if (highlightBold) FontWeight.Bold else FontWeight.Normal)) {
                    append(f.text)
                }
            } else {
                withStyle(SpanStyle(color = nonHighlightColor)) { append(f.text) }
            }
        }
    }
    Text(
        annotated,
        modifier = modifier,
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 15.5.sp * LINE_HEIGHT_RATIO,
        style = ClearTextStyle,
    )
}

/**
 * Converts `**bold**` / `__bold__` runs into bold spans and single-delimiter
 * `*italic*` / `_italic_` runs into italic spans. iOS renders these strings as
 * Markdown; this covers the syntax the live content actually uses (surveyed
 * 2026-07-21: 411/435 message bodies use bold, a handful use italics, zero use
 * links/headers/lists — so no full Markdown engine is needed).
 */
fun markdownBold(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""(\*\*|__)(.+?)\1|(?<![*\w])\*([^*\n]+)\*(?!\*)|(?<![_\w])_([^_\n]+)_(?!\w)""")
    var last = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val bold = m.groupValues[2]
        if (bold.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else {
            val italic = m.groupValues[3].ifEmpty { m.groupValues[4] }
            withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(italic) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

/** Text composable that renders `**bold**` markdown emphasis, Lexend-styled. */
@Composable
fun MarkdownText(
    text: String,
    fontSize: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    markdownBold(text),
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = fontSize * LINE_HEIGHT_RATIO,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    style = ClearTextStyle,
)

/** Heading2 (25sp Medium) with `**bold**` markdown emphasis — used for question prompts. */
@Composable
fun Heading2Markdown(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    MarkdownText(text, 25.sp, FontWeight.Medium, modifier, color)

/** SmallText (15.5sp) with `**bold**` markdown emphasis — used for affirmation bodies. */
@Composable
fun SmallTextMarkdown(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    MarkdownText(text, 15.5.sp, FontWeight.Normal, modifier, color)

/** Heading3 (22sp Medium) with `**bold**` markdown emphasis. */
@Composable
fun Heading3Markdown(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    MarkdownText(text, 22.sp, FontWeight.Medium, modifier, color)

/**
 * Full BLOCK markdown for guide bodies (iOS parses these with AttributedString
 * `.full` and renders in SwiftUI Text): `#` headers → bold lines, `-`/`*`
 * bullets → • glyphs, `[label](url)` → tappable underlined links, plus the
 * inline bold/italic emphasis. The live guide content uses all of these
 * (31/32 guides carry links + headers), which the inline-only renderer showed
 * as raw syntax.
 */
fun markdownBlocks(text: String): AnnotatedString = buildAnnotatedString {
    fun AnnotatedString.Builder.appendInline(s: String) {
        val regex = Regex(
            """\[([^\]]+)\]\(([^)\s]+)\)|(\*\*|__)(.+?)\3|(?<![*\w])\*([^*\n]+)\*(?!\*)|(?<![_\w])_([^_\n]+)_(?!\w)""",
        )
        var last = 0
        for (m in regex.findAll(s)) {
            if (m.range.first > last) append(s.substring(last, m.range.first))
            val label = m.groupValues[1]
            val bold = m.groupValues[4]
            when {
                label.isNotEmpty() -> withLink(
                    LinkAnnotation.Url(
                        m.groupValues[2],
                        TextLinkStyles(
                            style = SpanStyle(
                                color = org.clear30.views.theme.Clear30Colors.meditation1,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) { append(label) }
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                else -> {
                    val italic = m.groupValues[5].ifEmpty { m.groupValues[6] }
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(italic) }
                }
            }
            last = m.range.last + 1
        }
        if (last < s.length) append(s.substring(last))
    }

    val lines = text.lines()
    lines.forEachIndexed { i, raw ->
        val line = raw.trim()
        val header = Regex("""^#{1,6}\s+(.*)""").find(line)
        val bullet = Regex("""^[-*]\s+(.*)""").find(line)
        when {
            header != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(header.groupValues[1]) }
            bullet != null -> { append("•  "); appendInline(bullet.groupValues[1]) }
            else -> appendInline(raw)
        }
        if (i != lines.lastIndex) append("\n")
    }
}

/** SmallText rendering full guide markdown (headers / bullets / tappable links). */
@Composable
fun SmallTextMarkdownBlocks(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    markdownBlocks(text),
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = FontWeight.Normal,
    fontSize = 15.5.sp,
    lineHeight = 15.5.sp * LINE_HEIGHT_RATIO,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    style = ClearTextStyle,
)
