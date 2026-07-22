package org.clear30.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * PagerDots — the standard page indicator under any [androidx.compose.foundation.pager]
 * carousel (iOS `FeedView.pageDots`): a pill of 7dp dots on the translucent gray
 * chip, the active one at 0.75 alpha and the rest at 0.25.
 *
 * Used by the Today feed pagers, the craving/sleep meditation rail, and the
 * Groups benefit carousel — always centered directly under its pager.
 */
@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Clear30Colors.opacityGray)
            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) Clear30Colors.text.copy(alpha = 0.75f)
                        else Clear30Colors.text.copy(alpha = 0.25f),
                    ),
            )
        }
    }
}
