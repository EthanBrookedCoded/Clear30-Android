package org.clear30.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/**
 * Styled text inputs ported from `Defaults/Inputs.swift`. iOS uses plain SwiftUI
 * `TextField`s with custom fonts/backgrounds; here `BasicTextField` + a
 * `decorationBox` reproduce that (no Material underline). All show the placeholder
 * while empty, at half opacity.
 *
 * (`returnNewLine = false` strips newlines rather than iOS's drop-last-and-defocus —
 * a minor behavioral difference. `SmallTextEditor` maps to [MultiLineInput].)
 */

@Composable
private fun Placeholder(text: String, fontSize: TextUnit, weight: FontWeight = FontWeight.Normal) {
    Text(text, fontFamily = Lexend, fontWeight = weight, fontSize = fontSize, color = Clear30Colors.text.copy(alpha = 0.5f))
}

/** DefaultTextInput — plain single-line field, Lexend `fontSize` (default 19). */
@Composable
fun DefaultTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 19.sp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Lexend, fontSize = fontSize, color = Clear30Colors.text),
        cursorBrush = SolidColor(Clear30Colors.text),
        decorationBox = { inner ->
            if (value.isEmpty()) Placeholder(placeholder, fontSize)
            inner()
        },
    )
}

/** Heading3Input — multi-line title field, Lexend 22 medium. */
@Composable
fun Heading3Input(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    returnNewLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (returnNewLine) it else it.replace("\n", "")) },
        modifier = modifier,
        textStyle = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Clear30Colors.text),
        cursorBrush = SolidColor(Clear30Colors.text),
        decorationBox = { inner ->
            if (value.isEmpty()) Placeholder(placeholder, 22.sp, FontWeight.Medium)
            inner()
        },
    )
}

/** OffWhiteInput — single line on an `opacityGray` rounded field. `numsOnly` keeps digits only. */
@Composable
fun OffWhiteInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numsOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (numsOnly) it.filter(Char::isDigit) else it) },
        modifier = modifier.fillMaxWidth().clip(shape).background(Clear30Colors.opacityGray),
        singleLine = true,
        textStyle = TextStyle(fontFamily = Lexend, fontSize = 20.sp, color = Clear30Colors.text),
        keyboardOptions = if (numsOnly) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        cursorBrush = SolidColor(Clear30Colors.text),
        decorationBox = { inner ->
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Placeholder(placeholder, 20.sp)
                inner()
            }
        },
    )
}

/** MultiLineOffWhiteInput — growing field on an `opacityGray` rounded field. */
@Composable
fun MultiLineOffWhiteInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    returnNewLine: Boolean = false,
    smallText: Boolean = false,
) {
    val shape = RoundedCornerShape(if (smallText) 15.dp else Dimens.cornerRadius)
    val fontSize = if (smallText) 15.sp else 20.sp
    val vPad = if (smallText) Dimens.cardSpacing * 0.8f else Dimens.cardSpacing
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (returnNewLine) it else it.replace("\n", "")) },
        modifier = modifier.fillMaxWidth().clip(shape).background(Clear30Colors.opacityGray),
        textStyle = TextStyle(fontFamily = Lexend, fontSize = fontSize, color = Clear30Colors.text),
        cursorBrush = SolidColor(Clear30Colors.text),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = Dimens.cardSpacing, vertical = vPad)) {
                if (value.isEmpty()) Placeholder(placeholder, fontSize)
                inner()
            }
        },
    )
}

/** MultiLineInput — growing field with a `text @ 0.25` outline, radius 13. */
@Composable
fun MultiLineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    returnNewLine: Boolean = false,
) {
    val shape = RoundedCornerShape(13.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (returnNewLine) it else it.replace("\n", "")) },
        modifier = modifier
            .clip(shape)
            .border(2.5.dp, Clear30Colors.text.copy(alpha = 0.25f), shape),
        textStyle = TextStyle(fontFamily = Lexend, fontSize = 17.sp, color = Clear30Colors.text),
        cursorBrush = SolidColor(Clear30Colors.text),
        decorationBox = { inner ->
            Box(Modifier.padding(10.dp)) {
                if (value.isEmpty()) Placeholder(placeholder, 17.sp)
                inner()
            }
        },
    )
}
