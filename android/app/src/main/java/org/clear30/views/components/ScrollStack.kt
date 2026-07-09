package org.clear30.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue

/**
 * scrollStackItem — Kotlin/Compose port of the react-bits `ScrollStack` effect
 * (https://reactbits.dev), with an added 3D perspective tilt. Apply to each page
 * of a `VerticalPager`: the centered card sits forward (full size, opaque, flat);
 * as a card is swiped away it recedes into a stack — scaling toward [baseScale],
 * fading to [recedeAlpha], held back with a parallax [overlap] (it tracks slower
 * than the pager), and leaning back on the X axis by [tilt] so the swipe reads
 * like flipping a real deck of cards. The incoming card straightens up as it
 * settles, giving the gesture a satisfying, physical "snap into place".
 *
 * @param pageOffset signed distance of this page from the centered page:
 *   `(pagerState.currentPage - page) + pagerState.currentPageOffsetFraction`
 *   (0 = focused, +1 = one page back/above, -1 = one page forward/below).
 * @param tilt degrees of 3D lean at full offset (0 disables the perspective).
 */
fun Modifier.scrollStackItem(
    pageOffset: Float,
    baseScale: Float = 0.85f,
    recedeAlpha: Float = 0.5f,
    overlap: Float = 0.08f,
    tilt: Float = 9f,
): Modifier = graphicsLayer {
    val signed = pageOffset.coerceIn(-1f, 1f)
    val dist = pageOffset.absoluteValue.coerceIn(0f, 1f)
    // smoothstep so the focused card stays crisp a touch longer, then recedes
    // quickly near the edges — a springier, more physical feel than a linear ramp.
    val eased = dist * dist * (3f - 2f * dist)
    val s = lerp(baseScale, 1f, 1f - eased)
    scaleX = s
    scaleY = s
    alpha = lerp(recedeAlpha, 1f, 1f - dist)
    translationY = pageOffset * size.height * overlap
    // 3D perspective lean. A higher cameraDistance keeps the tilt subtle (deep
    // vanishing point) rather than a hard fold.
    cameraDistance = 14f * density
    rotationX = signed * tilt
}

/**
 * popEntrance — a spring scale-up + fade-in that plays the first time a composable
 * appears, so opening a section (an in-app viewer, sheet, or overlay) "pops" into
 * place with a satisfying bounce instead of a hard cut. The low damping ratio
 * gives a gentle overshoot on settle.
 *
 * @param from starting scale (slightly under 1 reads as growing forward at you).
 */
@Composable
fun Modifier.popEntrance(from: Float = 0.9f): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else from,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f),
        label = "popScale",
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f,
        animationSpec = tween(160),
        label = "popAlpha",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
