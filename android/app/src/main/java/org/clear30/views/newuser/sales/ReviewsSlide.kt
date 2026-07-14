package org.clear30.views.newuser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingReviews — 1:1 port of `OnboardingReviews.swift`.
 *
 * Leading-aligned scroll:
 *   Heading3   "Clear30 was designed for people like you."
 *   SmallText  "Give us a rating!" (opacity 0.5)
 *   InfoScreenLaurels (big)   — two laurel flourishes around 5 big gold stars (centered)
 *   users row                 — 3 emoji circles + "+ 15,000 people" (centered)
 *   reviewsList               — IntroScreenReviewCard for each review
 *
 *   TextIconButton "Next" (foreground 0.5 + disabled for the first 2s on appear)
 *
 * The Supabase `getReviews` fetch is a TODO — the iOS view starts with an empty
 * list and populates after the network round-trip; we mirror that with static
 * placeholder testimonials until the RPC is wired, so the card layout is faithful.
 */
@Composable
fun ReviewsSlide(userInfo: UserInfo, onNext: () -> Unit) {
    @Suppress("UNUSED_PARAMETER") val u = userInfo
    var canProgress by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // iOS `requestReview()` on appear — the Play In-App Review equivalent.
        // Silently no-ops when Play isn't available (emulator, sideload) or the
        // quota is exhausted, exactly like StoreKit's requestReview.
        runCatching {
            val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>()
                .firstOrNull()
            if (activity != null) {
                val manager = com.google.android.play.core.review.ReviewManagerFactory.create(context)
                manager.requestReviewFlow().addOnSuccessListener { info ->
                    manager.launchReviewFlow(activity, info)
                }
            }
        }
        delay(2000)
        canProgress = true
    }

    // TODO(port): SupabaseController.getReviews → replace these placeholders.
    // Mirrors the iOS first-paint state (empty), populated post-fetch.
    val reviews = remember { placeholderReviews }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        horizontalAlignment = Alignment.Start,
    ) {
        // ScrollView with the iOS `.fadeOut(fadeLength: 10)` edge treatment —
        // content dissolves over the last 10dp at the top and bottom of the
        // scroll viewport instead of hard-clipping.
        Column(
            Modifier
                .weight(1f)
                .fadeOutEdges(10.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.Top,
        ) {
            Heading3(
                "Clear30 was designed for people like you.",
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
            )
            SmallText(
                "Give us a rating!",
                modifier = Modifier.padding(bottom = Dimens.cardSpacing * 3),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )

            InfoScreenLaurels(
                big = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.cardSpacing / 2),
            )

            UsersRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.cardSpacing * 3),
            )

            // reviewsList — sorted by priority on iOS; static placeholders here.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                reviews.sortedBy { it.priority }.forEach { review ->
                    IntroScreenReviewCard(title = review.title, bodyText = review.body)
                }
            }
        }

        // TextIconButton "Next" — foreground 0.5 + disabled until the timer fires.
        NextIconButton(
            enabled = canProgress,
            modifier = Modifier.padding(top = Dimens.cardSpacing),
            onClick = onNext,
        )
    }
}

/** Local placeholder analogue of the iOS `Review` Codable until getReviews lands. */
private data class PlaceholderReview(val priority: Int, val title: String, val body: String)

private val placeholderReviews = listOf(
    PlaceholderReview(0, "Life changing", "Clear30 gave me the structure and support I needed to finally take a break. The daily check-ins keep me honest."),
    PlaceholderReview(1, "Worth every penny", "I tried quitting on my own so many times. Having a plan made all the difference — I'm on day 22 and feeling amazing."),
    PlaceholderReview(2, "So much clarity", "I sleep better, I'm more present with my family, and I finally feel like myself again. Highly recommend."),
)

/**
 * InfoScreenLaurels (big) — port of `InfoScreenLaurels`. Five gold stars between
 * two laurel branches. `laurel.leading` / `laurel.trailing` SF symbols aren't in
 * the Material map, so we render Unicode laurel flourishes — the leading one
 * mirrored horizontally so the pair wraps the stars like the iOS wreath. Stars
 * carry the iOS `journalsGradient` tint (O9).
 */
@Composable
private fun InfoScreenLaurels(big: Boolean, modifier: Modifier = Modifier) {
    val starHeight = if (big) 30.dp else 20.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🌿", fontSize = if (big) 56.sp else 36.sp, color = LocalContentColor.current,
            modifier = Modifier.graphicsLayer { scaleX = -1f },
        )
        Spacer(Modifier.size(Dimens.cardSpacing / 4))
        Row(
            Modifier.gradientTint(Clear30Gradients.journals),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            repeat(5) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Clear30Colors.journal1,
                    modifier = Modifier.size(starHeight),
                )
            }
        }
        Spacer(Modifier.size(Dimens.cardSpacing / 4))
        Text("🌿", fontSize = if (big) 56.sp else 36.sp, color = LocalContentColor.current)
    }
}

/**
 * iOS `.foregroundStyle(journalsGradient)` on icon runs — composites the row
 * offscreen and stamps the gradient over the rendered pixels (SrcAtop).
 */
private fun Modifier.gradientTint(brush: androidx.compose.ui.graphics.Brush): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
    }

/**
 * iOS `.fadeOut(fadeLength:)` — masks the composable so content dissolves over
 * the first/last [fadeLength] of its bounds (DstIn alpha mask).
 */
private fun Modifier.fadeOutEdges(fadeLength: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = fadeLength.toPx().coerceAtMost(size.height / 2)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Transparent,
                (fade / size.height) to Color.Black,
                (1f - fade / size.height) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/**
 * IntroScreenReviewCard — port of `IntroScreenReviewCard`: a leading-aligned
 * card with a row of 5 gold stars, the review title (DefaultText) and the body
 * (TinyText), wrapped in the standard Clear30 card treatment.
 */
@Composable
private fun IntroScreenReviewCard(title: String, bodyText: String) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(
                Modifier
                    .padding(bottom = Dimens.cardSpacing / 2)
                    .gradientTint(Clear30Gradients.journals),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                repeat(5) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Clear30Colors.journal1,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DefaultText(title)
            TinyText(bodyText)
        }
    }
}

/** users — 3 emoji avatar circles + "+ 15,000 people" (port of `users`). */
@Composable
private fun UsersRow(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        listOf("☺️", "😎", "🥹").forEach { e ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Clear30Colors.opacityGray),
                contentAlignment = Alignment.Center,
            ) { SmallText(e) }
        }
        SmallText("+ 15,000 people", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

/**
 * Next CTA — port of the iOS `TextIconButton(text:"Next", imageName:"arrow.right",
 * foregroundOpacity: canProgress ? 1 : 0.5).disabled(!canProgress)`: a full-width
 * white card pill with centered dark text + trailing arrow. While disabled the
 * foreground dims to 0.5 AND the press interaction (scale + haptic + click) is
 * suppressed entirely, matching SwiftUI `.disabled`.
 */
@Composable
private fun NextIconButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // iOS flips canProgress inside withAnimation(defaultAnimation) — animate the dim.
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = Anim.default(),
        label = "nextAlpha",
    )
    Clear30Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressScale(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallText("Next", color = Clear30Colors.text.copy(alpha = contentAlpha), maxLines = 1)
            Icon(
                sfSymbol("arrow.right"),
                contentDescription = null,
                tint = Clear30Colors.text.copy(alpha = contentAlpha),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
