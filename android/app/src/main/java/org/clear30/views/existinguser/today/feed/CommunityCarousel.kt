package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.clear30.data.model.Post
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * CommunityCarouselCard — ported from iOS `TodayFeedCommunity.swift` (DayPostCommunity).
 * A feed card with a horizontal carousel of recent community posts and a "share your
 * experience" CTA. Tapping a post deep-links into the Community tab's detail; the CTA
 * (and "See all") switch to the Community tab.
 */
@Composable
fun CommunityCarouselCard(
    posts: List<Post>,
    onOpenCommunity: () -> Unit,
    onOpenPost: (Post) -> Unit,
) {
    // Community-coloured backdrop shadow so the card's glow signals its type.
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = Clear30Colors.community1.copy(alpha = 0.5f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading3("Community")
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .clickable { onOpenCommunity() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TinyText("See all", color = Clear30Colors.text.copy(alpha = 0.5f))
                    Icon(sfSymbol("chevron.right"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                }
            }

            if (posts.isEmpty()) {
                SmallText("Be the first to share today.", color = Clear30Colors.text.copy(alpha = 0.5f))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    items(posts) { post -> CommunityPreview(post) { onOpenPost(post) } }
                }
            }

            DefaultButton(
                "Share your experience",
                gradient = Clear30Gradients.community,
                modifier = Modifier.fillMaxWidth(),
            ) { onOpenCommunity() }
        }
    }
}

/** A compact, fixed-width preview of one post for the horizontal carousel. */
@Composable
private fun CommunityPreview(post: Post, onClick: () -> Unit) {
    Box(
        Modifier.width(220.dp)
            .clip(RoundedCornerShape(Dimens.cornerRadius * 0.8f))
            .background(Clear30Colors.opacityGrayFlattened)
            .clickable { onClick() }
            .padding(Dimens.cardSpacing),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallText(post.title, color = Clear30Colors.text, maxLines = 2)
            if (post.body.isNotBlank()) {
                SmallText(post.body, color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 3)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(sfSymbol("bubble.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                TinyText("${post.totalCommentsCount}", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.size(Dimens.cardSpacing / 4))
                Icon(sfSymbol("chart.bar.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                TinyText("${post.viewCount}", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            TinyText("Tap to see more →", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}
