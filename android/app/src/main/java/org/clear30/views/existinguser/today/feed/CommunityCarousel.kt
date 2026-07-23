package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.PagerDots
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextHighlighted
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * CommunityCarouselCard — ported from iOS `TodayFeedCommunity.swift` (DayPostCommunity).
 * A feed card with a horizontal carousel of recent community posts and a "share your
 * experience" CTA. Tapping a post opens it in place on the Today tab.
 */
@Composable
fun CommunityCarouselCard(
    posts: List<Post>,
    onOpenPost: (Post) -> Unit,
) {
    // iOS DayPostCommunity (TodayFeedCommunity.swift): a FULL-PAGE-height
    // horizontal pager — one full card per post ("Community Responses" heading,
    // title, dim body, footer pinned by a Spacer).
    //
    // The trailing share-your-experience page (iOS CreateDayPostView, ported in
    // W12) is REMOVED per Thatcher — deliberate iOS divergence. The feed-end
    // card already carries an "Enter the Community" CTA.
    val pageCount = posts.size
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            // Same as the guides pager: this inner pager clips at the feed page's
            // width, cutting the post cards' soft shadows — bleed + re-pad
            // (iOS scrollShadowFix pattern).
            modifier = Modifier.fillMaxWidth().weight(1f).scrollShadowBleed(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.scrollShadowFix,
            ),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            DayPostCard(posts[page]) { onOpenPost(posts[page]) }
        }
        if (pageCount > 1) {
            // Page dots like the other carousels (iOS FeedView pageDots).
            PagerDots(
                count = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .padding(top = Dimens.cardSpacing / 2),
            )
        }
    }
}

/** One full-height community post page (iOS `DayPostView`). */
@Composable
private fun DayPostCard(post: Post, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxSize().clickable { onClick() }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(
                formats = listOf(org.clear30.views.components.HighlightedTextFormat("Community Responses", highlighted = true)),
                highlightBrush = Clear30Gradients.community,
            )
            Heading3(post.title)
            if (post.body.isNotBlank()) {
                SmallText(post.body, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TinyText("Tap to see more", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                Icon(sfSymbol("bubble.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(Dimens.cardSpacing / 4))
                TinyText("${post.totalCommentsCount}", color = Clear30Colors.text.copy(alpha = 0.25f))
                Spacer(Modifier.size(Dimens.cardSpacing / 2))
                Icon(sfSymbol("chart.bar.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(Dimens.cardSpacing / 4))
                TinyText("${post.viewCount}", color = Clear30Colors.text.copy(alpha = 0.25f))
            }
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
