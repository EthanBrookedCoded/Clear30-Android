package org.clear30.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout helpers ported from `ViewOrganization/FlowLayout.swift` and
 * `ViewOrganization/GridView.swift`.
 */

/**
 * FlowLayout — wrap items onto multiple lines with even spacing, aligned to the
 * given horizontal edge. The iOS version is a custom `Layout`; Compose ships
 * `FlowRow`, so this is a thin wrapper. (iOS centers items vertically within each
 * line; `FlowRow` top-aligns them — a minor difference for same-height chips.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowLayout(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    spacing: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing, horizontalAlignment),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

/**
 * GridView — a fixed-column grid (row-major fill). Matches GridView.swift.
 *
 * Cells use `weight(1f)` so columns are equal width, and absent trailing cells
 * keep their slot (so the final row stays aligned to the grid). The iOS `lazy`
 * flag is dropped: Compose can't safely nest a `LazyColumn` inside another scroll
 * — callers wanting true laziness should reach for `LazyVerticalGrid` standalone.
 */
@Composable
fun <T> GridView(
    data: List<T>,
    columns: Int,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val rows = (data.size + columns - 1) / columns
    Column(modifier) {
        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = spacing),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    Box(Modifier.weight(1f)) {
                        if (index < data.size) content(data[index])
                    }
                }
            }
        }
    }
}
