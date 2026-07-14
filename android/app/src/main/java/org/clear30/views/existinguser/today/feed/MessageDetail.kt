package org.clear30.views.existinguser.today

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.ProgramMemberPerk
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.updateContentInfo
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.ElectricProgressBar
import org.clear30.views.components.IconButton
import org.clear30.views.components.RedditDialog
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.WebViewDialog
import org.clear30.views.components.scrollStackItem
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * MessageDetail — the full-screen paged message viewer (port of iOS
 * `ProgramMessagesView`/`SingleMessageView`). One vertical-pager page per
 * content part, in the iOS order: topic card, video, message, carousel,
 * guide pages, meditation, each reddit, each youtube, each member perk, each
 * Claire prompt, journal prompts, then the feed-end celebration.
 *
 * The 70dp header swaps between back-button + favorite heart (page 0) and the
 * topic + progress bar (later pages, tap to return to the top), mirroring the
 * iOS `showProgressBar` header. Paging writes the day's `ContentInfo.progress`
 * as `index / (count - 1)`.
 *
 * Marks the message visited on first open and fires `openedMessage`.
 */
@Composable
fun MessageDetail(
    program: Program,
    message: ProgramMessage,
    userInfo: UserInfo,
    // Needed to save "Journal on this" entries; null on the rare path that has no
    // journal in scope (the journal cards simply no-op there).
    journalEntries: JournalEntries? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // In-app overlays opened from the content pages.
    var webUrl by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    var journalPrompt by remember { mutableStateOf<String?>(null) }
    // Rev bumps re-read `favorited` after the heart toggles (it's a plain var).
    var favoriteRev by remember { mutableStateOf(0) }

    LaunchedEffect(message) {
        if (!message.visited) {
            message.visited = true
            scope.launch {
                // Persist locally first, then mirror content state (visited /
                // favorites) up to Supabase (content_info) so it follows the
                // user across devices. Best-effort — the helper try-catches.
                Clear30Store.save(program)
                SupabaseController.updateContentInfo(program.contentInfo)
            }
        }
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedMessage,
            mapOf(LogEventExtraDataType.MESSAGE_TITLE to message.title),
        )
    }

    // One page per content part (iOS `generateFeedItems`).
    val pages = remember(message) {
        buildList {
            add(ViewerPage.Topic)
            if (!message.videoURL.isNullOrBlank()) add(ViewerPage.Video)
            add(ViewerPage.Body)
            message.carouselImages?.takeIf { it.isNotEmpty() }?.let { add(ViewerPage.Carousel(it)) }
            if (message.programPageInfo.isNotEmpty()) add(ViewerPage.Guides)
            message.meditation?.let { add(ViewerPage.Meditation(it)) }
            message.reddits.forEach { add(ViewerPage.Reddit(it)) }
            message.youTubes.forEach { add(ViewerPage.YouTube(it)) }
            if (message.hasOnlyResources) add(ViewerPage.Resources(message.onlyResources))
            message.memberPerks?.forEach { add(ViewerPage.Perk(it)) }
            message.clairePrompts.forEach { add(ViewerPage.Claire(it)) }
            message.journalPrompts?.takeIf { it.isNotEmpty() }?.let { add(ViewerPage.Journal(it)) }
            add(ViewerPage.FeedEnd)
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val contentDay = remember(message) { PlainDate.from(message.unlockOn) }

    // Progress write-back as the user pages (iOS `updateFeedProgress`) —
    // in-memory only per page turn; persisted when the end page completes.
    LaunchedEffect(pagerState.settledPage) {
        val denom = (pages.size - 1).coerceAtLeast(1)
        program.contentInfo[contentDay]?.updateProgress(pagerState.settledPage.toDouble() / denom)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        // ── Header: back + heart ↔ topic + progress (iOS :262-383) ──────────
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.CenterStart) {
            val showProgressHeader = pagerState.currentPage > 0
            Crossfade(targetState = showProgressHeader, animationSpec = tween(160), label = "viewerHeader") { progressHeader ->
                if (progressHeader) {
                    Column(
                        Modifier.fillMaxWidth().clickable { scope.launch { pagerState.animateScrollToPage(0) } },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SmallText(listOfNotNull(message.topicEmoji, message.topicTitle).joinToString(" "), maxLines = 1)
                        Spacer(Modifier.size(Dimens.cardSpacing))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                            Box(Modifier.weight(1f)) {
                                ElectricProgressBar(current = pagerState.currentPage, max = (pages.size - 1).coerceAtLeast(1))
                            }
                            Icon(
                                sfSymbol("chevron.up"),
                                contentDescription = "Back to top",
                                tint = Clear30Colors.text,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Clear30Colors.opacityGray)
                                    .clickable { scope.launch { pagerState.animateScrollToPage(0) } }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                                    .size(10.dp),
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton("chevron.backward", onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        @Suppress("UNUSED_EXPRESSION") favoriteRev
                        FavoriteHeart(favorited = message.favorited) {
                            message.favorited = !message.favorited
                            favoriteRev++
                            if (message.favorited) Haptics.successLight() else Haptics.successHeavy()
                            scope.launch {
                                Clear30Store.save(program)
                                SupabaseController.updateContentInfo(program.contentInfo)
                            }
                        }
                    }
                }
            }
        }

        // ── Pager (same receding-deck treatment as the Today feed) ─────────
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Dimens.cardSpacing * 2.5f),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val focused = page == pagerState.settledPage
            val glow = if (focused) Clear30Gradients.clear30 else null
            Box(
                Modifier.fillMaxSize().scrollStackItem(pageOffset, baseScale = 0.92f),
                contentAlignment = Alignment.Center,
            ) {
                when (val item = pages.getOrNull(page)) {
                    ViewerPage.Topic -> TopicProgressCard(
                        emoji = message.topicEmoji,
                        title = message.topicTitle,
                        badge = program.getBadgeInfo(message.unlockOn).let { (subtitle, title) -> subtitle to title },
                        progress = program.contentInfo[contentDay]?.progress?.toFloat(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    ViewerPage.Video -> VideoFeedCard(message, userInfo, focused = focused)
                    ViewerPage.Body -> MessageContentCard(message, program, userInfo, glow = glow)
                    is ViewerPage.Carousel -> CarouselFeedCard(item.images, glow = glow)
                    ViewerPage.Guides -> GuidesFeedCard(message, glow = glow)
                    is ViewerPage.Meditation -> MeditationFeedCard(item.med, program, glow = glow?.let { Clear30Gradients.meditation })
                    is ViewerPage.Reddit -> RedditFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.reddit }, onOpen = { redditUrl = it })
                    is ViewerPage.YouTube -> YouTubeFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.youtube }, focused = focused, onOpenWeb = { webUrl = it })
                    is ViewerPage.Resources -> ResourcesCard(item.resources, userInfo, onOpen = { webUrl = it })
                    is ViewerPage.Perk -> MemberPerkFeedCard(item.perk, glow = glow?.let { Clear30Gradients.supplements }, onOpenWeb = { webUrl = it })
                    is ViewerPage.Claire -> ClairePromptFeedCard(item.prompt, userInfo, glow = glow?.let { Clear30Gradients.claire })
                    is ViewerPage.Journal -> JournalPromptsFeedCard(item.prompts, glow = glow?.let { Clear30Gradients.journals }, onJournal = { journalPrompt = it })
                    ViewerPage.FeedEnd -> FeedEndCelebration(
                        message = message,
                        focused = focused,
                        alreadyComplete = (program.contentInfo[contentDay]?.progress ?: 0.0) >= 1.0,
                        showCta = false,
                        onScrollToTop = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onCompleted = {
                            program.contentInfo[contentDay]?.updateProgress(1.0)
                            scope.launch { Clear30Store.save(program) }
                        },
                    )
                    null -> {}
                }
            }
        }
    }

    // In-app overlays (kept outside the pager so they cover the screen).
    webUrl?.let { u -> WebViewDialog(u, onDismiss = { webUrl = null }) }
    redditUrl?.let { u -> RedditDialog(u, onDismiss = { redditUrl = null }) }
    journalPrompt?.let { prompt ->
        JournalOnTopicDialog(
            prompt = prompt,
            onDismiss = { journalPrompt = null },
            onSave = { text ->
                journalEntries?.let { je ->
                    je.entries.add(JournalEntry(title = prompt, content = text, date = now()))
                    scope.launch { Clear30Store.save(je) }
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.createdJournalEntry,
                        mapOf(LogEventExtraDataType.TYPE to "text"),
                    )
                }
                journalPrompt = null
            },
        )
    }
}

/** One vertical-pager page of the viewer (iOS `ProgramMessageItem`). */
private sealed interface ViewerPage {
    data object Topic : ViewerPage
    data object Video : ViewerPage
    data object Body : ViewerPage
    data class Carousel(val images: List<String>) : ViewerPage
    data object Guides : ViewerPage
    data class Meditation(val med: ProgramMeditation) : ViewerPage
    data class Reddit(val res: ProgramResource) : ViewerPage
    data class YouTube(val res: ProgramResource) : ViewerPage
    data class Resources(val resources: List<ProgramResource>) : ViewerPage
    data class Perk(val perk: ProgramMemberPerk) : ViewerPage
    data class Claire(val prompt: ProgramClairePrompt) : ViewerPage
    data class Journal(val prompts: List<String>) : ViewerPage
    data object FeedEnd : ViewerPage
}

/**
 * The favorite heart (iOS `ProgramMessagesView` header :319-348): a gradient
 * disc when favorited, a dim gray heart when not.
 */
@Composable
private fun FavoriteHeart(favorited: Boolean, onToggle: () -> Unit) {
    if (favorited) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Clear30Gradients.clear30).pressScale { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(sfSymbol("heart.fill"), contentDescription = "Unfavorite", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    } else {
        IconButton("heart.fill", tint = Clear30Colors.text.copy(alpha = 0.25f), onClick = onToggle)
    }
}

/** Non reddit/youtube cross-links, one page (each opens the in-app web viewer). */
@Composable
private fun ResourcesCard(resources: List<ProgramResource>, userInfo: UserInfo, onOpen: (String) -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallText("Resources", color = Clear30Colors.text.copy(alpha = 0.5f))
            resources.forEach { res ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.URL to res.url))
                        onOpen(res.url)
                    }.padding(vertical = Dimens.cardSpacing / 3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    Icon(sfSymbol("arrow.up.right"), contentDescription = null, tint = Clear30Colors.text)
                    Column(Modifier.weight(1f)) {
                        SmallText(res.title)
                        TinyText(res.url, color = Clear30Colors.text.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
