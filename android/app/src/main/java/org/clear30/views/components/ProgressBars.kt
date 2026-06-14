package org.clear30.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import kotlin.math.min
import kotlin.math.pow

/**
 * Progress bars ported 1:1 from the iOS ViewOrganization ProgressBar.swift files
 * (GradientProgressBar / SegmentedProgressBar / InfiniteProgressBar /
 * ProgressBar). Tracks use `clear30OpacityGray`; fills use the brand gradient.
 *
 * (Two screens — NormativeFeedbackView and PreviousBreaksSection — still define
 * their own private `ProgressBar`; those can be migrated onto [ProgressBar] here
 * in a later cleanup pass.)
 */

/**
 * GradientProgressBar — a fill whose gradient slowly rotates (a gradient-filled
 * circle, sized `width + 20`, centered on the bar, rotating 360° every 5s and
 * masked to the filled rounded rect). Matches GradientProgressBar.swift.
 */
@Composable
fun GradientProgressBar(
    value: Float,
    width: Dp,
    height: Dp,
    color1: Color,
    color2: Color,
    cornerRadius: Dp = 10.dp,
) {
    val transition = rememberInfiniteTransition(label = "gradientProgress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(Modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val filledWidth = value.coerceIn(0f, 1f) * w
        val r = cornerRadius.toPx()
        val diameter = w + 20.dp.toPx()
        val center = Offset(w / 2f, h / 2f)
        val maskPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, filledWidth, h, CornerRadius(r, r)))
        }
        clipPath(maskPath) {
            rotate(degrees = angle, pivot = center) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(color1, color2),
                        start = Offset(center.x - diameter / 2f, center.y - diameter / 2f),
                        end = Offset(center.x + diameter / 2f, center.y + diameter / 2f),
                    ),
                    radius = diameter / 2f,
                    center = center,
                )
            }
        }
    }
}

/**
 * SegmentedProgressBar — a row of rounded segments; filled ones get the brand
 * gradient, the next (in-progress) one is enlarged 1.25×. The control keeps an
 * aspect ratio of `totalSegments * 1.5`. Matches SegmentedProgressBar.swift.
 */
@Composable
fun SegmentedProgressBar(
    totalSegments: Int,
    filledSegments: Int,
    modifier: Modifier = Modifier,
) {
    val spacingFactor = 0.2f
    val enlargementFactor = 1.25f
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(totalSegments * 1.5f)) {
        // padding per side = totalWidth * spacingFactor / (2 * total) — same as iOS,
        // and with weight(1f) cells the visible segment width works out to 0.8/total.
        val segmentPadding = maxWidth * spacingFactor / (2 * totalSegments)
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (index in 0 until totalSegments) {
                val isFilled = index < filledSegments
                val isEnlarged = index == filledSegments
                val scale by animateFloatAsState(
                    targetValue = if (isEnlarged) enlargementFactor else 1f,
                    animationSpec = Anim.default(),
                    label = "segmentScale",
                )
                val fill =
                    if (isFilled) Modifier.background(Clear30Gradients.clear30)
                    else Modifier.background(Clear30Colors.opacityGray)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = segmentPadding)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(6.dp))
                        .then(fill),
                )
            }
        }
    }
}

/**
 * InfiniteProgressBar — a never-quite-full bar: each step adds a decaying amount
 * (`1 - 0.9^step` over `1 - 0.9^max`), so it approaches but never reaches 100%.
 * Matches InfiniteProgressBar.swift.
 */
@Composable
fun InfiniteProgressBar(
    currentProgress: Int,
    maxEstimate: Int,
    brush: Brush = Clear30Gradients.clear30,
    height: Dp = 10.dp,
) {
    val decay = 0.9
    val progress = if (maxEstimate <= 0) {
        0.0
    } else {
        val step = min(currentProgress, maxEstimate)
        (1 - decay.pow(step)) / (1 - decay.pow(maxEstimate))
    }
    val animated by animateFloatAsState(
        targetValue = progress.toFloat().coerceIn(0f, 1f),
        animationSpec = Anim.default(),
        label = "infiniteProgress",
    )
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().clip(shape).background(Clear30Colors.opacityGray))
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(shape).background(brush))
    }
}

/**
 * ProgressBar — the plain linear bar (fraction `current / max`), filled with a
 * gradient when provided, else a solid color. Matches ProgressBar.swift.
 */
@Composable
fun ProgressBar(
    current: Int,
    max: Int,
    color: Color = Clear30Colors.text,
    brush: Brush? = null,
    height: Dp = 10.dp,
) {
    val progress = (current.toFloat() / maxOf(max, 1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = Anim.default(),
        label = "progress",
    )
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    Box(Modifier.fillMaxWidth().height(height).clip(shape)) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.opacityGray))
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(brush ?: SolidColor(color)))
    }
}
