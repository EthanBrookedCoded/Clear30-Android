package org.clear30.views.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import org.clear30.views.theme.Clear30Colors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ConfettiOverlay — a self-contained Compose particle confetti burst, ported from
 * the iOS `ConfettiCheckIn` (confettiCannon: 50 blue/green/yellow pieces sprayed
 * across the top half, radius 200, then rained down and faded out). No third-party
 * library: a [Canvas] integrates each piece's launch velocity + gravity over the
 * animated elapsed time, rotating it as it flies, fading out over the tail.
 *
 * Fire-and-forget: drop it into a full-screen overlay; it plays once for
 * [durationMillis] then holds the final (faded) frame. The host removes it.
 */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    durationMillis: Int = 1500,
    count: Int = 50,
    colors: List<Color> = listOf(Clear30Colors.blue, Clear30Colors.green, Clear30Colors.yellow),
) {
    val particles = remember {
        List(count) {
            ConfettiParticle(
                color = colors[Random.nextInt(colors.size)],
                angle = Random.nextFloat() * PI.toFloat(),            // 0..180° (top half)
                speed = 0.55f + Random.nextFloat() * 0.7f,            // fraction of radius
                sizePx = 14f + Random.nextFloat() * 12f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,    // deg/sec
                startRotation = Random.nextFloat() * 360f,
                spread = Random.nextFloat() - 0.5f,                    // -0.5..0.5
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis, easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val radius = minOf(w, h) * 0.9f
        val origin = Offset(w / 2f, h * 0.55f)
        val time = progress.value * (durationMillis / 1000f)          // seconds elapsed
        val gravity = radius * 1.7f                                    // px/s²
        // Fade the whole burst out over the final 30% of its life.
        val fade = if (progress.value > 0.7f) (1f - (progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f

        particles.forEach { p ->
            val vx = cos(p.angle) * p.speed * radius
            val vy = -sin(p.angle) * p.speed * radius
            val x = origin.x + p.spread * radius * 0.25f + vx * time
            val y = origin.y + vy * time + 0.5f * gravity * time * time
            if (y > h + 60f) return@forEach
            rotate(degrees = p.startRotation + p.rotationSpeed * time, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.sizePx / 2f, y - p.sizePx / 4f),
                    size = Size(p.sizePx, p.sizePx / 2f),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val color: Color,
    val angle: Float,
    val speed: Float,
    val sizePx: Float,
    val rotationSpeed: Float,
    val startRotation: Float,
    val spread: Float,
)
