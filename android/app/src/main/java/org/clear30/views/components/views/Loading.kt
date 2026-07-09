package org.clear30.views.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors

/**
 * Loading affordances ported from `Views/LoadingIcon.swift` and the `shimmer`
 * modifier in `Views/ShimmerView.swift`. (LoadingScreen.swift's `LoadingCoordinator`
 * already lives in `data/`; its `SplashScreen` is deferred to the app-shell batch,
 * as it needs the launcher-icon resource.)
 */

/**
 * LoadingIcon — a 28dp ring trimmed to 60% that spins 360° every 0.9s. Matches
 * LoadingIcon.swift.
 */
@Composable
fun LoadingIcon(
    modifier: Modifier = Modifier,
    color: Color = Clear30Colors.opacityGray,
    scale: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "loadingIcon")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(
        modifier
            .size(28.dp)
            .graphicsLayer {
                rotationZ = angle
                scaleX = scale
                scaleY = scale
            },
    ) {
        val strokeWidth = 3.dp.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 0.6f * 360f,
            useCenter = false,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/**
 * shimmer — sweeps a soft white highlight band (60% of the width) left→right every
 * `durationMillis`, blended as overlay and clipped to `cornerRadius`. Matches the
 * `.shimmer(...)` view modifier in ShimmerView.swift.
 */
fun Modifier.shimmer(
    isActive: Boolean = true,
    durationMillis: Int = 1500,
    cornerRadius: Dp = 0.dp,
): Modifier = composed {
    if (!isActive) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    drawWithContent {
        drawContent()
        val gradientWidth = size.width * 0.6f
        val totalTravel = size.width + gradientWidth
        val startX = phase * totalTravel - gradientWidth
        val radius = cornerRadius.toPx()
        val band = Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0f),
            0.5f to Color.White.copy(alpha = 0.75f),
            1f to Color.White.copy(alpha = 0f),
            startX = startX,
            endX = startX + gradientWidth,
        )
        clipPath(
            Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
            },
        ) {
            drawRect(brush = band, blendMode = BlendMode.Overlay)
        }
    }
}
