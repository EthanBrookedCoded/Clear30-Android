package org.clear30.views.newuser.assessment

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * SegmentedCircleAnimation — ported from the iOS post-assessment loading
 * screen. Renders a stack of three concentric segmented rings that each
 * sweep through 360° at different speeds, then fade in 5 milestone copy
 * lines as we move through `totalMs` of "calculating your plan" time.
 *
 * Once the animation finishes (the last copy line reads), [onCompleted]
 * fires so the parent advances to the next slide. Total wall time defaults
 * to ~4 seconds — long enough to feel intentional, short enough not to
 * irritate.
 */
private val MILESTONES = listOf(
    "Reading your answers…",
    "Matching with research…",
    "Personalizing your plan…",
    "Picking your daily check-in time…",
    "Ready 💚",
)

@Composable
fun SegmentedCircleAnimation(
    totalMs: Int = 4000,
    onCompleted: () -> Unit,
) {
    // One Animatable per ring; each is allowed to spin from 0..1 (= 360°),
    // started at the same time, but with different durations so they
    // visually decouple.
    val outer = remember { Animatable(0f) }
    val middle = remember { Animatable(0f) }
    val inner = remember { Animatable(0f) }
    var milestoneIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // The "fastest" ring laps several times across the four-second window;
        // the slow ring barely completes one revolution. Linear easing keeps
        // it mechanical — like an instrument calibrating rather than a UI.
        kotlinx.coroutines.coroutineScope {
            launch { outer.animateTo(3f, tween(totalMs, easing = LinearEasing)) }
            launch { middle.animateTo(2f, tween(totalMs, easing = LinearEasing)) }
            launch { inner.animateTo(1f, tween(totalMs, easing = LinearEasing)) }
        }
    }

    LaunchedEffect(Unit) {
        // Reveal the milestone lines at evenly spaced intervals across the
        // animation, ending with the "Ready" line just before completion.
        val step = totalMs / MILESTONES.size
        MILESTONES.indices.forEach { i ->
            kotlinx.coroutines.delay(step.toLong())
            milestoneIndex = i + 1
        }
        // Tiny pause on the final line so the user sees it.
        kotlinx.coroutines.delay(400)
        onCompleted()
    }

    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(Dimens.cardSpacing))
        Heading1("Hang tight")
        Spacer(Modifier.size(Dimens.cardSpacing * 2))
        Box(
            Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            SegmentedRing(
                progress = outer.value,
                segments = 16,
                ringSize = 220.dp,
                stroke = 6.dp,
                brush = Clear30Gradients.clear30,
            )
            SegmentedRing(
                progress = middle.value,
                segments = 12,
                ringSize = 170.dp,
                stroke = 6.dp,
                brush = Clear30Gradients.community,
            )
            SegmentedRing(
                progress = inner.value,
                segments = 8,
                ringSize = 120.dp,
                stroke = 6.dp,
                brush = Clear30Gradients.meditation,
            )
        }
        Spacer(Modifier.size(Dimens.cardSpacing * 2))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MILESTONES.take(milestoneIndex).forEachIndexed { idx, line ->
                // Only animate the latest reveal — already-shown lines stay
                // visible to convey progress.
                if (idx == milestoneIndex - 1) {
                    SmallText(line, color = Clear30Colors.text)
                } else {
                    SmallText(line, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * A "segmented" progress ring — rendered as `segments` short arcs around the
 * circle, with [progress] of them lit up in the brand brush. We draw the lit
 * segments at full alpha and the unlit ones at the background's gray so the
 * ring reads as a dotted dial rather than a continuous arc.
 */
@Composable
private fun SegmentedRing(
    progress: Float,
    segments: Int,
    ringSize: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
    brush: Brush,
) {
    val effectiveProgress = (progress - progress.toInt()).coerceIn(0f, 1f)
    val litCount = (effectiveProgress * segments).toInt().coerceAtMost(segments)
    Canvas(modifier = Modifier.size(ringSize)) {
        val arcWidth = stroke.toPx()
        val arcAngle = 360f / segments
        val sweep = arcAngle - 6f  // 6° gap between segments for the "dial" look
        val topLeft = androidx.compose.ui.geometry.Offset(arcWidth / 2, arcWidth / 2)
        val arcSize = androidx.compose.ui.geometry.Size(size.width - arcWidth, size.height - arcWidth)
        repeat(segments) { i ->
            val start = -90f + i * arcAngle + 3f  // start at 12 o'clock, leave a 3° leading gap
            if (i < litCount) {
                drawArc(
                    brush = brush,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = arcWidth),
                    topLeft = topLeft,
                    size = arcSize,
                )
            } else {
                drawArc(
                    color = Clear30Colors.opacityGray,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = arcWidth),
                    topLeft = topLeft,
                    size = arcSize,
                )
            }
        }
    }
}

