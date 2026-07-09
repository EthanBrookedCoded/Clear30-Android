package org.clear30.views.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

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

/**
 * ElectricProgressBar — [ProgressBar] with an "electric" treatment for the
 * Today-tab top bar. The brand blue→green fill continuously flows left→right over
 * a soft glow; every [shockEvery] scrolls a brief shock sweeps the whole bar,
 * lighting small jagged lightning strands across it (top and bottom) as the front
 * passes, then fading. Subtle by design — the strands appear only during the
 * ~0.75s shock, never constantly.
 *
 * The glow uses `Modifier.blur`, which renders on Android 12+ (API 31); on older
 * devices it degrades gracefully to the flowing fill with no halo.
 */
@Composable
fun ElectricProgressBar(
    current: Int,
    max: Int,
    height: Dp = 10.dp,
    color1: Color = Clear30Colors.blue,
    color2: Color = Clear30Colors.green,
    strandGlow: Color = Clear30Colors.brightGreen2,
    shockEvery: Int = 3,
) {
    val target = (current.toFloat() / maxOf(max, 1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, Anim.default(), label = "electricFill")

    val transition = rememberInfiniteTransition(label = "electricBar")
    // The fill gradient drifts left→right, seamlessly (Repeated tiling loops at 1).
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "flow",
    )
    // Gentle breathing glow.
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    // Every `shockEvery` scrolls, fire a shock whose front sweeps left→right
    // (0 → 1). It rests at 1 (finished) so no strands show between shocks.
    val shock = remember { Animatable(1f) }
    var scrolls by remember { mutableIntStateOf(0) }
    LaunchedEffect(current) {
        scrolls++
        if (scrolls > 1 && scrolls % shockEvery == 0) {
            shock.snapTo(0f)
            shock.animateTo(1f, tween(750, easing = LinearEasing))
        }
    }

    val glowBrush = remember(color1, color2) { Brush.horizontalGradient(listOf(color1, color2)) }
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    Box(Modifier.fillMaxWidth().height(height)) {
        // Track.
        Box(Modifier.fillMaxSize().clip(shape).background(Clear30Colors.opacityGray))
        // Soft glow behind the fill.
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .graphicsLayer { alpha = 0.5f * pulse }
                .blur(6.dp, BlurredEdgeTreatment.Unbounded)
                .clip(shape)
                .background(glowBrush),
        )
        // Fill — the brand gradient flowing left→right.
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(shape)
                .drawBehind {
                    val w = size.width
                    if (w > 0f) {
                        val off = flow * w
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(color1, color2, color1),
                                start = Offset(off - w, 0f),
                                end = Offset(off, 0f),
                                tileMode = TileMode.Repeated,
                            ),
                        )
                    }
                },
        )
        // Shock — small lightning strands crackle across the bar (top + bottom) as
        // the shock front sweeps left→right. Not clipped, so strands poke past the
        // edges; only drawn while a shock is in progress.
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .drawWithCache {
                    val strands = buildStrands(size.width, size.height)
                    val h = size.height
                    val coreWidth = 1.2.dp.toPx()
                    val glowWidth = 3.dp.toPx()
                    onDrawBehind {
                        val s = shock.value
                        if (s <= 0f || s >= 1f) return@onDrawBehind   // idle: no strands
                        val w = size.width
                        val front = s * (w + h * 4f) - h * 2f         // sweep past both ends
                        val window = h * 3f
                        for (st in strands) {
                            val d = abs(st.x - front)
                            if (d > window) continue
                            val near = 1f - d / window
                            val flick = 0.6f + 0.4f * sin((s * 70f + st.seed).toDouble()).toFloat()
                            val a = (near * flick).coerceIn(0f, 1f)
                            if (a < 0.05f) continue
                            drawPath(st.path, strandGlow.copy(alpha = 0.5f * a), style = Stroke(glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(st.path, color2.copy(alpha = a), style = Stroke(coreWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                },
        )
    }
}

private const val TWO_PI = 6.2831855f

private class Strand(val x: Float, val path: Path, val seed: Float)

/**
 * Builds a fixed, deterministic set of small jagged lightning strands spanning a
 * [w]×[h] bar — one every ~1.4 bar-heights along its length, each crossing from
 * just above the top edge to just below the bottom. Stable (seeded) so a shock
 * only changes their opacity, not their shape.
 */
private fun buildStrands(w: Float, h: Float): List<Strand> {
    if (w <= 0f || h <= 0f) return emptyList()
    val rng = Random(7)
    val count = (w / (h * 1.4f)).toInt().coerceIn(6, 28)
    return buildList {
        for (i in 0 until count) {
            val x = ((i + 0.5f) / count) * w
            val top = -h * (0.25f + rng.nextFloat() * 0.45f)      // pokes above the top edge
            val bottom = h * (1.25f + rng.nextFloat() * 0.45f)    // pokes below the bottom edge
            val segments = 3 + rng.nextInt(2)
            val path = Path().apply {
                moveTo(x, top)
                for (s in 1..segments) {
                    val t = s.toFloat() / segments
                    val y = top + (bottom - top) * t
                    val sway = (rng.nextFloat() - 0.5f) * h * 0.9f
                    lineTo(x + sway, y)
                }
            }
            add(Strand(x, path, rng.nextFloat() * TWO_PI))
        }
    }
}
