package org.clear30.views.newuser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
            .navigationBarsPadding()
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

/** iOS `Review` analogue. The real copy below is the live `library.onboarding_reviews`
 *  table (sorted by priority), transcribed here so the slide doesn't need the
 *  non-public `library` Postgrest schema wired up. */
private data class PlaceholderReview(val priority: Int, val title: String, val body: String)

private val placeholderReviews = listOf(
    PlaceholderReview(
        1, "Exceptional quality app! Kudos to the team!",
        "Woah. I rarely leave reviews on here but this is so clean and elegant.\n\n" +
            "I found this app through a TikTok of someone dancing and celebrating that they're two weeks " +
            "into their break and then mentioned this app.\n\n" +
            "I usually don't download apps like this but I guess I liked that it was advertised as just a " +
            "30-day break and not a \"you need to stop doing this immediately.\" The onboarding experience " +
            "was welcoming and made me think about my own habits without feeling guilty.",
    ),
    PlaceholderReview(
        1, "Very helpful",
        "This has been the most helpful thing during my journey to quit smoking. I do not believe I could " +
            "have come as far as I have without the Clear 30 app.\n\n" +
            "There is a wonderful community of other smokers trying to quit that you can chat with, even an AI " +
            "bot to help. Daily check ins have helped me tremendously. I 100% recommend this app. 10/10",
    ),
    PlaceholderReview(
        2, "Highly recommend",
        "I have been smoking for 8 years straight every day. I randomly got the urge to try quitting for the " +
            "second time. I saw an ad for this app and decided to give it a try — I did not expect it to work. " +
            "However I was wrong; simply seeing the time I've been clean for and the symptoms that are normal to " +
            "feel has really helped me!",
    ),
    PlaceholderReview(
        2, "New beginnings",
        "This app really helped me see how much I relied on weed. I'm a month in and I can't tell you how much " +
            "clearer and better I am feeling. I love the check ins and everything this app has to offer. I didn't " +
            "think I could ever quit weed but here I am.",
    ),
    PlaceholderReview(
        2, "Finally something personal!",
        "I really appreciated this app and how personalized it was. I didn't want to fully quit, and just wanted " +
            "to monitor my use. Nobody tried to force me to fully quit, and I was able to really cut down. When I " +
            "was struggling with cravings I reached out to peer support and they had great advice!",
    ),
    PlaceholderReview(
        3, "Incredible guided meditations!",
        "The meditations really get me through the cravings and have a lot of very real and useful insight on " +
            "getting your power back and how cravings work.",
    ),
    PlaceholderReview(
        5, "NEEDED",
        "I have been struggling with weed for a while now and this app has helped boost my mental strength to " +
            "stop. I did slip up once but it really pushed me to keep trying. If you want to quit look no further " +
            "than this app.",
    ),
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
        // Center the emoji avatars + "+ 15,000 people" as a group (D12).
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
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
