package org.clear30.views.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * CardStyle — ported from the `CardStyle` ViewModifier (Cards.swift), the
 * card treatment used across every screen (see CLAUDE.md).
 *
 * Compose modifier order recreates the SwiftUI layering: outer shadow → clip →
 * background (gradient or solid) → border overlay → inner padding. The exotic
 * `outlineTrim` (partial progress ring) and `glowGradient` blur are omitted here
 * — they're niche and better drawn with Canvas in the few places that use them.
 *
 * SwiftUI's `.foregroundColor` (white text on a gradient) can't be set from a
 * Modifier, so use [Clear30Card] when you need the content color applied too.
 */
fun Modifier.cardStyle(
    color: Color = Clear30Colors.button,
    shadowColor: Color = Clear30Colors.shadow,
    cornerRadius: Dp = Dimens.cornerRadius,
    gradient: Brush? = null,
    outlineGradient: Brush? = null,
    outlineWidth: Dp = 3.dp,
    outlineOpacity: Float = 1f,
    padding: Boolean = true,
): Modifier = composedCardStyle(
    color, shadowColor, cornerRadius, gradient, outlineGradient, outlineWidth, outlineOpacity, padding,
)

private fun Modifier.composedCardStyle(
    color: Color,
    shadowColor: Color,
    cornerRadius: Dp,
    gradient: Brush?,
    outlineGradient: Brush?,
    outlineWidth: Dp,
    outlineOpacity: Float,
    padding: Boolean,
): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val bg: Brush = gradient ?: SolidColor(color)
    var m = this
        .shadow(elevation = 7.dp, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(bg, shape)
    if (outlineGradient != null && outlineWidth > 0.dp) {
        m = m
            .graphicsLayer { alpha = outlineOpacity }
            .border(BorderStroke(outlineWidth, outlineGradient), shape)
    }
    if (padding) m = m.padding(16.dp)
    return m
}

/**
 * Composable wrapper around [cardStyle] that also applies the foreground/content
 * color (white over a gradient, else `clear30Text`) — the full equivalent of the
 * SwiftUI modifier.
 */
@Composable
fun Clear30Card(
    modifier: Modifier = Modifier,
    color: Color = Clear30Colors.button,
    shadowColor: Color = Clear30Colors.shadow,
    cornerRadius: Dp = Dimens.cornerRadius,
    gradient: Brush? = null,
    outlineGradient: Brush? = null,
    outlineWidth: Dp = 3.dp,
    outlineOpacity: Float = 1f,
    padding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val contentColor = if (gradient != null) Color.White else Clear30Colors.text
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        androidx.compose.foundation.layout.Box(
            modifier.cardStyle(
                color = color,
                shadowColor = shadowColor,
                cornerRadius = cornerRadius,
                gradient = gradient,
                outlineGradient = outlineGradient,
                outlineWidth = outlineWidth,
                outlineOpacity = outlineOpacity,
                padding = padding,
            )
        ) { content() }
    }
}
