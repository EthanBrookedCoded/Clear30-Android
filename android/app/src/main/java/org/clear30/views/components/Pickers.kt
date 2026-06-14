package org.clear30.views.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * Segmented selector controls ported from `ViewOrganization/PillPicker.swift` and
 * `ViewOrganization/SectionMenu.swift` (the file is `SegmentedPicker.swift`).
 */

/** PillPicker style, mirroring the iOS `PillPicker.Style` enum. */
sealed class PillPickerStyle(
    val selectedFill: Brush,
    val selectedForeground: Color,
    val horizontalPadding: Dp,
    val useSmallText: Boolean,
) {
    class Primary(gradient: Brush = Clear30Gradients.clear30) :
        PillPickerStyle(gradient, Color.White, 14.dp, true)

    object Secondary :
        PillPickerStyle(SolidColor(Clear30Colors.button), Clear30Colors.text, 9.dp, false)
}

/**
 * PillPicker — an inline segmented control on an `opacityGray` track; the selected
 * item gets a gradient (primary) or solid (secondary) pill. Matches PillPicker.swift.
 *
 * The iOS `matchedGeometryEffect` sliding indicator is approximated by fading each
 * cell's own background in/out (no measured slide) — close enough for visual parity.
 */
@Composable
fun <T> PillPicker(
    selection: T,
    items: List<T>,
    label: (T) -> String,
    onChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    systemImage: ((T) -> String)? = null,
    style: PillPickerStyle = PillPickerStyle.Primary(),
    expanded: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    Row(
        modifier
            .clip(shape)
            .background(Clear30Colors.opacityGray)
            .padding(2.5.dp),
    ) {
        items.forEach { item ->
            val selected = item == selection
            val foreground by animateColorAsState(
                targetValue = if (selected) style.selectedForeground else Clear30Colors.text.copy(alpha = 0.5f),
                animationSpec = Anim.default(),
                label = "pillForeground",
            )
            val cell = Modifier
                .then(if (expanded) Modifier.weight(1f) else Modifier)
                .clip(shape)
                .then(if (selected) Modifier.background(style.selectedFill, shape) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Haptics.mediumImpact()
                    onChanged(item)
                }
                .padding(horizontal = style.horizontalPadding, vertical = 6.dp)
            Box(cell, contentAlignment = Alignment.Center) {
                when {
                    systemImage != null ->
                        Icon(sfSymbol(systemImage(item)), contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                    style.useSmallText -> SmallText(label(item), color = foreground, maxLines = 1)
                    else -> TinyText(label(item), color = foreground, maxLines = 1)
                }
            }
        }
    }
}

/** One section in a [SectionMenu], mirroring iOS `SectionMenuOption`. */
data class SectionMenuOption(
    val type: String,
    val gradient: Brush,
    val image: ImageVector? = null,
    val id: String? = null,
)

/**
 * SectionMenu — a horizontal strip of gradient "tab" chips; the selected chip is
 * filled with its gradient, enlarged 1.1×, and the strip scrolls so it stays
 * visible. Matches SectionMenu.swift.
 *
 * (iOS centers the selected chip; here we animate it into view via the lazy list.
 * Asset-image options — Reddit/YouTube icons — arrive once those vectors are
 * imported; `image` accepts any [ImageVector] meanwhile.)
 */
@Composable
fun SectionMenu(
    index: Int,
    items: List<SectionMenuOption>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Dimens.horizontalPadding,
    scrollable: Boolean = true,
    showText: Boolean = true,
) {
    if (scrollable) {
        val listState = rememberLazyListState()
        LaunchedEffect(index) { listState.animateScrollToItem(index.coerceAtLeast(0)) }
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding / 2),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = horizontalPadding + 5.dp),
        ) {
            itemsIndexed(items) { i, item ->
                SectionChip(item, selected = i == index, showText = showText) {
                    Haptics.mediumImpact()
                    onSelect(i)
                }
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding + 5.dp),
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                SectionChip(item, selected = i == index, showText = showText) {
                    Haptics.mediumImpact()
                    onSelect(i)
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    item: SectionMenuOption,
    selected: Boolean,
    showText: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Anim.default(), label = "chipScale")
    val shape = RoundedCornerShape(13.dp)
    val contentColor = if (selected) Color.White else Clear30Colors.text
    val background =
        if (selected) Modifier.background(item.gradient, shape)
        else Modifier.background(Clear30Colors.opacityGray, shape) // text @ 0.1 → opacityGray token
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (selected) 1f else 0.75f
            }
            .shadow(2.dp, shape)
            .clip(shape)
            .then(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.image?.let { Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp)) }
        if (showText) TinyText(item.type, color = contentColor, maxLines = 1)
    }
}
