package org.clear30.views.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * softShadow — replicates SwiftUI's `.shadow(color:radius:x:y:)`, an EVEN soft
 * glow centered on the shape (no Material elevation bias). Drawn with the native
 * `setShadowLayer` so the blur radius / color / offset match the iOS card
 * exactly. iOS uses `radius: 7, x: 0, y: 0` with `clear30Shadow` (black @ 0.2).
 *
 * Note: SwiftUI's shadow `radius` ≈ the Gaussian blur radius, so we pass it
 * straight through as the `setShadowLayer` blur radius.
 */
fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp = 7.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
): Modifier = this.drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val paint = Paint()
    val frameworkPaint = paint.asFrameworkPaint()
    frameworkPaint.color = android.graphics.Color.TRANSPARENT
    frameworkPaint.setShadowLayer(
        blurRadius.toPx(),
        offsetX.toPx(),
        offsetY.toPx(),
        color.toArgb(),
    )
    val r = cornerRadius.toPx()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, frameworkPaint)
    }
}

/**
 * cardGlow — the `glowGradient` halo from iOS CardStyle (Cards.swift:663-667):
 * a RoundedRectangle stroked with the gradient, blurred, drawn BEHIND the card
 * so only the soft edges peek out around it. iOS light mode uses a 2pt stroke,
 * 3.5 blur radius, 0.6 opacity (the app ships light-only — see Theme.kt); the
 * off-scale 0.6 alpha is a deliberate 1:1 copy of the iOS source.
 */
private fun Modifier.cardGlow(brush: Brush, cornerRadius: Dp): Modifier = this.drawBehind {
    val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = 2.dp.toPx()
    }
    brush.applyTo(size, paint, 0.6f)
    paint.asFrameworkPaint().maskFilter =
        android.graphics.BlurMaskFilter(3.5.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
    val r = cornerRadius.toPx()
    drawIntoCanvas { canvas ->
        canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

/**
 * CardStyle — ported from the `CardStyle` ViewModifier (Cards.swift), the
 * card treatment used across every screen (see CLAUDE.md).
 *
 * Compose modifier order recreates the SwiftUI layering: outer shadow → glow →
 * clip → background (gradient or solid) → border overlay → inner padding. The
 * exotic `outlineTrim` (partial progress ring) is omitted here — it's niche and
 * better drawn with Canvas in the few places that use it.
 *
 * [glowGradient] renders the iOS feed-focus halo (see [cardGlow]); pass the
 * card type's content gradient when the card is the focused feed page.
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
    glowGradient: Brush? = null,
    padding: Boolean = true,
): Modifier = composedCardStyle(
    color, shadowColor, cornerRadius, gradient, outlineGradient, outlineWidth, outlineOpacity, glowGradient, padding,
)

private fun Modifier.composedCardStyle(
    color: Color,
    shadowColor: Color,
    cornerRadius: Dp,
    gradient: Brush?,
    outlineGradient: Brush?,
    outlineWidth: Dp,
    outlineOpacity: Float,
    glowGradient: Brush?,
    padding: Boolean,
): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val bg: Brush = gradient ?: SolidColor(color)
    var m = this.softShadow(shadowColor, cornerRadius, blurRadius = 7.dp)
    if (glowGradient != null) m = m.cardGlow(glowGradient, cornerRadius)
    m = m
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
    glowGradient: Brush? = null,
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
                glowGradient = glowGradient,
                padding = padding,
            )
        ) { content() }
    }
}
