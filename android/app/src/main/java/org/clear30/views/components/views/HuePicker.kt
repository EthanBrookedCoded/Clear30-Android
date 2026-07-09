package org.clear30.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors

/**
 * HuePicker — a horizontal hue slider: a capsule track painted with a 100-step hue
 * gradient (at the given saturation/brightness) and a white rounded thumb. Matches
 * HuePicker.swift, but built natively instead of via the iOS `Sliders` package.
 *
 * `hue` is 0..1 (as on iOS). Tapping or dragging updates it.
 */
@Composable
fun HuePicker(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    saturation: Float = 0.6f,
    brightness: Float = 0.9f,
) {
    val hueColors = remember(saturation, brightness) {
        (0 until 100).map { step -> Color.hsv((step / 99f) * 360f, saturation, brightness) }
    }
    val thumbSize = 27.dp
    val trackHeight = 20.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumbSize),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Maps a pointer x to a 0..1 hue, accounting for the thumb's travel inset.
        val thumbHalfPx = with(LocalDensity.current) { (thumbSize / 2).toPx() }
        val update: (Float, Float) -> Unit = { x, widthPx ->
            val frac = ((x - thumbHalfPx) / (widthPx - thumbHalfPx * 2f)).coerceIn(0f, 1f)
            onHueChange(frac)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(hueColors)),
        )

        val thumbX = (maxWidth - thumbSize) * hue.coerceIn(0f, 1f)
        Box(
            Modifier
                .offset(x = thumbX)
                .size(thumbSize)
                .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Clear30Colors.shadow, spotColor = Clear30Colors.shadow)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        )

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> update(offset.x, size.width.toFloat()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> update(change.position.x, size.width.toFloat()) }
                },
        )
    }
}
