package org.clear30.views.newuser.assessment

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getAssessmentSocialProofValue
import org.clear30.data.supabase.getCommunityPostById
import org.clear30.data.supabase.incrementAssessmentSocialProofCount
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import org.clear30.views.theme.Lexend
import kotlin.math.absoluteValue

// MARK: - Data Models (ported from AssessmentSocialProof.swift)

private data class SocialProofStat(
    val percentage: String,
    val label: String,
    val subtitle: String,
    val isPositive: Boolean, // true for increase, false for decrease
)

private data class SocialProofReview(
    val rating: String,
    val reviewCount: String,
)

private sealed interface TestimonialType {
    data class CommunityPost(
        val postId: String,
        val title: String,
        val body: String,
    ) : TestimonialType

    data class Review(val title: String, val body: String, val rating: Double) : TestimonialType
}

/** iOS `SheetType` — the tapped testimonial's detail sheet (O5). */
private sealed interface SocialProofSheet {
    data class PostDetail(val title: String, val body: String) : SocialProofSheet
    data class ReviewDetail(val title: String, val body: String, val rating: Double) : SocialProofSheet
}

private data class SocialProofData(
    val peopleCount: String,
    val stats: List<SocialProofStat>,
    val review: SocialProofReview,
    val testimonials: List<TestimonialType>,
)

/**
 * Backup data — mirrors `SocialProofData.backupData` from the iOS file. Network
 * loading (`getAssessmentSocialProofData` / `incrementAssessmentSocialProofCount`)
 * is not wired yet, so we render this static set as the final visible state.
 *
 * The headline rating is "4.8" with "3.1k ratings" (the App Store figure the team
 * surfaces here); the iOS backup string is older, so this matches current copy.
 */
// TODO(port): fetch live data via SupabaseController.getAssessmentSocialProofData
//             and increment the live counter on appear. CommunityPost bodies are
//             fetched by id on iOS (getCommunityPostById); here they are inlined.
private val backupSocialProofData = SocialProofData(
    // Fallback only (used when the DB row is absent, e.g. the local stack). The
    // live count is fetched from library.one_offs at runtime.
    peopleCount = "132,485",
    stats = listOf(
        SocialProofStat(
            percentage = "88.4%",
            label = "Mental Health",
            subtitle = "after 30 days",
            isPositive = true,
        ),
        SocialProofStat(
            percentage = "90%",
            label = "Cannabis Use",
            subtitle = "for committed users",
            isPositive = false,
        ),
    ),
    review = SocialProofReview(rating = "4.8", reviewCount = "3.1k ratings"),
    testimonials = listOf(
        TestimonialType.CommunityPost(
            postId = "7b893f09-1558-446b-b468-e4da4a684657",
            title = "Day 14 and I actually slept",
            body = "I never thought I'd make it two weeks. The cravings still come but they pass so much faster now, and I woke up today feeling clear for the first time in years. To anyone on day 1 — it really does get easier.",
        ),
        TestimonialType.Review(
            title = "This app is deadass amazing",
            body = "I've been smoking for about 2 years straight every day and I wanted to go on a fat t break to clear my mind and lungs I've never been able to fully make it through but this app helped me soooo much with my cravings and not being able to sleep at night I highly recommend this app to anyone who needs a break from bud",
            rating = 5.0,
        ),
        TestimonialType.CommunityPost(
            postId = "e38da793-a1d4-489f-9bb2-088a69bc034d",
            title = "The community kept me going",
            body = "Reading other people's check-ins every morning is what got me through the rough days. Knowing I wasn't the only one feeling restless made all the difference. We're all in this together.",
        ),
        TestimonialType.Review(
            title = "Exceptional quality app! Kudos to the team!",
            body = "Woah. I rarely leave reviews on here but this is so clean and elegant. \n\nI found this app through a TikTok of someone dancing and celebrating that they're two weeks into their break and then mentioned this app. \n\nI usually don't download apps like this but I guess I liked that was it was advertised (on the App Store page) as just a 30-day break and not a \"you need to stop doing this immediately.\" The onboarding experience was welcoming and made me think about my own habits without feeling guilty. I feel like I'm learning more about my consumption and understanding why I feel the way I feel sometimes already. \n\nSide note: I'm an iOS developer as well and this app sets a pretty high bar for quality and craft - I'm now a little more motivated about getting my own work to this level. Kudos to the team!",
            rating = 5.0,
        ),
        TestimonialType.CommunityPost(
            postId = "e380938e-5fa3-4575-8f46-148f8bef67d9",
            title = "30 days clear today 🎉",
            body = "I'm posting this through tears. A month ago I couldn't picture a single day without it. Today I hit 30 and I feel like I got a piece of myself back. Thank you to everyone here.",
        ),
        TestimonialType.Review(
            title = "Incredible guided meditations!",
            body = "The meditations really get me through the cravings and has a lot of very real and useful insight on getting your power back and how cravings work.",
            rating = 5.0,
        ),
        TestimonialType.CommunityPost(
            postId = "df9817e9-622e-4ab5-b40d-e74bb7aa12aa",
            title = "Wish I'd found this years ago",
            body = "The check-ins, the audios, the people — it all just works. I've tried quitting on my own so many times. Having something in my pocket that actually understands what this feels like changed everything.",
        ),
    ),
)

// MARK: - Main View

/**
 * AssessmentSocialProof — ported from AssessmentSocialProof.swift. Shown on the
 * green gradient assessment background (white LocalContentColor provided by the
 * parent), so no background is set and the typed text composables inherit white.
 *
 * Layout: a live member count, two stat cards, a rating "laurel" card (4.8 ★),
 * and a horizontally scrolling testimonials rail (community posts + App Store
 * reviews). The parent wraps this in a weight(1f) box and renders the Next button
 * below it, so the content fills height and scrolls internally.
 */
@Composable
fun AssessmentSocialProof(name: String, modifier: Modifier = Modifier) {
    val data = backupSocialProofData
    // Pull the live member count from the DB (library.one_offs, like iOS), falling
    // back to the static count when the row is absent (e.g. the local stack has
    // none), and bump the counter on appear.
    var peopleCount by remember { mutableStateOf(data.peopleCount) }
    var activeSheet by remember { mutableStateOf<SocialProofSheet?>(null) }
    LaunchedEffect(Unit) {
        SupabaseController.getAssessmentSocialProofValue()
            ?.let(::parseLivePeopleCount)
            ?.let { peopleCount = it }
        SupabaseController.incrementAssessmentSocialProofCount()
    }
    Column(
        modifier
            .fillMaxSize()
            // iOS scrollShadowFix (+inner / -outer): widen the scroll clip past
            // the parent AssessmentInfoSlide's 25dp inset and re-pad inside it,
            // so the stat/review cards' soft shadows aren't cut at the sides.
            .scrollShadowBleed()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Dimens.headingTopPadding)
            .padding(horizontal = Dimens.scrollShadowFix),
    ) {
        PeopleCountSection(name = name, peopleCount = peopleCount)
        StatisticsSection(stats = data.stats)
        ReviewSection(review = data.review)
        TestimonialsSection(testimonials = data.testimonials, onOpenSheet = { activeSheet = it })
    }

    // Detail sheets (iOS `.sheet(item: $activeSheet)` → DetailViewWrapper).
    activeSheet?.let { sheet -> SocialProofDetailSheet(sheet) { activeSheet = null } }
}

/** iOS `DetailViewWrapper` — the tapped review / community post, full text. */
@Composable
private fun SocialProofDetailSheet(sheet: SocialProofSheet, onDismiss: () -> Unit) {
    Clear30Sheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (sheet) {
                is SocialProofSheet.PostDetail -> {
                    CommunityHeadingPill()
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    Heading3(sheet.title, color = Clear30Colors.text)
                    if (sheet.body.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.cardSpacing))
                        SmallText(sheet.body, color = Clear30Colors.text)
                    }
                }
                is SocialProofSheet.ReviewDetail -> {
                    StarsRow(rating = sheet.rating, starSize = 20.dp)
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    Heading3(sheet.title, color = Clear30Colors.text)
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    SmallText(sheet.body, color = Clear30Colors.text)
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
        }
    }
}

/** Lenient JSON for the social-proof blob (we only need `peopleCount`). */
private val socialProofJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Extracts `peopleCount` from the social-proof JSON value and formats it with
 *  thousands separators (the DB may store a raw number or a pre-formatted string). */
private fun parseLivePeopleCount(value: String): String? = runCatching {
    val raw = socialProofJson.parseToJsonElement(value).jsonObject["peopleCount"]
        ?.jsonPrimitive?.content
    if (raw.isNullOrBlank()) null
    else raw.filter { it.isDigit() }.toLongOrNull()?.let { "%,d".format(it) } ?: raw
}.getOrNull()

// MARK: - People count

@Composable
private fun PeopleCountSection(name: String, peopleCount: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardSpacing * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // iOS uses DefaultText; default color inherits white LocalContentColor.
        SmallText("$name,")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            LiveCircle(modifier = Modifier.size(25.dp))
            GiganticText(peopleCount)
        }
        SmallText(
            "people just like you took\ncontrol with Clear30",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * LiveCircle — a dim outer ring with a solid inner dot that pulses (easeInOut,
 * ~1.5s, repeating) so the member count reads as live, matching iOS.
 */
@Composable
private fun LiveCircle(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulse",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(25.dp)
                .alpha(0.25f)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = pulse }
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// MARK: - Statistics

@Composable
private fun StatisticsSection(stats: List<SocialProofStat>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        stats.forEach { stat ->
            SocialProofStatCard(stat = stat, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SocialProofStatCard(stat: SocialProofStat, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // iOS fills the percentage with the clear30 gradient.
            GradientHeading1(stat.percentage)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                Icon(
                    if (stat.isPositive) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = Clear30Colors.text,
                    modifier = Modifier.size(14.dp),
                )
                SmallText(stat.label, color = Clear30Colors.text, maxLines = 1)
            }
            MiniText(
                stat.subtitle,
                modifier = Modifier.alpha(0.5f),
                color = Clear30Colors.text,
            )
        }
    }
}

/** Heading1 (32sp SemiBold) filled with the clear30 gradient — matches iOS `.foregroundStyle(clear30Gradient)`. */
@Composable
private fun GradientHeading1(text: String) {
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(brush = Clear30Gradients.clear30, fontWeight = FontWeight.SemiBold)) {
            append(text)
        }
    }
    Text(
        annotated,
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 32.sp * 1.3f,
    )
}

// MARK: - Review

@Composable
private fun ReviewSection(review: SocialProofReview) {
    Clear30Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardSpacing * 2),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                // laurel.leading — not in the SF-symbol map; render a dim flourish.
                // TODO(port): map laurel.leading / laurel.trailing SF symbols.
                LaurelFlourish()
                Heading3(review.rating, color = Clear30Colors.text)
                // 4 full stars + 1 half star → the 4.8 visual rating, gold journal fill.
                repeat(4) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Clear30Colors.journal2,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Icon(
                    Icons.Rounded.StarHalf,
                    contentDescription = null,
                    tint = Clear30Colors.journal2,
                    modifier = Modifier.size(20.dp),
                )
                LaurelFlourish()
            }
            TinyText(
                review.reviewCount,
                modifier = Modifier.alpha(0.5f),
                color = Clear30Colors.text,
            )
        }
    }
}

/** Dim laurel-leaf stand-in until the SF symbol lands (laurel.leading / .trailing). */
@Composable
private fun LaurelFlourish() {
    // A simple vertical bar pair reads as the laurel "wreath" bracket without a glyph.
    Box(
        Modifier
            .size(width = 4.dp, height = 22.dp)
            .alpha(0.25f)
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.text),
    )
}

// MARK: - Testimonials

@Composable
private fun TestimonialsSection(
    testimonials: List<TestimonialType>,
    onOpenSheet: (SocialProofSheet) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SmallText(
            "Real people who felt just like you",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.cardSpacing),
            textAlign = TextAlign.Center,
        )
        // iOS pages these through FeedView (horizontal, peekAmount 0): full-width
        // snapping pages whose neighbors scale to 0.9 at 0.5 opacity, with page
        // dots below. HorizontalPager reproduces that.
        val pagerState = rememberPagerState(pageCount = { testimonials.size })
        var settledPage by remember { mutableStateOf(0) }
        LaunchedEffect(pagerState.currentPage) {
            // Light tick on page change (FeedView haptics), skipping first paint.
            if (pagerState.currentPage != settledPage) {
                settledPage = pagerState.currentPage
                Haptics.lightImpact()
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(300.dp),
        ) { page ->
            // iOS scrollTransition: identity page at 1.0/1.0, neighbors at 0.9
            // scale + disabledOpacity (0.5) — interpolate by the page offset.
            val offset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            TestimonialCard(
                testimonial = testimonials[page],
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 1f - 0.1f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.5f * offset
                    },
                onOpenSheet = onOpenSheet,
            )
        }
        if (testimonials.size > 1) {
            PageDots(
                count = testimonials.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Dimens.cardSpacing),
            )
        }
    }
}

/**
 * Page dots — the FeedView `pageDots` (BigUIPaging PageIndicator, prominent
 * style): small dots in a translucent capsule; the current dot reads darker.
 */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.25f))
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

@Composable
private fun TestimonialCard(
    testimonial: TestimonialType,
    modifier: Modifier = Modifier,
    onOpenSheet: (SocialProofSheet) -> Unit,
) {
    when (testimonial) {
        is TestimonialType.CommunityPost -> CommunityPostTestimonialCard(
            postId = testimonial.postId,
            fallbackTitle = testimonial.title,
            fallbackBody = testimonial.body,
            modifier = modifier,
            onOpenSheet = onOpenSheet,
        )

        is TestimonialType.Review -> ReviewTestimonialCard(
            title = testimonial.title,
            bodyText = testimonial.body,
            rating = testimonial.rating,
            modifier = modifier,
            onOpenSheet = onOpenSheet,
        )
    }
}

/** The "Clear30 Community" gradient pill (iOS `FeedCardHeading`). */
@Composable
private fun CommunityHeadingPill() {
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius / 2))
            .background(Clear30Gradients.community)
            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
    ) {
        TinyText("Clear30 Community", color = Color.White)
    }
}

/** The 5-star row shared by the review card (17dp) and its sheet (20dp). */
@Composable
private fun StarsRow(rating: Double, starSize: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // iOS: full stars for index < rating, gold journal fill. A 5.0 review
        // shows five filled stars.
        repeat(5) { index ->
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = if (index < rating.toInt()) Clear30Colors.journal2 else Clear30Colors.journal2.copy(alpha = 0.25f),
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * CommunityPostTestimonialCard — iOS `CommunityPostTestimonialCard`: fetches the
 * live post by id (`getCommunityPostById`) and opens the detail sheet on tap
 * (O5). The inlined title/body serve as the fallback while loading / when the
 * fetch fails (e.g. the local stack without the seeded posts), so the card is
 * never blank — a deliberate softening of iOS's empty loading state.
 */
@Composable
private fun CommunityPostTestimonialCard(
    postId: String,
    fallbackTitle: String,
    fallbackBody: String,
    modifier: Modifier = Modifier,
    onOpenSheet: (SocialProofSheet) -> Unit,
) {
    var title by remember(postId) { mutableStateOf(fallbackTitle) }
    var bodyText by remember(postId) { mutableStateOf(fallbackBody) }
    LaunchedEffect(postId) {
        SupabaseController.getCommunityPostById(postId).getOrNull()?.let { post ->
            title = post.title
            bodyText = post.body
        }
    }
    Clear30Card(
        modifier = modifier
            .height(300.dp)
            .pressScale { onOpenSheet(SocialProofSheet.PostDetail(title, bodyText)) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            CommunityHeadingPill()
            Spacer(Modifier.height(Dimens.cardSpacing))
            Heading3(title, color = Clear30Colors.text)
            Spacer(Modifier.height(Dimens.cardSpacing))
            SmallText(
                bodyText,
                modifier = Modifier.fillMaxWidth().alpha(0.5f),
                color = Clear30Colors.text,
                maxLines = 4,
            )
            Spacer(Modifier.weight(1f))
            TinyText(
                "Tap to see more",
                modifier = Modifier.alpha(0.5f),
                color = Clear30Colors.text,
            )
        }
    }
}

@Composable
private fun ReviewTestimonialCard(
    title: String,
    bodyText: String,
    rating: Double,
    modifier: Modifier = Modifier,
    onOpenSheet: (SocialProofSheet) -> Unit,
) {
    Clear30Card(
        modifier = modifier
            .height(300.dp)
            .pressScale { onOpenSheet(SocialProofSheet.ReviewDetail(title, bodyText, rating)) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(bottom = Dimens.cardSpacing / 2)) {
                StarsRow(rating = rating, starSize = 17.dp)
            }
            Heading3(title, color = Clear30Colors.text)
            Spacer(Modifier.height(Dimens.cardSpacing))
            SmallText(
                bodyText,
                modifier = Modifier.fillMaxWidth().alpha(0.5f),
                color = Clear30Colors.text,
                maxLines = 6,
            )
            Spacer(Modifier.weight(1f))
            TinyText(
                "Tap to see more",
                modifier = Modifier.alpha(0.5f),
                color = Clear30Colors.text,
            )
        }
    }
}
