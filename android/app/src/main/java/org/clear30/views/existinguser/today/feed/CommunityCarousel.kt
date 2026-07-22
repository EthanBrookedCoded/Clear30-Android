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
 * experience" CTA. Tapping a post deep-links into the Community tab's detail; the CTA
 * (and "See all") switch to the Community tab.
 */
@Composable
fun CommunityCarouselCard(
    posts: List<Post>,
    onOpenCommunity: () -> Unit,
    onOpenPost: (Post) -> Unit,
) {
    // iOS DayPostCommunity (TodayFeedCommunity.swift): a FULL-PAGE-height
    // horizontal pager — one full card per post ("Community Responses" heading,
    // title, dim body, footer pinned by a Spacer) plus a trailing
    // share-your-experience page (W12).
    val pageCount = posts.size + 1
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            if (page < posts.size) {
                DayPostCard(posts[page]) { onOpenPost(posts[page]) }
            } else {
                CreateDayPostCard(onOpenCommunity)
            }
        }
        if (pageCount > 1) {
            // Page dots like the other carousels (iOS FeedView pageDots).
            FeedPagerDots(
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

/** Trailing share-your-experience page (iOS `CreateDayPostView`). */
@Composable
private fun CreateDayPostCard(onOpenCommunity: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxSize()) {
        // Text at the TOP, a text+icon button pinned at the BOTTOM (not a filled
        // button) — mirrors the reddit/journal card affordance.
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            Heading3("Share your experience")
            SmallText(
                "Your story could help someone today.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().pressScale { onOpenCommunity() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText("Enter the Community", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                Icon(
                    sfSymbol("arrow.right"),
                    contentDescription = null,
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
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
