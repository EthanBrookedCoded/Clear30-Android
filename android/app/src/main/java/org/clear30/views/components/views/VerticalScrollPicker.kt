package org.clear30.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.abs

/**
 * VerticalScrollPicker — a vertical wheel: the item nearest the vertical center is
 * enlarged 1.2× and fully opaque, the rest sit at 0.5 opacity; scrolling snaps and
 * fires a light haptic as the centered item changes. Tapping an item scrolls it to
 * the center (selecting it). Top/bottom edges fade out. Matches
 * VerticalScrollPicker.swift (`startAtBottom` reverses the order).
 */
@Composable
fun VerticalScrollPicker(
    numItems: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startAtBottom: Boolean = true,
    content: @Composable (Int) -> Unit,
) {
    val order = remember(numItems, startAtBottom) {
        if (startAtBottom) (0 until numItems).reversed().toList() else (0 until numItems).toList()
    }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .filter { it.key is Int }
                .minByOrNull { abs((it.offset + it.size / 2f) - viewportCenter) }
                ?.key as? Int
        }
    }

    LaunchedEffect(centeredIndex) {
        val idx = centeredIndex
        if (idx != null && idx != selectedIndex) {
            onSelectedIndexChange(idx)
            Haptics.lightImpact()
        }
    }

    BoxWithConstraints(modifier) {
        val spacing = Dimens.cardSpacing
        // Big enough that the FIRST and LAST items can physically reach the
        // viewport center (iOS uses h/2 − 3·spacing, but SwiftUI's
        // `.scrollPosition(anchor: .center)` tolerates the shortfall; LazyColumn
        // clamps, which left edge items stuck off-center).
        val edgePad = maxHeight / 2 - spacing

        // Center the initial selection (captured at first composition, before the
        // centered-item observer can overwrite it during the settle).
        val initialTarget = remember { selectedIndex }
        LaunchedEffect(Unit) {
            val pos = order.indexOf(initialTarget)
            if (pos >= 0) {
                listState.scrollToItem(pos + 1) // +1 for the leading spacer
                listState.centerOn(initialTarget)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().verticalFadeMask(),
            state = listState,
            flingBehavior = fling,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("topSpacer") { Spacer(Modifier.height(edgePad)) }
            items(order, key = { it }) { index ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.2f else 1f,
                    animationSpec = Anim.default(),
                    label = "pickerScale",
                )
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = if (selected) 1f else 0.5f
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // Tap = jump: scroll the tapped item to the center;
                            // the centered-item observer then selects it.
                            scope.launch { listState.centerOn(index, animate = true) }
                        },
                ) { content(index) }
            }
            item("bottomSpacer") { Spacer(Modifier.height(edgePad)) }
        }
    }
}

/** Scroll so the visible item with [key] sits at the viewport's vertical center. */
private suspend fun LazyListState.centerOn(key: Int, animate: Boolean = false) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val delta = (item.offset + item.size / 2f) - viewportCenter
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}

/** Top/bottom fade (clear→black 0..0.1, black→clear 0.9..1), matching the iOS mask. */
private fun Modifier.verticalFadeMask(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.1f to Color.Black,
                0.9f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
