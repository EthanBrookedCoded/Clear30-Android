package org.clear30.views.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
@Composable
private fun sized(
    text: String, size: TextUnit, weight: FontWeight,
    modifier: Modifier, color: Color, maxLines: Int,
) = Text(
    text = text,
    modifier = modifier,
    color = color,
    fontFamily = Lexend,
    fontWeight = weight,
    fontSize = size,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

@Composable fun GiganticText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 50.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE)

@Composable fun HugeText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 40.sp, FontWeight.Bold, modifier, color, Int.MAX_VALUE)

@Composable fun Heading1(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 32.sp, FontWeight.SemiBold, modifier, color, Int.MAX_VALUE)

@Composable fun Heading2(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 25.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE)

@Composable fun Heading3(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 22.sp, FontWeight.Medium, modifier, color, Int.MAX_VALUE)

@Composable fun DefaultText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    sized(text, 19.sp, FontWeight.Normal, modifier, color, Int.MAX_VALUE)

@Composable fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) =
    sized(text, 15.5.sp, FontWeight.Normal, modifier, color, maxLines)

@Composable fun TinyText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) =
    sized(text, 14.sp, FontWeight.Normal, modifier, color, maxLines)

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
    Text(annotated, modifier = modifier, fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 15.5.sp)
}
