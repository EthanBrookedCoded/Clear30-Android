package org.clear30.views.existinguser.today

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
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.ElectricProgressBar
import org.clear30.views.components.IconButton
import org.clear30.views.components.RedditDialog
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.WebViewDialog
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * MessageDetail — the full-screen paged message viewer (port of iOS
 * `ProgramMessagesView`/`SingleMessageView`). One vertical-pager page per
 * content part, in the iOS order: topic card, video, message, carousel,
 * guide pages, meditation, each reddit, each youtube, each member perk, each
 * Claire prompt, journal prompts, then the feed-end celebration.
 *
 * The 70dp header swaps between the back button (page 0) and the topic +
 * progress bar (later pages, tap to return to the top), mirroring the iOS
 * `showProgressBar` header minus the favorite heart (removed per Thatcher,
 * W35 — deliberate iOS divergence). Paging writes the day's
 * `ContentInfo.progress` as `index / (count - 1)`.
 *
 * Marks the message visited on first open and fires `openedMessage`.
 */
/** Single-message convenience — wraps into a one-element group. */
@Composable
fun MessageDetail(
    program: Program,
    message: ProgramMessage,
    userInfo: UserInfo,
    journalEntries: JournalEntries? = null,
    onBack: () -> Unit,
) = MessageDetail(program, listOf(message), userInfo, journalEntries, onBack)

@Composable
fun MessageDetail(
    program: Program,
    // The day's message GROUP (iOS ProgramMessagesView takes [ProgramMessage]):
    // the first is the main message, any same-day sub-messages (usually the
    // assessment-response message) append their content after it.
    messages: List<ProgramMessage>,
    userInfo: UserInfo,
    // Needed to save "Journal on this" entries; null on the rare path that has no
    // journal in scope (the journal cards simply no-op there).
    journalEntries: JournalEntries? = null,
    onBack: () -> Unit,
) {
    val message = messages.firstOrNull() ?: return
    val scope = rememberCoroutineScope()
    // In-app overlays opened from the content pages.
    var webUrl by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    var creatingJournalSeed by remember { mutableStateOf<String?>(null) }
    var editingJournalEntry by remember { mutableStateOf<JournalEntry?>(null) }
    // Journal entries are mutated in place, so the journal page needs a revision
    // to re-derive its answered/unanswered split after a save (same pattern as
    // TodayTab's `refresh`).
    var journalRevision by remember { mutableStateOf(0) }

    LaunchedEffect(messages) {
        val unvisited = messages.filter { !it.visited }
        if (unvisited.isNotEmpty()) {
            unvisited.forEach { it.visited = true }
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

    // One page per content part, iterating every message in the group (iOS
    // `generateFeedItems`): topic from the first, then each message's content
    // in sequence, then one feed-end.
    val pages = remember(messages) {
        buildList {
            add(ViewerPage.Topic)
            messages.forEach { m ->
                if (!m.videoURL.isNullOrBlank()) add(ViewerPage.Video(m))
                add(ViewerPage.Body(m))
                m.carouselImages?.takeIf { it.isNotEmpty() }?.let { add(ViewerPage.Carousel(it)) }
                if (m.programPageInfo.isNotEmpty()) add(ViewerPage.Guides(m))
                m.meditation?.let { add(ViewerPage.Meditation(it)) }
                m.instagramVideos?.takeIf { it.isNotEmpty() }?.let { add(ViewerPage.InstagramVideos(it)) }
                m.reddits.forEach { add(ViewerPage.Reddit(it)) }
                m.youTubes.forEach { add(ViewerPage.YouTube(it)) }
                // Plain "Resources" (non-reddit / non-youtube cross-links) are no
                // longer surfaced — only reddit threads and YouTube videos (G34).
                m.memberPerks?.forEach { add(ViewerPage.Perk(it)) }
                m.clairePrompts.forEach { add(ViewerPage.Claire(it)) }
                m.journalPrompts?.takeIf { it.isNotEmpty() }?.let {
                    add(ViewerPage.Journal(it, PlainDate.from(m.unlockOn)))
                }
            }
            add(ViewerPage.FeedEnd)
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val contentDay = remember(messages) { PlainDate.from(message.unlockOn) }

    // Progress write-back as the user pages (iOS `updateFeedProgress`) —
    // in-memory only per page turn; persisted when the end page completes.
    LaunchedEffect(pagerState.settledPage) {
        val denom = (pages.size - 1).coerceAtLeast(1)
        program.contentInfo[contentDay]?.updateProgress(pagerState.settledPage.toDouble() / denom)
    }

    // iOS scrollShadowFix (T9b, same as TodayTab): the column keeps
    // horizontalPadding − scrollShadowFix and the header re-pads the remainder,
    // so the pager (a clipping scroll container) has scrollShadowFix of room
    // around its pages and the cards' soft shadows aren't cut at the sides.
    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = Dimens.horizontalPadding - Dimens.scrollShadowFix),
    ) {
        // ── Header: back + heart ↔ topic + progress (iOS :262-383) ──────────
        Box(
            Modifier.fillMaxWidth().height(70.dp).padding(horizontal = Dimens.scrollShadowFix),
            contentAlignment = Alignment.CenterStart,
        ) {
            val showProgressHeader = pagerState.currentPage > 0
            // Plain switch (no Crossfade) — same W21 reasoning as the tab host:
            // the fading alpha layer corrupted the cards' blur shadows.
            run {
                val progressHeader = showProgressHeader
                if (progressHeader) {
                    Column(
                        Modifier.fillMaxWidth().clickable { scope.launch { pagerState.animateScrollToPage(0) } },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Title centered above the progress bar (Thatcher).
                        SmallText(
                            listOfNotNull(message.topicEmoji, message.topicTitle).joinToString(" "),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            maxLines = 1,
                        )
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
                    }
                }
            }
        }

        // ── Pager — same plain treatment as the Today feed (W21: the deck
        // tilt/overlap transform bled adjacent cards together and its alpha
        // compositing corrupted the blur shadows; glow off per Thatcher).
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Dimens.scrollShadowFix,
                vertical = Dimens.cardSpacing * 2.5f,
            ),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            val focused = page == pagerState.settledPage
            val glow: androidx.compose.ui.graphics.Brush? = null
            Box(
                Modifier.fillMaxSize(),
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
                    is ViewerPage.Video -> VideoFeedCard(item.msg, userInfo, focused = focused)
                    is ViewerPage.Body -> MessageContentCard(item.msg, program, userInfo, glow = glow)
                    is ViewerPage.Carousel -> CarouselFeedCard(item.images, glow = glow)
                    is ViewerPage.Guides -> GuidesFeedCard(item.msg, glow = glow)
                    is ViewerPage.Meditation -> MeditationFeedCard(item.med, program, glow = glow?.let { Clear30Gradients.meditation })
                    is ViewerPage.InstagramVideos -> VideosFeedCard(item.videos, focused = focused)
                    is ViewerPage.Reddit -> RedditFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.reddit }, onOpen = { redditUrl = it })
                    is ViewerPage.YouTube -> YouTubeFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.youtube }, focused = focused, onOpenWeb = { webUrl = it })
                    is ViewerPage.Perk -> MemberPerkFeedCard(item.perk, glow = glow?.let { Clear30Gradients.supplements }, onOpenWeb = { webUrl = it })
                    is ViewerPage.Claire -> ClairePromptFeedCard(item.prompt, userInfo, glow = glow?.let { Clear30Gradients.claire }, focused = focused)
                    is ViewerPage.Journal -> {
                        // An entry whose title answers one of this lesson's prompts
                        // REPLACES that prompt here (iOS JournalFeedView,
                        // TodayFeedViews.swift:1133-1140) — only the still-unanswered
                        // prompts keep a "New Journal" card. `journalRevision` is read
                        // so a save from this very card re-derives the split.
                        val dayEntries = remember(item, journalRevision, journalEntries) {
                            journalEntries?.entries(item.date.dateObject)
                                ?.filter { it.isVideo != true }
                                .orEmpty()
                        }
                        JournalFeedCard(
                            prompts = item.prompts.filter { p ->
                                dayEntries.none { JournalEntries.answersPrompt(it.title, p) }
                            },
                            entries = dayEntries.filter { JournalEntries.answersAnyPrompt(it.title, item.prompts) },
                            glow = glow?.let { Clear30Gradients.journals },
                            onJournal = { creatingJournalSeed = it },
                            onOpen = { editingJournalEntry = it },
                        )
                    }
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
    // Journal prompt → full-screen editor (G28), seeded with the prompt as title.
    creatingJournalSeed?.let { seed ->
        org.clear30.views.existinguser.profile.TextEntryEditor(
            initialTitle = seed,
            initialContent = "",
            alreadyShared = false,
            onClose = { title, body ->
                if (title.isNotBlank() && body.isNotBlank()) {
                    journalEntries?.let { je ->
                        // Dated to the MESSAGE's day, not now (iOS
                        // `newJournalEntry(date:)` uses the journal item's date) —
                        // otherwise an answered prompt on a caught-up day never
                        // finds its entry and keeps showing the prompt card.
                        je.entries.add(JournalEntry(title = title, content = body, date = contentDay.dateObject))
                        scope.launch { Clear30Store.save(je) }
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.createdJournalEntry,
                            mapOf(LogEventExtraDataType.TYPE to "text"),
                        )
                    }
                }
                creatingJournalSeed = null
                journalRevision++
            },
        )
    }
    // Existing entry → the same editor, writing back in place (as TodayTab does).
    editingJournalEntry?.let { entry ->
        org.clear30.views.existinguser.profile.TextEntryEditor(
            initialTitle = entry.title,
            initialContent = entry.content,
            alreadyShared = entry.communityPostId != null,
            onClose = { title, body ->
                entry.title = title
                entry.content = body
                journalEntries?.let { je -> scope.launch { Clear30Store.save(je) } }
                editingJournalEntry = null
                journalRevision++
            },
        )
    }
}

/** One vertical-pager page of the viewer (iOS `ProgramMessageItem`). */
private sealed interface ViewerPage {
    data object Topic : ViewerPage
    data class Video(val msg: ProgramMessage) : ViewerPage
    data class Body(val msg: ProgramMessage) : ViewerPage
    data class Carousel(val images: List<String>) : ViewerPage
    data class Guides(val msg: ProgramMessage) : ViewerPage
    data class Meditation(val med: ProgramMeditation) : ViewerPage
    data class InstagramVideos(val videos: List<org.clear30.data.model.ProgramVideo>) : ViewerPage
    data class Reddit(val res: ProgramResource) : ViewerPage
    data class YouTube(val res: ProgramResource) : ViewerPage
    data class Perk(val perk: ProgramMemberPerk) : ViewerPage
    data class Claire(val prompt: ProgramClairePrompt) : ViewerPage
    /** iOS `.journal(prompts:date:)` — the date scopes which entries answer them. */
    data class Journal(val prompts: List<String>, val date: PlainDate) : ViewerPage
    data object FeedEnd : ViewerPage
}

