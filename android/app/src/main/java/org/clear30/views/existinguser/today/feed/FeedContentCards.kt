package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.clear30.AppState
import org.clear30.data.Clear30Store
import org.clear30.data.DeepLinkRoute
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.RedditScraper
import org.clear30.data.RedditThread
import org.clear30.data.redditInlinePreview
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.ProgramMemberPerk
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.updateContentInfo
import org.clear30.views.components.Clear30Alert
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30FullScreenCover
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.PagerDots
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.cardStyle
import org.clear30.views.components.FeedNativeVideoPlayer
import org.clear30.views.components.softShadow
import org.clear30.views.components.Heading3
import org.clear30.views.components.Heading3Markdown
import org.clear30.views.components.HighlightedTextFormat
import org.clear30.views.components.InlineYouTubePlayer
import org.clear30.views.components.pressScale
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextHighlighted
import org.clear30.views.components.TinyText
import org.clear30.views.existinguser.support.schoolGradient
import org.clear30.views.theme.Haptics
import org.clear30.views.components.VideoThumbnail
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.youTubeId
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Feed content cards — each renders ONE container of a daily lesson as its own
 * swipe page in the Today pager (iOS `TodayFeedCardRouter`/`buildItems`). The
 * page IS the content (no tap-to-detail); interactive containers (reddit /
 * youtube / meditation / claire / journal) act in-app from the card itself.
 *
 * A long lesson takes the full page height (the pager page's constraint caps
 * the card naturally); overflowing text is clipped behind a fade with a
 * "Tap to read more" affordance that opens a full-screen reader — cards never
 * scroll internally (iOS parity: FeedView pages are the content).
 *
 * Each card takes an optional `glow` brush — the iOS feed-focus halo
 * (CardStyle glowGradient, TodayFeedViews.swift): the pager passes the card
 * type's content gradient while the card is the focused page.
 */

/**
 * Card whose content grows to the page height and clips (never inner-scrolls).
 * The disabled scroll state is a measurement trick: it lets the content lay out
 * at full height so `maxValue > 0` detects overflow, while the parent
 * constraint clips what's visible. On overflow a bottom fade + [tapHint]
 * appear; tapping opens [onTapOverride] if given (e.g. the Reddit viewer),
 * else a full-screen reader with the same [content].
 */
@Composable
internal fun ExpandingFeedCard(
    glow: Brush? = null,
    tapHint: String = "Tap to read more",
    onTapOverride: (() -> Unit)? = null,
    // When true, expanding opens the same bottom sheet the guides use (G25)
    // instead of a full-screen dialog.
    useBottomSheet: Boolean = false,
    content: @Composable () -> Unit,
) {
    var readerOpen by remember { mutableStateOf(false) }
    val clipState = rememberScrollState()
    val overflows by remember { derivedStateOf { clipState.maxValue > 0 } }
    val onTap: (() -> Unit)? = onTapOverride ?: if (overflows) ({ readerOpen = true }) else null
    // fillMaxSize: feed pages extend to the full page height like iOS (the
    // sections stretch with internal Spacers; short content top-aligns).
    Clear30Card(
        modifier = Modifier.fillMaxSize().then(
            if (onTap != null) Modifier.pressScale { onTap() } else Modifier,
        ),
        glowGradient = glow,
    ) {
        androidx.compose.foundation.layout.Box {
            Column(
                Modifier.verticalScroll(clipState, enabled = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) { content() }
            if (overflows) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(
                        Modifier.fillMaxWidth().height(Dimens.cardSpacing * 3).background(
                            Brush.verticalGradient(listOf(Color.Transparent, Clear30Colors.button)),
                        ),
                    )
                    Row(
                        Modifier.fillMaxWidth().background(Clear30Colors.button),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TinyText(tapHint, color = Clear30Colors.text.copy(alpha = 0.5f))
                        Spacer(Modifier.width(Dimens.cardSpacing / 4))
                        Icon(
                            sfSymbol("arrow.up.left.and.arrow.down.right"),
                            contentDescription = null,
                            tint = Clear30Colors.text.copy(alpha = 0.5f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
    }
    if (readerOpen) {
        if (useBottomSheet) {
            // Same bottom sheet the guides use (iOS `.sheet`).
            Clear30Sheet(onDismiss = { readerOpen = false }) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    content()
                    Spacer(Modifier.height(Dimens.cardSpacing * 2))
                }
            }
        } else {
            Clear30FullScreenCover(onDismiss = { readerOpen = false }, statusBarPadding = true) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing / 2),
                    ) {
                        Spacer(Modifier.weight(1f))
                        org.clear30.views.components.IconButton("xmark", onClick = { readerOpen = false })
                    }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.horizontalPadding)
                            .padding(bottom = Dimens.cardSpacing * 2),
                        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) { content() }
                }
            }
        }
    }
}

/**
 * The lesson's video — iOS `VideoFeedView`: the BARE video filling the page
 * (no card chrome, heading, or padding), corner-radius clipped with the card
 * shadow, and the draggable progress bar overlaid at the bottom
 * (`InlineNativeVideoView`). Plays on page focus, pauses when swiped away.
 */
@Composable
internal fun VideoFeedCard(msg: ProgramMessage, userInfo: UserInfo, focused: Boolean = false) {
    val url = msg.videoURL ?: return
    LaunchedEffect(focused) {
        if (focused) {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.URL to url))
        }
    }
    val frame = Modifier
        .fillMaxSize()
        .softShadow(Clear30Colors.shadow, Dimens.cornerRadius)
        .clip(RoundedCornerShape(Dimens.cornerRadius))
        .background(Color.Black)
    val ytId = youTubeId(url)
    if (ytId != null) {
        androidx.compose.foundation.layout.Box(frame, contentAlignment = Alignment.Center) {
            InlineYouTubePlayer(ytId, Modifier.fillMaxWidth(), autoplay = focused)
        }
    } else {
        org.clear30.views.components.FeedNativeVideoPlayer(url, frame, playTrigger = focused)
    }
}

/**
 * Instagram videos — iOS `VideosFeedView`: a horizontal PAGED carousel of bare
 * native videos (one per snapping page, page dots below). The "Instagram"
 * videos are plain hosted MP4 URLs (`video_url`), played inline with the same
 * native player as the lesson video; only the settled page plays while the
 * feed page is focused.
 */
@Composable
internal fun VideosFeedCard(videos: List<org.clear30.data.model.ProgramVideo>, focused: Boolean = false) {
    if (videos.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { videos.size })
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            org.clear30.views.components.FeedNativeVideoPlayer(
                videos[page].videoURL,
                Modifier
                    .fillMaxSize()
                    .softShadow(Clear30Colors.shadow, Dimens.cornerRadius)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Color.Black),
                playTrigger = focused && page == pagerState.settledPage,
            )
        }
        if (videos.size > 1) {
            PagerDots(
                count = videos.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Dimens.cardSpacing / 2),
            )
        }
    }
}

/** The main lesson text (topic + body). Marks the message visited on first view. */
@Composable
internal fun MessageContentCard(msg: ProgramMessage, program: Program, userInfo: UserInfo, glow: Brush? = null) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(msg) {
        if (!msg.visited) {
            msg.visited = true
            scope.launch {
                Clear30Store.save(program)
                SupabaseController.updateContentInfo(program.contentInfo)
            }
            Logger.logEvent(
                userInfo.loggingID,
                LogEventType.openedMessage,
                mapOf(LogEventExtraDataType.MESSAGE_TITLE to msg.title),
            )
        }
    }
    ExpandingFeedCard(glow = glow, useBottomSheet = true) {
        // iOS MessageFeedView badge (TodayFeedViews.swift:154-169): a school
        // pill for school messages, else the badge configured for the user's
        // own assessment answer (personalized), top-right of the heading.
        val school = userInfo.schoolData
        val badge: Pair<String, Brush>? = when {
            msg.isSchoolMessage && school != null -> school.short_name to school.schoolGradient()
            else -> {
                val qid = msg.questionID
                val qresp = msg.questionResponse
                if (qid != null && qresp != null) {
                    program.currentBreak?.getBadgeText(qid, qresp)?.let { it to Clear30Gradients.clear30 }
                } else {
                    null
                }
            }
        }
        FeedCardHeading(
            "Message", Clear30Gradients.clear30,
            trailing = badge?.let { (text, gradient) -> { FeedBadgePill(text, gradient) } },
        )
        TopicRow(msg)
        // iOS content-opacity hierarchy (TodayFeedViews.swift:125-129):
        // subtitle at 0.75, body at 0.5. The body renders inline markdown +
        // links (iOS SmallTextWithLinks) — 411/435 live bodies use **bold**,
        // which plain SmallText showed as raw asterisks.
        if (msg.subtitle.isNotBlank()) SmallText(msg.subtitle, color = Clear30Colors.text.copy(alpha = 0.75f))
        if (msg.message.isNotBlank()) {
            org.clear30.views.components.SmallTextMarkdownBlocks(msg.message, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

/**
 * Image gallery — iOS `CarouselFeedView`: a full-height card with the "Frames"
 * heading and a full-width PAGED image carousel (one image per snapping page,
 * aspect-fit, page dots below).
 */
@Composable
internal fun CarouselFeedCard(images: List<String>, glow: Brush? = null) {
    var zoomedImage by remember { mutableStateOf<String?>(null) }
    Clear30Card(modifier = Modifier.fillMaxSize(), glowGradient = glow) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(formats = listOf(HighlightedTextFormat("Frames", highlighted = true)))
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { images.size })
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // iOS CarouselImageView fits to width and rounds the image's
                        // OWN bounds (fillMaxWidth + wrap height), so the rounded
                        // corners land on the visible image, not the letterbox box.
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.cornerRadius))
                            // iOS CarouselImageView: tapping a loaded image opens the
                            // pinch-zoom overlay with a medium haptic.
                            .clickable {
                                Haptics.mediumImpact()
                                zoomedImage = images[page]
                            },
                    )
                }
            }
            if (images.size > 1) {
                PagerDots(
                    count = images.size,
                    current = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
    zoomedImage?.let { url -> ZoomableImageOverlay(url) { zoomedImage = null } }
}

/**
 * The lesson's guide pages — iOS `MessageGuidesFeedView`: a horizontal PAGED
 * carousel where every guide is its own full-height card ("Guide" heading +
 * title + body at 0.5, "Tap to see more" pinned at the bottom → a sheet with
 * the full text).
 */
@Composable
internal fun GuidesFeedCard(msg: ProgramMessage, glow: Brush? = null) {
    val guides = msg.programPageInfo
    if (guides.isEmpty()) return
    var openGuide by remember { mutableStateOf<org.clear30.data.model.ProgramPageInfo?>(null) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { guides.size })
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            // This inner pager clips at the feed page's own width, which cut the
            // guide cards' soft shadows at the sides — bleed the clip outward and
            // re-pad inside it (iOS scrollShadowFix pattern).
            modifier = Modifier.fillMaxWidth().weight(1f).scrollShadowBleed(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.scrollShadowFix,
            ),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            val guide = guides[page]
            Clear30Card(
                modifier = Modifier.fillMaxSize().pressScale { openGuide = guide },
                glowGradient = glow,
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SmallTextHighlighted(formats = listOf(HighlightedTextFormat("Guide", highlighted = true)))
                    Heading3(guide.title)
                    // Guides carry real markdown (headers/bullets/links) — render
                    // it instead of showing the raw syntax (iOS SmallTextMarkdown).
                    // The body owns ALL the leftover height so the hint stays at
                    // the card's foot: weighting the body AND a trailing Spacer
                    // split that space in two, floating the hint mid-card.
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        org.clear30.views.components.SmallTextMarkdownBlocks(
                            guide.body,
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                            maxLines = 12,
                        )
                    }
                    TinyText("Tap to see more", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }
        if (guides.size > 1) {
            PagerDots(
                count = guides.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Dimens.cardSpacing / 2),
            )
        }
    }

    // Full guide text (iOS `.sheet(item: $currentGuide)`).
    openGuide?.let { guide ->
        Clear30Sheet(onDismiss = { openGuide = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                Heading3(guide.title)
                org.clear30.views.components.SmallTextMarkdownBlocks(guide.body, color = Clear30Colors.text)
                Spacer(Modifier.height(Dimens.cardSpacing * 2))
            }
        }
    }
}


/**
 * FeedCardHeading (iOS TodayFeedViews.swift:22-58): the gradient section title
 * top-leading with an optional trailing accessory, cardSpacing/2 below.
 */
@Composable
internal fun FeedCardHeading(text: String, gradient: Brush, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.Top,
    ) {
        SmallTextHighlighted(
            formats = listOf(HighlightedTextFormat(text, highlighted = true)),
            highlightBrush = gradient,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** iOS `VisitedNode` — the checkbox-style visited marker on feed headings. */
@Composable
internal fun VisitedNode(visited: Boolean, gradient: Brush) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(23.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Clear30Colors.button)
            .border(3.dp, Clear30Colors.text.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (visited) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(gradient),
            )
        }
    }
}

/**
 * Meditation — iOS `MeditationFeedView`: a PLAIN card with the "Meditation"
 * gradient heading (+ visited node), and the inline MeditationPage player
 * centered in the stretched space (iOS wraps it in Spacers). The gradient
 * lives on the play disc / progress fill, not the card background.
 *
 * The player's scrubber stays hidden (faded out, but holding its space so the
 * card doesn't jump) until this meditation has actually started playing, and is
 * inset by `horizontalPadding` — see [org.clear30.views.existinguser.support.MeditationPageInline].
 */
@Composable
internal fun MeditationFeedCard(
    med: ProgramMeditation,
    program: Program,
    glow: Brush? = null,
    // The sleep/cravings hub hides the visited checkbox in the card corner.
    showVisited: Boolean = true,
) {
    Clear30Card(modifier = Modifier.fillMaxSize(), glowGradient = glow) {
        Column(Modifier.fillMaxSize()) {
            FeedCardHeading(
                "Meditation", Clear30Gradients.meditation,
                trailing = if (showVisited) {
                    { VisitedNode(med.visited, Clear30Gradients.meditation) }
                } else {
                    null
                },
            )
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                org.clear30.views.existinguser.support.MeditationPageInline(med, program)
            }
        }
    }
}

/**
 * A Reddit thread — previews the post inline (title + body preview + stats),
 * tapping opens the full in-app viewer. Ports iOS `RedditThreadFeedView` +
 * `RedditViewer(inline: true)`: a plain card with a "Reddit Thread" gradient
 * label, not a full gradient card. The thread JSON is fetched lazily on first
 * composition; until it arrives the seed `res.title` shows.
 */
@Composable
internal fun RedditFeedCard(res: ProgramResource, userInfo: UserInfo, glow: Brush? = null, onOpen: (String) -> Unit) {
    // The thread JSON comes from the reddit_proxy edge function, which serves
    // from the backend `library.reddit_threads` cache (like iOS); until (or
    // unless) it arrives the seed `res.title` shows on its own.
    var thread by remember(res.url) { mutableStateOf<RedditThread?>(null) }
    LaunchedEffect(res.url) { thread = RedditScraper.fetch(res.url) }
    Clear30Card(
        modifier = Modifier.fillMaxSize().pressScale {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedRedditThread, mapOf(LogEventExtraDataType.URL to res.url))
            onOpen(res.url)
        },
        glowGradient = glow,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(
                formats = listOf(HighlightedTextFormat("Reddit Thread", highlighted = true)),
                highlightBrush = Clear30Gradients.reddit,
            )
            val t = thread
            Heading3(t?.post?.title ?: res.title)
            val preview = t?.post?.body?.let { redditInlinePreview(it) }.orEmpty()
            // The preview owns ALL the leftover height so the stats row below is
            // pinned to the card's foot: weighting the preview AND a trailing
            // Spacer split that space in two, floating the row mid-card.
            Column(Modifier.fillMaxWidth().weight(1f)) {
                if (preview.isNotBlank()) {
                    SmallText(
                        preview,
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                        maxLines = 12,
                    )
                }
            }
            // Bottom row: stats (upvotes + comments) bottom-LEFT, "Tap to see more"
            // bottom-RIGHT — always, regardless of preview length.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                t?.post?.let { post ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        RedditStat(if (post.score >= 0) "chevron.up" else "chevron.down", kotlin.math.abs(post.score))
                        RedditStat("text.bubble.fill", post.numComments)
                    }
                }
                Spacer(Modifier.weight(1f))
                TinyText("Tap to see more", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/** Small upvote/comment stat used by the inline Reddit preview card. */
@Composable
private fun RedditStat(symbol: String, number: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(sfSymbol(symbol), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        TinyText("$number", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

/** A YouTube video — plays inline in the card via the embedded player. */
@Composable
internal fun YouTubeFeedCard(
    res: ProgramResource,
    userInfo: UserInfo,
    glow: Brush? = null,
    focused: Boolean = false,
    onOpenWeb: (String) -> Unit,
) {
    // iOS `YouTubeVideoFeedView`: a PLAIN card with the "YouTube Video" gradient
    // heading + share icon, the rounded player, and the video title below.
    val context = androidx.compose.ui.platform.LocalContext.current
    val id = youTubeId(res.url)
    Clear30Card(
        modifier = Modifier.fillMaxSize().then(
            // Non-parsable URLs (no video id) fall back to the in-app web viewer.
            if (id == null) {
                Modifier.pressScale {
                    Logger.logEvent(userInfo.loggingID, LogEventType.openedYouTubeVideo, mapOf(LogEventExtraDataType.URL to res.url))
                    onOpenWeb(res.url)
                }
            } else {
                Modifier
            },
        ),
        glowGradient = glow,
    ) {
        Column(Modifier.fillMaxSize()) {
            FeedCardHeading("YouTube Video", Clear30Gradients.youtube) {
                Icon(
                    sfSymbol("square.and.arrow.up"),
                    contentDescription = "Share",
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp).pressScale {
                        runCatching {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Check out this video I found through Clear30!\n${res.url}",
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                    },
                )
            }
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (id != null) {
                    // `autoplay` mirrors iOS YouTubeViewer's isFeedFocused handling:
                    // play when the page settles into focus, stop when it leaves.
                    InlineYouTubePlayer(id, Modifier.fillMaxWidth(), autoplay = focused) {
                        Logger.logEvent(userInfo.loggingID, LogEventType.openedYouTubeVideo, mapOf(LogEventExtraDataType.URL to res.url))
                    }
                } else {
                    VideoThumbnail(null, Modifier.fillMaxWidth())
                }
            }
            if (res.title.isNotBlank()) {
                Spacer(Modifier.height(Dimens.cardSpacing))
                Heading3(res.title)
            }
        }
    }
}

/**
 * A Claire conversation starter — iOS `ClairePromptFeedView`: a PLAIN card
 * with the "Claire Prompt" gradient heading, the title in a claire-outlined
 * capsule, a chat preview (the user's prompt as a gradient bubble on the
 * right, an animated-emoji Claire bubble on the left), and a full-width
 * gradient "Reveal ✨" CTA pinned at the bottom.
 */
@Composable
internal fun ClairePromptFeedCard(
    prompt: ProgramClairePrompt,
    userInfo: UserInfo,
    glow: Brush? = null,
    focused: Boolean = false,
    // When provided (Today feed), Claire opens in-place on the current tab (G27)
    // instead of switching to the Support tab.
    onOpenClaire: ((String) -> Unit)? = null,
) {
    fun openClaire() {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.EXTRA to prompt.title))
        if (onOpenClaire != null) {
            onOpenClaire(prompt.prompt)
        } else {
            // Fallback: Claire lives on the Support tab — stash the seed, then
            // switch tabs (a sub-route alone doesn't navigate).
            AppState.requestSubRoute(DeepLinkRoute.Claire(prompt.prompt))
            AppState.requestTab("SUPPORT")
        }
    }
    Clear30Card(modifier = Modifier.fillMaxSize(), glowGradient = glow) {
        Column(Modifier.fillMaxSize()) {
            FeedCardHeading("Claire Prompt", Clear30Gradients.claire)
            // Title in a claire-outlined capsule, centered (iOS CardStyle with
            // clear fill + outline 0.5).
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallText(
                    prompt.title,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.cardStyle(
                        color = Color.Transparent,
                        shadowColor = Color.Transparent,
                        outlineGradient = Clear30Gradients.claire,
                        outlineOpacity = 0.5f,
                    ),
                )
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
            // The user's prompt — gradient bubble, right-aligned.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                ClaireBubble(gradient = Clear30Gradients.claire) {
                    TinyText(prompt.effectiveDisplayPrompt, color = Color.White)
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            // Claire's reply — an animated emoji in a plain bubble, left-aligned.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                ClaireBubble(gradient = null) {
                    AnimatedEmoji(
                        emojis = prompt.effectiveEmojis.ifEmpty { listOf("😁", "🤔", "☝️", "🤓", "💡") },
                        animating = focused,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // "Reveal ✨" (iOS TextIconButton with the claire gradient).
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Clear30Gradients.claire)
                    .pressScale { openClaire() }
                    .padding(vertical = Dimens.cardSpacing * 0.75f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallText("Reveal", color = Color.White)
                Icon(sfSymbol("sparkles"), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** iOS `BubbleStyle` — a rounded chat bubble; gradient = the user side. */
@Composable
private fun ClaireBubble(gradient: Brush?, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .then(
                if (gradient != null) Modifier.background(gradient)
                else Modifier.background(Clear30Colors.opacityGray),
            )
            .padding(horizontal = Dimens.cardSpacing, vertical = (Dimens.cardSpacing.value * 0.8f).dp),
    ) { content() }
}

/**
 * iOS `AnimatedEmojiView` — the emoji pulses (1.0 → 1.15 → 1.0) and cycles to
 * the next one each loop while the page is focused.
 */
@Composable
private fun AnimatedEmoji(emojis: List<String>, animating: Boolean) {
    var index by remember(emojis) { mutableStateOf(0) }
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(animating, emojis) {
        if (!animating) {
            scale.animateTo(1f, androidx.compose.animation.core.tween(300))
            return@LaunchedEffect
        }
        while (true) {
            scale.animateTo(1.15f, androidx.compose.animation.core.tween(1500))
            kotlinx.coroutines.delay(500)
            scale.animateTo(1f, androidx.compose.animation.core.tween(1500))
            kotlinx.coroutines.delay(500)
            if (emojis.size > 1) index = (index + 1) % emojis.size
        }
    }
    Text(
        emojis.getOrElse(index) { "💡" },
        fontSize = 22.sp,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

/** A premium member perk — its CTA opens the link in-app or jumps to a section. */
@Composable
internal fun MemberPerkFeedCard(perk: ProgramMemberPerk, glow: Brush? = null, onOpenWeb: (String) -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxSize(), gradient = Clear30Gradients.clear30, glowGradient = glow) {
        // Full page height, content centered (iOS MemberPerkFeedView Spacers).
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterVertically),
        ) {
            perk.imageURL?.let { img ->
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(Dimens.cornerRadius)),
                )
            }
            perk.title?.let { SmallText(it, color = Color.White) }
            perk.subtitle?.let { TinyText(it, color = Color.White.copy(alpha = 0.75f)) }
            DefaultButton(perk.buttonText, gradient = Clear30Gradients.button) {
                perk.buttonLink?.let { onOpenWeb(it) }
                    ?: when (perk.sheetType) {
                        "groups", "group" -> AppState.requestTab("GROUPS")
                        "claire" -> { AppState.requestSubRoute(DeepLinkRoute.Claire()); AppState.requestTab("SUPPORT") }
                        "community" -> AppState.requestTab("COMMUNITY")
                        else -> {}
                    }
            }
        }
    }
}

/**
 * The lesson's journal prompts — a "New Journal" card that shows the prompt and a
 * clear gradient "Write entry" button, so it reads as an obvious tap target
 * (ports iOS `NewJournalFeedView`). Multiple prompts page with chevrons; the
 * button journals on whichever prompt is shown.
 */
@Composable
internal fun JournalPromptsFeedCard(prompts: List<String>, glow: Brush? = null, onJournal: (String) -> Unit) {
    var current by remember(prompts) { mutableStateOf(0) }
    val index = current.coerceIn(0, (prompts.size - 1).coerceAtLeast(0))
    Clear30Card(modifier = Modifier.fillMaxSize(), glowGradient = glow) {
        // Title block at the top, "Write entry" pinned at the bottom — the
        // stretch space between them keeps the tap target off the prompt text.
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            SmallTextHighlighted(
                formats = listOf(HighlightedTextFormat("New Journal", highlighted = true)),
                highlightBrush = Clear30Gradients.journals,
            )
            // Prompts can carry markdown (e.g. "**bold**") — render it.
            Heading3Markdown(prompts.getOrNull(index) ?: "Create a new journal.")
            Spacer(Modifier.weight(1f))
            if (prompts.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val canPrev = index > 0
                    val canNext = index < prompts.size - 1
                    Icon(
                        sfSymbol("chevron.left"), contentDescription = "Previous prompt",
                        tint = Clear30Colors.text.copy(alpha = if (canPrev) 0.5f else 0.25f),
                        modifier = Modifier.size(20.dp).clickable(enabled = canPrev) { current = index - 1 },
                    )
                    Spacer(Modifier.weight(1f))
                    TinyText("${index + 1} / ${prompts.size}", color = Clear30Colors.text.copy(alpha = 0.5f))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        sfSymbol("chevron.right"), contentDescription = "Next prompt",
                        tint = Clear30Colors.text.copy(alpha = if (canNext) 0.5f else 0.25f),
                        modifier = Modifier.size(20.dp).clickable(enabled = canNext) { current = index + 1 },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Clear30Gradients.journals)
                    .pressScale { onJournal(prompts.getOrNull(index).orEmpty()) }
                    .padding(vertical = Dimens.cardSpacing * 0.75f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(sfSymbol("square.and.pencil"), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                SmallText("Write entry", color = Color.White)
            }
        }
    }
}

/** Compose a journal entry seeded with a daily-topic prompt (shared by the feed). */
@Composable
internal fun JournalOnTopicDialog(prompt: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Clear30Alert(
        onDismissRequest = onDismiss,
        title = "Journal on this",
        message = prompt,
        confirmLabel = "Save",
        onConfirm = { onSave(text.trim()) },
        confirmEnabled = text.isNotBlank(),
        dismissLabel = "Cancel",
        content = {
            OutlinedTextField(
                text, { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Write your thoughts…") },
            )
        },
    )
}

/** Topic emoji + title row shared by the video and message cards. */
@Composable
private fun TopicRow(msg: ProgramMessage) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        msg.topicEmoji?.let { SmallText(it) }
        Heading3(msg.topicTitle)
    }
}

/** Small gradient pill for feed-card headings (iOS `Badge` — disabled TinyTextButton look). */
@Composable
internal fun FeedBadgePill(text: String, gradient: Brush) {
    TinyText(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(gradient)
            .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
        color = Color.White,
    )
}

/**
 * Fullscreen pinch-zoom viewer for carousel images (iOS `ZoomableImageOverlay`):
 * pinch scales 1×–5× and pans with the two-finger centroid; releasing springs
 * back to fit (zoom is deliberately not persistent, like iOS). Tap anywhere or
 * the ✕ dismisses. iOS dims with ultraThinMaterial; here the app background at
 * 0.75 alpha stands in.
 */
@Composable
internal fun ZoomableImageOverlay(imageUrl: String, onDismiss: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize()
                .background(Clear30Colors.background.copy(alpha = 0.75f))
                .clickable { onDismiss() },
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.horizontalPadding)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offset.value.x
                        translationY = offset.value.y
                    }
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                // iOS PinchGestureView: transform only while two
                                // fingers are down — a single finger neither zooms
                                // nor pans (and leaves taps to the dismiss handler).
                                if (event.changes.size > 1) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scope.launch {
                                        val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                                        scale.snapTo(newScale)
                                        // pointerInput sits after graphicsLayer, so pan
                                        // deltas arrive in the untransformed local space
                                        // — scale them or the image lags the fingers
                                        // while zoomed.
                                        offset.snapTo(offset.value + pan * newScale)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                event = awaitPointerEvent()
                            }
                            scope.launch { scale.animateTo(1f) }
                            scope.launch { offset.animateTo(Offset.Zero) }
                        }
                    },
            )
            org.clear30.views.components.IconButton(
                icon = "xmark",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.horizontalPadding),
            )
        }
    }
}

/**
 * An existing journal entry surfaced in the Today feed (iOS `TextJournalFeedView`):
 * "Journal Entry" heading, title, dim body, tap to open the full editor.
 */
@Composable
internal fun JournalEntryFeedCard(entry: JournalEntry, glow: Brush? = null, onOpen: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxSize().pressScale { onOpen() }, glowGradient = glow) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            FeedCardHeading("Journal Entry", Clear30Gradients.journals)
            Heading3(entry.title)
            // The body owns ALL the leftover height so "Tap to see more" is
            // pinned to the card's foot: weighting the body AND a trailing
            // Spacer split that space in two, floating the hint mid-card.
            Column(Modifier.fillMaxWidth().weight(1f)) {
                SmallText(
                    entry.content,
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    // Cap long entries so the fixed-height page keeps "Tap to see
                    // more" visible instead of the body clipping over it.
                    maxLines = 12,
                )
            }
            TinyText(
                "Tap to see more",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * One journal feed page — iOS `JournalFeedView` (TodayFeedViews.swift:1109-1150):
 * a horizontal sub-feed of the day's journal ENTRIES plus the "New Journal"
 * card. A journal that ANSWERS a prompt REPLACES that prompt, so callers pass
 * only the still-unanswered [prompts] alongside the [entries]; the "New
 * Journal" card leads (iOS inserts it at index 0 while prompts remain) and is
 * shown alone when nothing has been written yet.
 */
@Composable
internal fun JournalFeedCard(
    prompts: List<String>,
    entries: List<JournalEntry>,
    glow: Brush? = null,
    onJournal: (String) -> Unit = {},
    onOpen: (JournalEntry) -> Unit = {},
) {
    val showPrompts = prompts.isNotEmpty() || entries.isEmpty()
    val pageCount = entries.size + if (showPrompts) 1 else 0
    if (pageCount <= 1) {
        if (showPrompts) JournalPromptsFeedCard(prompts, glow = glow, onJournal = onJournal)
        else JournalEntryFeedCard(entries[0], glow = glow) { onOpen(entries[0]) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            // Same as the guides pager: the inner pager clips at the feed page's
            // width, cutting the cards' soft shadows — bleed + re-pad (iOS
            // scrollShadowFix pattern).
            modifier = Modifier.fillMaxWidth().weight(1f).scrollShadowBleed(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.scrollShadowFix,
            ),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            val entryIndex = if (showPrompts) page - 1 else page
            if (entryIndex < 0) {
                JournalPromptsFeedCard(prompts, glow = glow, onJournal = onJournal)
            } else {
                JournalEntryFeedCard(entries[entryIndex], glow = glow) { onOpen(entries[entryIndex]) }
            }
        }
        PagerDots(
            count = pageCount,
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Dimens.cardSpacing / 2),
        )
    }
}

/** Cache key for permanently hiding the catch-up card (iOS `UnreadMessagesFeedView.cacheKey`). */
internal const val HIDE_CATCH_UP_KEY = "hide_catch_up_card"

/**
 * "Catch Up 📈" (iOS `UnreadMessagesFeedView`) — today-only nudge listing up to
 * 3 missed days (feed never started, `progress == 0`) as compact topic rows;
 * dismissable forever. The trigger (today + active break + ≥3 missed days +
 * not dismissed) lives at the call site, like iOS `buildFeedItems`.
 */
@Composable
internal fun CatchUpFeedCard(
    groups: List<List<ProgramMessage>>,
    program: Program,
    glow: Brush? = null,
    onOpenDay: (List<ProgramMessage>) -> Unit,
    onAllMessages: () -> Unit,
    onDismissForever: () -> Unit,
) {
    var dismissed by remember { mutableStateOf(false) }
    Clear30Card(modifier = Modifier.fillMaxSize(), glowGradient = glow) {
        Column(Modifier.fillMaxSize()) {
            Heading3("Catch Up 📈")
            SmallText(
                "Key messages from the ${groups.size} day${if (groups.size == 1) "" else "s"} you missed.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = Dimens.cardSpacing),
            )
            Spacer(Modifier.weight(1f))
            if (groups.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallText("You're all caught up!")
                    TinyText(
                        "Dive in for today",
                        modifier = Modifier.padding(bottom = Dimens.cardSpacing * 2),
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                    Icon(
                        sfSymbol("arrow.down"),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(35.dp).feedGradientTint(Clear30Gradients.clear30),
                    )
                }
            } else {
                groups.take(3).forEach { group ->
                    CatchUpMessageRow(group, program, Modifier.padding(bottom = Dimens.cardSpacing)) { onOpenDay(group) }
                }
                val more = groups.size - 3
                if (more > 1) {
                    TinyText(
                        "+ $more more",
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onAllMessages() },
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TinyText(
                if (dismissed) "✓ Won't show again" else "Don't show again",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Dimens.cardSpacing)
                    .clickable(enabled = !dismissed) {
                        dismissed = true
                        onDismissForever()
                    },
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

/** Compact missed-day row: topic line (+ second topic when the day has more),
 *  and a Day-N pill (iOS `MessageCard` — ring always empty here, progress is 0). */
@Composable
private fun CatchUpMessageRow(
    group: List<ProgramMessage>,
    program: Program,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val msg = group.firstOrNull() ?: return
    val day = program.getBreak(msg.unlockOn)?.let { "Day ${it.getBreakDay(msg.unlockOn)}" } ?: ""
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.opacityGray)
            .pressScale { onClick() }
            .padding(Dimens.cardSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            SmallText("${msg.topicEmoji ?: "💬"} ${msg.topicTitle}")
            if (group.size > 1) {
                val second = group[1]
                TinyText(
                    "${second.topicEmoji ?: "💬"} ${second.topicTitle}",
                    modifier = Modifier.padding(top = Dimens.cardSpacing / 2),
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        }
        if (day.isNotEmpty()) {
            Row(
                Modifier
                    .padding(start = Dimens.cardSpacing / 2)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .border(2.dp, Clear30Colors.text.copy(alpha = 0.25f), RoundedCornerShape(Dimens.cornerRadius))
                    .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText(day)
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                Icon(
                    sfSymbol("arrow.right"),
                    contentDescription = null,
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** iOS `.foregroundStyle(gradient)` on an icon — stamps the gradient over the
 *  rendered pixels (SrcAtop), same pattern as ReviewsSlide. */
private fun Modifier.feedGradientTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
    }
