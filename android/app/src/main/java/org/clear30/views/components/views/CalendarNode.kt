package org.clear30.views.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Haptics

/**
 * StandardCalendarNode — a single calendar day cell: a 30dp rounded node (filled
 * per [CalendarNodeFillStyle]) inside a 40dp selection/today outline, with an
 * optional tap (light haptic). Matches CalendarNode.swift.
 */

/** Fill treatment for a node, mirroring iOS `CalendarNodeFillStyle`. */
sealed class CalendarNodeFillStyle(val fill: Brush, val textColor: Color) {
    class Gradient(brush: Brush) : CalendarNodeFillStyle(brush, Color.White)
    data object Gray : CalendarNodeFillStyle(Clear30Gradients.gray, Clear30Colors.text)
    data object GrayFlat : CalendarNodeFillStyle(Clear30Gradients.grayFlat, Clear30Colors.text)
    data object LowOpacity : CalendarNodeFillStyle(
        SolidColor(Clear30Colors.opacityGray.copy(alpha = Clear30Colors.opacityGray.alpha * 0.25f)),
        Clear30Colors.text,
    )
    data object Clear : CalendarNodeFillStyle(SolidColor(Color.Transparent), Clear30Colors.text)
}

@Composable
fun StandardCalendarNode(
    label: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    today: Boolean = false,
    fillStyle: CalendarNodeFillStyle = CalendarNodeFillStyle.Gray,
    showLabel: Boolean = true,
    labelOpacity: Float = 1f,
    selectedGradient: Brush? = null,
    onTap: (() -> Unit)? = null,
) {
    // Outline brush. NOTE: 0.4 / 0.125 are outside the project's allowed alpha set,
    // but they are the exact values iOS uses here (outlineSelected/TodayOpacity), so
    // for 1:1 visual parity we match the source rather than the guideline.
    val outline: Brush = when {
        selected && selectedGradient != null -> selectedGradient
        selected -> SolidColor(Clear30Colors.text.copy(alpha = 0.4f))
        today -> SolidColor(Clear30Colors.text.copy(alpha = 0.125f))
        else -> SolidColor(Color.Transparent)
    }

    val node = @Composable {
        Box(
            Modifier
                .height(48.dp)
                .alpha(if (enabled) 1f else 0.25f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(BorderStroke(2.5.dp, outline), RoundedCornerShape(12.dp)),
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(fillStyle.fill),
                contentAlignment = Alignment.Center,
            ) {
                if (showLabel) {
                    SmallText("$label", color = fillStyle.textColor, modifier = Modifier.alpha(labelOpacity))
                }
            }
        }
    }

    val outer = modifier.padding(vertical = 3.5.dp)
    if (onTap != null) {
        Box(
            outer.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                Haptics.lightImpact()
                onTap()
            },
        ) { node() }
    } else {
        Box(outer) { node() }
    }
}
