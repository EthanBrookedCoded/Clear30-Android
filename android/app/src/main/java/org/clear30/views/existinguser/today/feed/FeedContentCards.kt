package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.ProgramMemberPerk
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.updateContentInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.HighlightedTextFormat
import org.clear30.views.components.InlineVideoPlayer
import org.clear30.views.components.InlineYouTubePlayer
import org.clear30.views.components.pressScale
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextHighlighted
import org.clear30.views.components.TinyText
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
    content: @Composable () -> Unit,
) {
    var readerOpen by remember { mutableStateOf(false) }
    val clipState = rememberScrollState()
    val overflows by remember { derivedStateOf { clipState.maxValue > 0 } }
    val onTap: (() -> Unit)? = onTapOverride ?: if (overflows) ({ readerOpen = true }) else null
    Clear30Card(
        modifier = Modifier.fillMaxWidth().then(
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
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { readerOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = Clear30Colors.background,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
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

/** The lesson's video — plays in-app (direct file inline, YouTube embedded in place). */
@Composable
internal fun VideoFeedCard(msg: ProgramMessage, userInfo: UserInfo) {
    val url = msg.videoURL ?: return
    Clear30Card(modifier = Modifier.fillMaxWidth(), shadowColor = Clear30Colors.green.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(formats = listOf(HighlightedTextFormat("Video", highlighted = true)))
            val ytId = youTubeId(url)
            if (ytId != null) {
                InlineYouTubePlayer(ytId, Modifier.fillMaxWidth()) {
                    Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.URL to url))
                }
            } else {
                InlineVideoPlayer(url, msg.thumbnailURL, Modifier.fillMaxWidth())
            }
            TopicRow(msg)
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
    ExpandingFeedCard(glow = glow) {
        SmallTextHighlighted(formats = listOf(HighlightedTextFormat("Message", highlighted = true)))
        TopicRow(msg)
        // iOS content-opacity hierarchy (TodayFeedViews.swift:125-129):
        // subtitle at 0.75, body at 0.5.
        if (msg.subtitle.isNotBlank()) SmallText(msg.subtitle, color = Clear30Colors.text.copy(alpha = 0.75f))
        if (msg.message.isNotBlank()) SmallText(msg.message, color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

/** Horizontally scrollable image gallery (iOS CarouselFeedView). */
@Composable
internal fun CarouselFeedCard(images: List<String>, glow: Brush? = null) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), glowGradient = glow) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(formats = listOf(HighlightedTextFormat("Gallery", highlighted = true)))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                images.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.height(220.dp).width(300.dp).clip(RoundedCornerShape(Dimens.cornerRadius)),
                    )
                }
            }
        }
    }
}

/** The lesson's guide pages (intro / how-to / why-it-matters), stacked; overflow expands. */
@Composable
internal fun GuidesFeedCard(msg: ProgramMessage, glow: Brush? = null) {
    ExpandingFeedCard(glow = glow) {
        msg.programPageInfo.forEach { page ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Heading3(page.title)
                SmallText(page.body)
            }
        }
    }
}

/** Meditation — the inline MeditationPage player on a gradient card (iOS
 *  `MeditationFeedView` = `MeditationPage(inlineType:)`). */
@Composable
internal fun MeditationFeedCard(med: ProgramMeditation, program: Program, glow: Brush? = null) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        gradient = Clear30Gradients.meditation,
        glowGradient = glow,
    ) {
        org.clear30.views.existinguser.support.MeditationPageInline(med, program)
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
    var thread by remember(res.url) { mutableStateOf<RedditThread?>(null) }
    LaunchedEffect(res.url) { thread = RedditScraper.fetch(res.url) }
    ExpandingFeedCard(
        glow = glow,
        tapHint = "Tap to see more",
        onTapOverride = {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedRedditThread, mapOf(LogEventExtraDataType.URL to res.url))
            onOpen(res.url)
        },
    ) {
        SmallTextHighlighted(
            formats = listOf(HighlightedTextFormat("Reddit Thread", highlighted = true)),
            highlightBrush = Clear30Gradients.reddit,
        )
        val t = thread
        Heading3(t?.post?.title ?: res.title)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            t?.post?.let { post ->
                RedditStat(if (post.score >= 0) "chevron.up" else "chevron.down", kotlin.math.abs(post.score))
                RedditStat("text.bubble.fill", post.numComments)
            }
            Spacer(Modifier.weight(1f))
            TinyText("Tap to see more", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        val preview = t?.post?.body?.let { redditInlinePreview(it) }.orEmpty()
        if (preview.isNotBlank()) {
            SmallText(preview, color = Clear30Colors.text.copy(alpha = 0.5f))
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
    onOpenWeb: (String) -> Unit,
) {
    val id = youTubeId(res.url)
    Clear30Card(
        modifier = Modifier.fillMaxWidth().then(
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
        gradient = Clear30Gradients.youtube,
        glowGradient = glow,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            if (id != null) {
                InlineYouTubePlayer(id, Modifier.fillMaxWidth()) {
                    Logger.logEvent(userInfo.loggingID, LogEventType.openedYouTubeVideo, mapOf(LogEventExtraDataType.URL to res.url))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Icon(sfSymbol("play.fill"), contentDescription = null, tint = Color.White)
                SmallText(res.title, color = Color.White)
            }
        }
    }
}

/** A Claire conversation starter — opens Claire. */
@Composable
internal fun ClairePromptFeedCard(prompt: ProgramClairePrompt, userInfo: UserInfo, glow: Brush? = null) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth().pressScale {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.EXTRA to prompt.title))
            // Claire lives on the Support tab — stash the seed, then switch tabs
            // (a sub-route alone doesn't navigate; the destination tab consumes it).
            AppState.requestSubRoute(DeepLinkRoute.Claire(prompt.prompt))
            AppState.requestTab("SUPPORT")
        },
        gradient = Clear30Gradients.claire,
        glowGradient = glow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(sfSymbol("bubble.left.fill"), contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f)) {
                SmallText(prompt.title, color = Color.White)
                (prompt.displayPrompt ?: prompt.prompt).takeIf { it.isNotBlank() }?.let {
                    TinyText(it, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** A premium member perk — its CTA opens the link in-app or jumps to a section. */
@Composable
internal fun MemberPerkFeedCard(perk: ProgramMemberPerk, glow: Brush? = null, onOpenWeb: (String) -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30, glowGradient = glow) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
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
    Clear30Card(modifier = Modifier.fillMaxWidth(), glowGradient = glow) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallTextHighlighted(
                formats = listOf(HighlightedTextFormat("New Journal", highlighted = true)),
                highlightBrush = Clear30Gradients.journals,
            )
            Heading3(prompts.getOrNull(index) ?: "Create a new journal.")
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.cornerRadius))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Journal on this") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                SmallText(prompt, color = Clear30Colors.text.copy(alpha = 0.75f))
                OutlinedTextField(
                    text, { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write your thoughts…") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
