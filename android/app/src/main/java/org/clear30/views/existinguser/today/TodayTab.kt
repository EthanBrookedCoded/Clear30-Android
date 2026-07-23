package org.clear30.views.existinguser.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import coil.compose.AsyncImage
import org.clear30.data.CheckInLogger
import org.clear30.data.Clear30Store
import androidx.compose.ui.graphics.Brush
import org.clear30.data.model.CalendarViewMode
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Post
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.sorted
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.data.supabase.getCommunityFeedByDayName
import org.clear30.data.supabase.getCommunityPostsByTitles
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.now
import org.clear30.views.existinguser.community.communityProgramTagName
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.pressScale
import org.clear30.views.components.scrollStackItem
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.HighlightedTextFormat
import org.clear30.views.components.InlineVideoPlayer
import org.clear30.views.components.MiniText
import org.clear30.views.components.VideoThumbnail
import org.clear30.views.components.youTubeId
import org.clear30.views.components.ElectricProgressBar
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextHighlighted
import org.clear30.views.components.StandardCalendarNode
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * TodayTab — faithful port of Home.swift. The top section morphs between three
 * states, exactly like iOS:
 *
 *   • Week mode (default) — a 7-day week strip, no heading.
 *   • Week mode + scrolled — the strip collapses into a progress bar showing the
 *     topic of the day, your position in the feed, and a scroll-to-top chevron.
 *   • Month mode — a full month grid with a "Month Year" heading + Share, reached
 *     by the chevron toggle on the divider.
 *
 * The feed (CheckInDayCard + the day's content messages) scrolls below a fixed
 * top section; its scroll position drives the progress-bar morph. All three
 * transitions are animated (animated height + crossfade + chevron rotation),
 * matching `GlobalData.shared.defaultAnimation`.
 */
@Composable
fun TodayTab(
    program: Program,
    userInfo: UserInfo,
    journalEntries: org.clear30.data.model.JournalEntries,
    onOpenPostAssessment: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val logger = remember { CheckInLogger(program, userInfo, scope) }
    var refresh by remember { mutableIntStateOf(0) }
    // The day the check-in sheet is open for; null = closed (iOS
    // CheckInViewModel.showCheckInForDate). Forced flows (W69) open it for
    // yesterday / a past selected day, not just today.
    var checkInSheetFor by remember { mutableStateOf<PlainDate?>(null) }
    // True when the open sheet is a FORCED (iOS inline) check-in: unskippable,
    // no back-dismiss — the day must be logged (iOS replaces the feed with it).
    var checkInBlocking by remember { mutableStateOf(false) }
    var showMultiCheckIn by remember { mutableStateOf(false) }
    var showMidPilotAssessment by remember { mutableStateOf(false) }
    // In-app overlays opened from the inlined content cards (YouTube now plays
    // inline within its card).
    var webUrl by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    // Catch-up card → full-screen viewer for a missed day's first message; the
    // journal feed cards open the full-screen text editor (create / edit).
    var catchUpMessages by remember { mutableStateOf<List<ProgramMessage>?>(null) }
    var creatingJournalSeed by remember { mutableStateOf<String?>(null) }
    // Claire opened in-place from a today-feed prompt card (G27) — stays on TODAY.
    var claireOverlay by remember { mutableStateOf<String?>(null) }
    // A community post opened from the feed carousel — shown IN PLACE (iOS
    // `viewModel.activeSheet = .communityPostDetail`, TodayFeedCommunity.swift:73-75),
    // not by switching to the Community tab.
    var communityPostOverlay by remember { mutableStateOf<Post?>(null) }
    var editingJournalEntry by remember { mutableStateOf<org.clear30.data.model.JournalEntry?>(null) }
    // Drop feed state saved on a previous calendar day, so a process that
    // lives overnight doesn't silently restore yesterday's selected day/page.
    remember { TodayTabUiState.resetIfStale(PlainDate.from(now())) }
    // Seeded from the process-lifetime holder so returning to the tab doesn't
    // momentarily drop the Community page — which would shift the restored
    // pager index onto FeedEnd and falsely complete the day.
    var communityPosts by remember { mutableStateOf(TodayTabUiState.savedCommunityPosts) }
    var viewMode by remember { mutableStateOf<CalendarViewMode>(program.latestCalendarViewMode ?: CalendarViewMode.Weed) }

    val today = PlainDate.from(now())
    // Restore the last-viewed day across tab switches — the plain `when` tab
    // host (W21) disposes this composable when leaving the tab, so without the
    // holder the feed would snap back to today/page 0 every time.
    var selectedDay by remember { mutableStateOf(TodayTabUiState.savedDay ?: today) }
    var showWeekView by remember { mutableStateOf(true) }
    var monthAnchor by remember { mutableStateOf(firstOfMonth(today)) }

    // iOS CheckInViewModel.updateCheckIn(old:new:): remove `old`, append `new`,
    // re-log the whole day through CheckInLogger (recomputes last-smoked,
    // rewards, sync). Drives the day card's remove / smoked-again / amount /
    // detail edits.
    val onCheckInChange: (org.clear30.data.model.LoggedCheckIn?, org.clear30.data.model.LoggedCheckIn?) -> Unit =
        { old, new ->
            if (old != new) {
                val checkIns = program.dayInfo[selectedDay]?.loggedCheckIns
                if (checkIns != null) {
                    val updated = checkIns.filterNot { it == old } + listOfNotNull(new)
                    logger.logCheckIns(selectedDay.dateObject, updated)
                    refresh++
                }
            }
        }

    LaunchedEffect(Unit) {
        // Refresh message copy in place when the server content revision changed
        // (progress/unlockOn survive); if the timeline was never scheduled
        // (repair path), schedule it from the current break's start — so a
        // freshly-started break unlocks its lessons day-by-day from day 1 —
        // otherwise from the program start.
        // Anchor at the MAIN break's start, not the start-soon bridge's — the
        // repair path would otherwise schedule the Clear30 curriculum from the
        // Preparation break's first day.
        val contentStart = (program.currentBreakNotStartSoon ?: program.currentBreak)?.startDate ?: program.startDate
        if (org.clear30.data.ProgramMessageHandler.ensureContent(program, contentStart, userInfo.name)) refresh++
        // Fall back to the built-in daily lessons whenever the backend returned no
        // content (local dev / pre-assessment) so the Today feed is never empty.
        if (program.contentInfo.values.sumOf { it.messages.size } == 0) {
            val seeded = org.clear30.data.ProgramMessageHandler.applyBuiltInLessons(program, contentStart, userInfo.name)
            if (seeded > 0) refresh++
        }
        // School messages ride along un-notified only if they lack copy; include
        // them like iOS AllTabs:488 (schoolMessages carry no notification copy,
        // so scheduleContent skips them — included for parity all the same).
        val allMessages = program.contentInfo.values.flatMap { it.messages } + program.schoolMessages
        org.clear30.data.NotificationHandler.scheduleContent(userInfo, allMessages, program)
        org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.openedCalendar)
    }

    // W69 — forced check-in flows, the iOS CheckInViewModel.handleShowCheckIn
    // decision ladder (CheckInViewModel.swift:39-93), re-run per selected day
    // like iOS loadDayContext. In priority order:
    //   1. Today + 2+ unlogged days → multi check-in (multiCheckInThreshold = 2).
    //   2. Today + program day >= 1 + yesterday unlogged → force YESTERDAY's
    //      check-in.
    //   3. A non-today unlogged selected day → force that day's check-in.
    //   4. Today unlogged on the first load of the day → auto-present today's
    //      check-in (iOS forceShowForSelectedDay = firstLoad && sober == nil).
    // The today-scoped auto-presents (2 & 4) run once per calendar day per
    // process, via TodayTabUiState — Android disposes this composable on every
    // tab switch, so without the guard the sheet would re-open each return.
    // Runs the iOS ladder (CheckInViewModel.handleShowCheckIn). [manual] mirrors
    // iOS `forceShowForSelectedDay`: the day card's "Check in" button routes
    // through here too, so tapping it with a backlog opens the MULTI sheet and
    // with yesterday unlogged opens YESTERDAY — it never jumps straight to the
    // selected day (iOS TodayFeedView.swift:159-172).
    fun runCheckInLadder(manual: Boolean) {
        val isToday = selectedDay == today
        if (isToday && missedCheckInDays(program).size >= 2) {
            showMultiCheckIn = true
            return
        }
        val yesterday = today.adding(days = -1)
        if (isToday && program.getDay(now()) >= 1 && program.dayInfo[yesterday]?.sober == null) {
            // Auto-present once per day per process; a manual tap always opens it.
            if (manual || TodayTabUiState.forcedYesterdayShownOn != today) {
                TodayTabUiState.forcedYesterdayShownOn = today
                // iOS branch 2/3 is the INLINE check-in — unskippable (the feed is
                // replaced until the day is logged, TodayFeedView.swift:32-33).
                checkInBlocking = true
                checkInSheetFor = yesterday
            }
            return
        }
        if (!isToday && program.dayInfo[selectedDay]?.sober == null) {
            checkInBlocking = true
            checkInSheetFor = selectedDay
            return
        }
        if (manual || (isToday && program.dayInfo[today]?.sober == null &&
                TodayTabUiState.autoCheckInShownOn != today)
        ) {
            TodayTabUiState.autoCheckInShownOn = today
            // Branch 4 is iOS's fullScreen (isToday) variant — skippable.
            checkInBlocking = false
            checkInSheetFor = selectedDay
        }
    }

    LaunchedEffect(selectedDay) {
        // iOS gates the on-load ladder behind `showCheckInOnLoad = !firstLaunch`
        // (Home.swift:39-41) so a user who just finished onboarding lands on the
        // feed/tutorial, not a check-in sheet.
        if (userInfo.completedOnboarding == true) runCheckInLadder(manual = false)
    }

    // Mid-pilot assessment (F2 — iOS TodayFeedView.handlePopups:290-300): a
    // school user with 10+ checked-in days who hasn't completed it. On-load
    // only for v1 (iOS also re-checks after check-ins); deferred while another
    // sheet is up.
    LaunchedEffect(showMultiCheckIn, checkInSheetFor) {
        if (userInfo.schoolId != null &&
            userInfo.midPilotAssessmentCompleted != true &&
            program.numDaysCheckedIn >= 10 &&
            !showMultiCheckIn && checkInSheetFor == null && !showMidPilotAssessment
        ) {
            kotlinx.coroutines.delay(500)
            showMidPilotAssessment = true
        }
    }

    // Community posts for the in-feed carousel — iOS loadCommunityPosts
    // (TodayFeedViewModel.swift:350-403): journal-prompt-title search first,
    // then the "Day N" + program-tag fallback; TEXT posts with bodies only.
    LaunchedEffect(Unit) {
        val todayMessages = program.getProgramMessages(today).sorted
        val prompts = org.clear30.data.model.JournalEntries.getPrompts(todayMessages)
        val byPrompts = if (prompts.isNotEmpty()) {
            SupabaseController.getCommunityPostsByTitles(
                titles = prompts,
                minComments = 2,
                minDate = now().adding(days = -60).toString(),
                sortBy = "views",
                limitCount = 10,
            ).getOrNull().orEmpty().filter { !it.isVideo && it.body.isNotEmpty() }
        } else {
            emptyList()
        }
        val fetched = byPrompts.ifEmpty {
            val currentProgramDay = program.currentBreak?.currentBreakDay ?: program.coreProgramDay
            SupabaseController.getCommunityFeedByDayName(
                dayName = "Day $currentProgramDay",
                programName = program.communityProgramTagName,
                excludePinned = true,
                minComments = 1,
                minDate = now().adding(days = -30).toString(),
                sortBy = "views",
            ).getOrNull().orEmpty().filter { !it.isVideo && it.body.isNotEmpty() }
        }
        if (fetched.isNotEmpty() || communityPosts.isEmpty()) {
            communityPosts = fetched
            TodayTabUiState.savedCommunityPosts = fetched
        }
    }

    // The SELECTED day's feed content only (iOS TodayFeedViewModel:
    // `program.getProgramMessages(for: date).sorted`) — switching days on the
    // calendar swaps the whole feed. `refresh` re-derives after mutations.
    val messages = remember(selectedDay, refresh) {
        program.getProgramMessages(selectedDay).sorted
    }
    val hasCommunity = communityPosts.isNotEmpty()
    // Full component separation (iOS `buildItems`): every container in a lesson is
    // its own swipe page (and so part of the progress bar), in this exact order —
    // video, message, carousel, guides, meditation, each reddit, each youtube, each
    // claire prompt, each member perk, then the journal prompts.
    // selectedDay is a REQUIRED key: the builder reads it for the today-only
    // Community/CatchUp gates and the journal entries — two message-less days
    // produce equal (empty) `messages`, so without it the previous day's
    // today-only pages would leak onto the newly-selected day.
    val feedItems = remember(messages, hasCommunity, refresh, selectedDay) {
        buildList {
            add(FeedItem.CheckIn)
            // The day's text journals, split once: the ones ANSWERING a lesson
            // prompt belong on that prompt's own page (they replace it, below),
            // everything else is free-form and gets the top card.
            val dayEntries = journalEntries.entries(selectedDay.dateObject)
                .filter { it.isVideo != true }
            val dayPrompts = org.clear30.data.model.JournalEntries.getPrompts(messages)
            // Top journal card (iOS buildFeedItems inserts at index min(1, count) —
            // right after the day card, BEFORE the catch-up nudge): the day's
            // free-form entries (title not tied to a lesson prompt).
            if (messages.isNotEmpty()) {
                val freeEntries = dayEntries.filter {
                    !org.clear30.data.model.JournalEntries.answersAnyPrompt(it.title, dayPrompts)
                }
                if (freeEntries.isNotEmpty()) add(FeedItem.JournalEntriesPage(freeEntries))
            }
            // Catch-up nudge (iOS buildFeedItems:171-177): today only, active
            // break, not dismissed, ≥3 missed (never-started) days.
            val currentBreak = program.currentBreak
            if (selectedDay == today && currentBreak != null && !userInfo.getCachedBool(HIDE_CATCH_UP_KEY)) {
                val missedGroups = program.getNonStartedMessages(currentBreak)
                if (missedGroups.size >= 3) add(FeedItem.CatchUp(missedGroups))
            }
            messages.forEach { m ->
                if (!m.videoURL.isNullOrBlank()) add(FeedItem.Video(m))
                add(FeedItem.Message(m))
                m.carouselImages?.takeIf { it.isNotEmpty() }?.let { add(FeedItem.Carousel(it)) }
                if (m.programPageInfo.isNotEmpty()) add(FeedItem.Guides(m))
                m.meditation?.let { add(FeedItem.Meditation(it)) }
                m.instagramVideos?.takeIf { it.isNotEmpty() }?.let { add(FeedItem.InstagramVideos(it)) }
                m.reddits.forEach { add(FeedItem.Reddit(it)) }
                m.youTubes.forEach { add(FeedItem.YouTube(it)) }
                m.clairePrompts.forEach { add(FeedItem.Claire(it)) }
                m.memberPerks?.forEach { add(FeedItem.Perk(it)) }
                // Journal page: an entry whose title answers one of the lesson's
                // prompts REPLACES that prompt here (iOS JournalFeedView,
                // TodayFeedViews.swift:1133-1140) — only the prompts still
                // unanswered keep a "New Journal" card.
                m.journalPrompts?.takeIf { it.isNotEmpty() }?.let { msgPrompts ->
                    add(
                        FeedItem.Journal(
                            prompts = msgPrompts.filter { p ->
                                dayEntries.none { org.clear30.data.model.JournalEntries.answersPrompt(it.title, p) }
                            },
                            entries = dayEntries.filter {
                                org.clear30.data.model.JournalEntries.answersAnyPrompt(it.title, msgPrompts)
                            },
                        ),
                    )
                }
            }
            // iOS TodayFeedViewModel appends a feedEnd celebration after the day's
            // messages, with community inserted BEFORE it (insertCommunityPosts,
            // TodayFeedViewModel.swift:404-417); the community carousel is
            // TODAY-ONLY (W12 — iOS loadCommunityPosts guards `isToday`).
            if (hasCommunity && selectedDay == today) add(FeedItem.Community)
            if (messages.isNotEmpty()) add(FeedItem.FeedEnd)
        }
    }
    val totalFeedItems = feedItems.size
    val topicOfTheDay = messages.firstOrNull()?.let { "${it.topicEmoji ?: ""} ${it.topicTitle}".trim() }

    // Vertical card pager — each feed item is a full page you swipe through (iOS
    // FeedView, `.scrollTargetBehavior(.paging)`). Reset to the top on day change;
    // returning to the tab restores the saved page instead of resetting.
    val pagerState = rememberPagerState(
        initialPage = TodayTabUiState.savedPage.coerceIn(0, (totalFeedItems - 1).coerceAtLeast(0)),
        pageCount = { totalFeedItems },
    )
    LaunchedEffect(selectedDay) {
        if (TodayTabUiState.savedDay != selectedDay) {
            TodayTabUiState.savedDay = selectedDay
            TodayTabUiState.savedOn = today
            pagerState.scrollToPage(0)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            TodayTabUiState.savedPage = it
            TodayTabUiState.savedOn = today
        }
    }

    // Page index drives the top progress bar (iOS `updateFeedProgress`: index > 0).
    val currentIndex by remember { derivedStateOf { pagerState.currentPage } }
    val showProgressBar by remember { derivedStateOf { showWeekView && pagerState.currentPage > 0 } }

    // iOS scrollShadowFix pattern (T9b): the column keeps horizontalPadding −
    // scrollShadowFix and every non-pager child pads the remaining
    // scrollShadowFix itself, so the pager (a clipping scroll container) has
    // scrollShadowFix of breathing room around its pages and the cards' soft
    // shadows aren't cut at the pager bounds.
    val shadowPad = Dimens.scrollShadowFix
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding - shadowPad)) {
        @Suppress("UNUSED_EXPRESSION") refresh

        // Pending post-assessment card (iOS PopUps.swift:19-33) — tap opens the
        // flow. Gated on daysSinceAppOpen > 0 like iOS: first-day users never
        // see the popup slot.
        val postAssessmentText = program.postAssessmentCardText
        val daysSinceAppOpen = userInfo.firstAppOpen.daysTo(now())
        if (postAssessmentText != null && onOpenPostAssessment != null && daysSinceAppOpen > 0) {
            org.clear30.views.existinguser.postassessment.PostAssessmentPopupCard(
                text = postAssessmentText,
                modifier = Modifier.padding(horizontal = shadowPad).padding(bottom = Dimens.cardSpacing / 2),
                onClick = onOpenPostAssessment,
            )
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = shadowPad)) {
            TodayTopSection(
            program = program,
            today = today,
            selectedDay = selectedDay,
            monthAnchor = monthAnchor,
            showWeekView = showWeekView,
            showProgressBar = showProgressBar,
            revision = refresh,
            topicOfTheDay = topicOfTheDay,
            currentIndex = currentIndex,
            totalItems = totalFeedItems,
            viewMode = viewMode,
            onSelectViewMode = {
                viewMode = it
                program.latestCalendarViewMode = it
                scope.launch { Clear30Store.save(program) }
            },
            // iOS `loadDay(date:)`: tapping a calendar day swaps the whole feed
            // to that day (check-in card + its messages) — no detail sheet; the
            // page-0 CheckInDayCard already edits the selected day's log.
            onSelectDay = { date -> selectedDay = date },
            onMonthStep = { months -> monthAnchor = monthAnchor.adding(months = months) },
            onScrollToTop = { scope.launch { pagerState.animateScrollToPage(0) } },
            )
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = shadowPad)) {
            FeedDivider(showToggle = !showProgressBar, isDown = showWeekView) {
                Haptics.lightImpact()
                if (!showWeekView) {
                    // collapsing to week
                } else {
                    monthAnchor = firstOfMonth(selectedDay) // open month on the selected day's month
                }
                showWeekView = !showWeekView
            }
        }

        if (!showWeekView) {
            // Month mode: a single centered day-preview card (iOS `dayPreview`).
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = shadowPad),
                contentAlignment = Alignment.Center,
            ) {
                CheckInDayCard(
                    selectedDay = selectedDay,
                    program = program,
                    showStreak = selectedDay == today,
                    revision = refresh,
                    onCheckIn = { runCheckInLadder(manual = true) },
                    onCheckInChange = onCheckInChange,
                )
            }
        } else {
            // Week mode: vertical card pager — swipe through full content cards one
            // at a time, with a ScrollStack-style receding-deck transition (below).
            Box(Modifier.fillMaxWidth().weight(1f)) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Extra vertical padding so the previous/next card clearly peeks
                    // above & below the focused one — it should be obvious there's a
                    // deck to swipe through, not a single static card. Horizontal =
                    // the scrollShadowFix share this column no longer applies, so
                    // page content stays aligned while shadows get room (T9b).
                    contentPadding = PaddingValues(
                        horizontal = shadowPad,
                        vertical = Dimens.cardSpacing * 2.5f,
                    ),
                    pageSpacing = Dimens.cardSpacing,
                ) { page ->
                    // Subscribe each page to the mutation counter — `program` is
                    // mutated in place (same reference), so without this read the
                    // pager pages are strong-skipped and render stale check-in /
                    // progress state after refresh++.
                    @Suppress("UNUSED_EXPRESSION") refresh
                    // ScrollStack-style receding-deck transition (react-bits ScrollStack,
                    // ported in scrollStackItem): the centered card sits forward and
                    // crisp; the card you swipe away scales down, fades, and lingers
                    // behind the incoming one like a deck. A higher baseScale keeps the
                    // receding card reading as a full card peeking behind.
                    // W21: plain full-height pages — the deck tilt/overlap
                    // transform (scrollStackItem) made adjacent cards bleed into
                    // each other; iOS FeedView pages plainly too. Glow disabled
                    // per Thatcher ("basic and good") — its blurred stroke was
                    // part of the shadow corruption.
                    val glow: Brush? = null
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (val item = feedItems.getOrNull(page)) {
                            // First page (iOS TodayFeedCardRouter): check-in card at
                            // the top, the day's topic card stretched over the rest
                            // of the page height (iOS `stretch: true`).
                            FeedItem.CheckIn -> Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                            ) {
                                CheckInDayCard(
                                    selectedDay = selectedDay,
                                    program = program,
                                    showStreak = selectedDay == today,
                                    revision = refresh,
                                    onCheckIn = { runCheckInLadder(manual = true) },
                                    onCheckInChange = onCheckInChange,
                                )
                                messages.firstOrNull()?.let { first ->
                                    TopicProgressCard(
                                        emoji = first.topicEmoji,
                                        title = first.topicTitle,
                                        badge = program.getBadgeInfo(first.unlockOn),
                                        progress = program.contentInfo[selectedDay]?.progress?.toFloat(),
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        onContinue = {
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                        },
                                    )
                                }
                                // Message-less day (iOS TodayFeedCardRouter:218-227):
                                // a "New Journal" card fills the stretch space instead.
                                if (messages.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().weight(1f)) {
                                        JournalPromptsFeedCard(prompts = emptyList()) {
                                            creatingJournalSeed = ""
                                        }
                                    }
                                }
                            }
                            is FeedItem.CatchUp -> CatchUpFeedCard(
                                groups = item.groups,
                                program = program,
                                glow = glow,
                                onOpenDay = { group -> catchUpMessages = group.takeIf { it.isNotEmpty() } },
                                onAllMessages = {
                                    org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Messages)
                                    org.clear30.AppState.requestTab("SUPPORT")
                                },
                                onDismissForever = {
                                    userInfo.setCacheBool(HIDE_CATCH_UP_KEY, true)
                                    scope.launch { Clear30Store.save(userInfo) }
                                },
                            )
                            is FeedItem.JournalEntriesPage -> JournalFeedCard(
                                prompts = emptyList(),
                                entries = item.entries,
                                glow = glow?.let { Clear30Gradients.journals },
                                onOpen = { editingJournalEntry = it },
                            )
                            is FeedItem.Video -> VideoFeedCard(
                                item.msg, userInfo,
                                focused = page == pagerState.settledPage,
                            )
                            is FeedItem.Message -> MessageContentCard(item.msg, program, userInfo, glow = glow)
                            is FeedItem.Carousel -> CarouselFeedCard(item.images, glow = glow)
                            is FeedItem.Guides -> GuidesFeedCard(item.msg, glow = glow)
                            is FeedItem.Meditation -> MeditationFeedCard(item.med, program, glow = glow?.let { Clear30Gradients.meditation })
                            is FeedItem.InstagramVideos -> VideosFeedCard(item.videos, focused = page == pagerState.settledPage)
                            is FeedItem.Reddit -> RedditFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.reddit }, onOpen = { redditUrl = it })
                            is FeedItem.YouTube -> YouTubeFeedCard(
                                item.res, userInfo,
                                glow = glow?.let { Clear30Gradients.youtube },
                                // iOS autoplays inline YouTube when the page settles
                                // into focus and pauses when it leaves (T9c).
                                focused = page == pagerState.settledPage,
                                onOpenWeb = { webUrl = it },
                            )
                            is FeedItem.Claire -> ClairePromptFeedCard(
                                item.prompt, userInfo,
                                glow = glow?.let { Clear30Gradients.claire },
                                focused = page == pagerState.settledPage,
                                onOpenClaire = { claireOverlay = it },
                            )
                            is FeedItem.Perk -> MemberPerkFeedCard(item.perk, glow = glow?.let { Clear30Gradients.supplements }, onOpenWeb = { webUrl = it })
                            is FeedItem.Journal -> JournalFeedCard(
                                prompts = item.prompts,
                                entries = item.entries,
                                glow = glow?.let { Clear30Gradients.journals },
                                onJournal = { creatingJournalSeed = it },
                                onOpen = { editingJournalEntry = it },
                            )
                            FeedItem.FeedEnd -> FeedEndCelebration(
                                message = messages.firstOrNull(),
                                focused = page == pagerState.settledPage,
                                alreadyComplete = (program.contentInfo[selectedDay]?.progress ?: 0.0) >= 1.0,
                                showCta = true,
                                // Program-wide unread count (iOS TodayTabEndFeedView):
                                // non-started unlocked day-groups across the whole
                                // break, or the core timeline at end of program (H36).
                                unreadMessages = run {
                                    val br = program.currentBreak
                                    (if (br != null) program.getNonStartedMessages(br)
                                    else program.getNonStartedCoreMessages()).size
                                },
                                onAllMessages = {
                                    org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Messages)
                                    org.clear30.AppState.requestTab("SUPPORT")
                                },
                                onCommunity = { org.clear30.AppState.requestTab("COMMUNITY") },
                                onScrollToTop = { scope.launch { pagerState.animateScrollToPage(0) } },
                                onCompleted = {
                                    program.contentInfo[selectedDay]?.updateProgress(1.0)
                                    scope.launch { Clear30Store.save(program) }
                                },
                            )
                            FeedItem.Community -> CommunityCarouselCard(
                                posts = communityPosts,
                                onOpenPost = { post -> communityPostOverlay = post },
                            )
                            null -> {}
                        }
                    }
                }

                // Swipe-up affordance — a gently bobbing chevron + label on the first
                // card while more cards wait below, so it's obvious the deck swipes.
                SwipeUpHint(
                    visible = currentIndex == 0 && totalFeedItems > 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.cardSpacing),
                )
            }
        }
    }

    checkInSheetFor?.let { sheetDay ->
        CheckInSheet(
            logger = logger,
            userInfo = userInfo,
            program = program,
            selectedDay = sheetDay,
            // Forced (iOS inline) check-ins can't be skipped or back-dismissed —
            // the day must be logged. Only the branch-4 today auto-present and
            // manual taps are skippable, matching iOS.
            blocking = checkInBlocking,
            onDismiss = {
                checkInSheetFor = null
                checkInBlocking = false
                refresh++
            },
            // Only a COMPLETED check-in advances the feed to the first lesson —
            // skipping/closing leaves it where it is (W10; iOS
            // CheckInViewModel.swift:131-146).
            onCompleted = {
                scope.launch { if (totalFeedItems > 1) pagerState.animateScrollToPage(1) }
            },
        )
    }

    if (showMultiCheckIn) {
        val missed = remember { missedCheckInDays(program) }
        if (missed.isNotEmpty()) {
            MultiCheckInSheet(
                name = userInfo.name,
                dates = missed,
                logger = logger,
                // iOS presents this through PopupManager's bare overlay — no
                // scrim tap, no close control, no swipe. The only exit is the
                // slide-to-confirm (CheckInViewModel.swift:166-172).
                onDismiss = { showMultiCheckIn = false; refresh++ },
            )
        }
    }

    // Community post detail, in place over the feed. PostDetail is built as a
    // full screen (own back chevron, ime/navbar padding), so it goes in a cover
    // rather than a bottom sheet — same treatment as the Claire and message
    // overlays. Editing is rare from here and continues in the Community tab.
    communityPostOverlay?.let { post ->
        org.clear30.views.components.Clear30FullScreenCover(
            onDismiss = { communityPostOverlay = null },
            statusBarPadding = true,
        ) {
            org.clear30.views.existinguser.community.PostDetail(
                post = post,
                userInfo = userInfo,
                onBack = { communityPostOverlay = null },
                onEdit = { editing ->
                    communityPostOverlay = null
                    org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Post(editing.id))
                    org.clear30.AppState.requestTab("COMMUNITY")
                },
                onDeleted = { communityPostOverlay = null },
            )
        }
    }

    if (showMidPilotAssessment) {
        org.clear30.views.existinguser.midpilot.MidPilotAssessment(userInfo) {
            showMidPilotAssessment = false
        }
    }

    // In-app overlays opened from the inlined content cards.
    webUrl?.let { u -> org.clear30.views.components.WebViewDialog(u, onDismiss = { webUrl = null }) }
    redditUrl?.let { u -> org.clear30.views.components.RedditDialog(u, onDismiss = { redditUrl = null }) }
    // Claire prompt → open Claire in-place (G27), without leaving the Today tab.
    claireOverlay?.let { seed ->
        org.clear30.views.components.Clear30FullScreenCover(onDismiss = { claireOverlay = null }) {
            org.clear30.views.existinguser.support.ClaireChat(
                onBack = { claireOverlay = null },
                userInfo = userInfo,
                program = program,
                initialInput = seed,
            )
        }
    }
    // Catch-up card → full-screen viewer of the missed day's lesson group (iOS
    // navigates to programMessages for the whole group).
    catchUpMessages?.let { group ->
        org.clear30.views.components.Clear30FullScreenCover(onDismiss = { catchUpMessages = null }) {
            MessageDetail(
                program = program,
                messages = group,
                userInfo = userInfo,
                journalEntries = journalEntries,
                onBack = {
                    catchUpMessages = null
                    refresh++
                },
            )
        }
    }

    // Journal feed cards → the full-screen text editor (iOS newTextEntry /
    // journalEntry destinations). Commit rules mirror JournalSection: new
    // entries need both fields; edits write back in place.
    creatingJournalSeed?.let { seed ->
        org.clear30.views.existinguser.profile.TextEntryEditor(
            initialTitle = seed,
            initialContent = "",
            alreadyShared = false,
            onClose = { title, body ->
                if (title.isNotBlank() && body.isNotBlank()) {
                    journalEntries.entries.add(
                        org.clear30.data.model.JournalEntry(title = title, content = body, date = selectedDay.dateObject),
                    )
                    scope.launch { Clear30Store.save(journalEntries) }
                    org.clear30.data.Logger.logEvent(
                        userInfo.loggingID,
                        org.clear30.data.LogEventType.createdJournalEntry,
                        mapOf(org.clear30.data.LogEventExtraDataType.TYPE to "text"),
                    )
                }
                creatingJournalSeed = null
                refresh++
            },
        )
    }
    editingJournalEntry?.let { entry ->
        org.clear30.views.existinguser.profile.TextEntryEditor(
            initialTitle = entry.title,
            initialContent = entry.content,
            alreadyShared = entry.communityPostId != null,
            onClose = { title, body ->
                entry.title = title
                entry.content = body
                scope.launch { Clear30Store.save(journalEntries) }
                editingJournalEntry = null
                refresh++
            },
        )
    }
}

/**
 * SwipeUpHint — a bobbing up-chevron + "Swipe" label that fades in over the
 * first card while the deck has more pages, making the swipe gesture obvious.
 */
@Composable
private fun SwipeUpHint(visible: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val bob = androidx.compose.animation.core.rememberInfiniteTransition(label = "swipeBob")
        val dy by bob.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "swipeBobDy",
        )
        Column(
            modifier = Modifier.offset(y = dy.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                sfSymbol("chevron.up"),
                contentDescription = null,
                tint = Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
            TinyText("Swipe", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

/** The morphing top section: month grid / week strip / progress bar. */
@Composable
private fun TodayTopSection(
    program: Program,
    today: PlainDate,
    selectedDay: PlainDate,
    monthAnchor: PlainDate,
    showWeekView: Boolean,
    showProgressBar: Boolean,
    // `program` is mutated in place, so its reference never changes and strong
    // skipping would leave the day pills stale after a check-in edit. The
    // revision counter (TodayTab's `refresh`) changes with every mutation and
    // defeats the skip down through WeekStrip/MonthGrid/DayNode.
    revision: Int,
    topicOfTheDay: String?,
    currentIndex: Int,
    totalItems: Int,
    viewMode: CalendarViewMode,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onSelectDay: (PlainDate) -> Unit,
    onMonthStep: (Int) -> Unit,
    onScrollToTop: () -> Unit,
) {
    // animateContentSize gives the week↔month expand/collapse its smooth height
    // change. iOS `toggleCalendar` animates with defaultAnimation.speed(1.5)
    // (Home.swift:210), and defaultAnimation is `.default.speed(1.5)`
    // (Clear30App.swift:33) — `.default` being the smooth spring (response 0.55,
    // dampingRatio 1). Net response = 0.55 / (1.5 × 1.5) ≈ 0.244s; Compose maps
    // response → stiffness = (2π / response)² ≈ 660, no bounce.
    Column(
        Modifier.fillMaxWidth().animateContentSize(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 660f),
        ),
    ) {
        if (!showWeekView) {
            // ── Month mode ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(vertical = Dimens.headingTopPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The month label slides + fades in the same direction as the grid.
                AnimatedContent(
                    targetState = monthAnchor,
                    transitionSpec = { monthSlide(targetState > initialState) },
                    label = "monthLabel",
                ) { anchor -> Heading1(monthHeading(anchor, today)) }
                Spacer(Modifier.weight(1f))
                ChevronBtn("chevron.left", "Previous month") { onMonthStep(-1) }
                Spacer(Modifier.size(Dimens.cardSpacing / 2))
                ChevronBtn("chevron.right", "Next month") { onMonthStep(1) }
            }
            WeekdayRow()
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            // Month grid slides left/right as you page months (iOS TabView paging).
            AnimatedContent(
                targetState = monthAnchor,
                transitionSpec = { monthSlide(targetState > initialState) },
                label = "monthGrid",
            ) { anchor ->
                MonthGrid(program, anchor, today, selectedDay, viewMode, revision, onSelectDay)
            }
            CalendarViewModeSelector(program, viewMode, onSelectViewMode)
        } else {
            // ── Week mode: week strip ↔ progress bar ────────────────────
            Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.CenterStart) {
                Crossfade(targetState = showProgressBar, animationSpec = tween(160), label = "topMorph") { progress ->
                    if (progress) {
                        Column(
                            Modifier.fillMaxWidth().clickable(onClick = onScrollToTop),
                            verticalArrangement = Arrangement.Center,
                            // W11: topic title centered over the progress bar.
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (!topicOfTheDay.isNullOrBlank()) {
                                SmallText(topicOfTheDay, maxLines = 1)
                                Spacer(Modifier.size(Dimens.cardSpacing))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                                Box(Modifier.weight(1f)) {
                                    ElectricProgressBar(current = currentIndex, max = maxOf(totalItems - 1, 1))
                                }
                                UpDownButton(isDown = false, onClick = onScrollToTop)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            WeekdayRow()
                            Spacer(Modifier.size(Dimens.cardSpacing / 2))
                            WeekStrip(program, today, selectedDay, viewMode, revision, onSelectDay)
                        }
                    }
                }
            }
        }
    }
}

/** Sun–Sat labels. */
@Composable
private fun WeekdayRow() {
    Row(Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { d ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TinyText(d, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/** 7-day strip for the week containing [selectedDay] (Sunday-first). */
@Composable
private fun WeekStrip(program: Program, today: PlainDate, selectedDay: PlainDate, viewMode: CalendarViewMode, revision: Int, onSelect: (PlainDate) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        weekDates(selectedDay).forEach { date ->
            DayNode(program, date, today, selectedDay, viewMode, revision, Modifier.weight(1f), onSelect)
        }
    }
}

/** Full month grid for [anchor]'s month. */
@Composable
private fun MonthGrid(program: Program, anchor: PlainDate, today: PlainDate, selectedDay: PlainDate, viewMode: CalendarViewMode, revision: Int, onSelect: (PlainDate) -> Unit) {
    val first = firstOfMonth(anchor)
    val daysInMonth = first.daysTo(first.adding(months = 1))
    val leadingBlanks = LocalDate(first.year, first.month, 1).dayOfWeek.isoDayNumber % 7 // Sunday-first
    val rows = (leadingBlanks + daysInMonth + 6) / 7
    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - leadingBlanks + 1
                    if (dayNum in 1..daysInMonth) {
                        DayNode(program, PlainDate(first.year, first.month, dayNum), today, selectedDay, viewMode, revision, Modifier.weight(1f), onSelect)
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Pills to switch what the calendar colors by — weed (default) or any custom
 * check-in (iOS CalendarViewMode). Only shown when custom check-ins exist.
 */
@Composable
private fun CalendarViewModeSelector(program: Program, viewMode: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    if (program.customCheckIns.isEmpty()) return
    val modes: List<CalendarViewMode> =
        listOf(CalendarViewMode.Weed) + program.customCheckIns.map { CalendarViewMode.Custom(it) }
    Spacer(Modifier.size(Dimens.cardSpacing / 2))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        modes.forEach { mode ->
            val selected = mode == viewMode
            val selBrush = when (mode) {
                is CalendarViewMode.Custom -> mode.customCheckIn.gradient
                else -> Clear30Gradients.clear30
            }
            Box(
                Modifier.clip(RoundedCornerShape(99.dp))
                    .background(if (selected) selBrush else Clear30Gradients.gray)
                    .clickable { Haptics.lightImpact(); onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                TinyText(mode.label, color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * The day's completion for the active view mode (iOS dayInfo lookup per mode).
 *
 * `revision` is deliberately threaded in (though unused here): DayNode must
 * genuinely read its `revision` parameter — the Compose compiler drops unused
 * params from the skip comparison, which would leave the pills stale after a
 * check-in edit (program is mutated in place, so no other arg ever changes).
 */
@Suppress("UNUSED_PARAMETER")
private fun nodeCompletion(program: Program, date: PlainDate, mode: CalendarViewMode, revision: Int): Boolean? =
    when (mode) {
        is CalendarViewMode.Weed -> program.dayInfo[date]?.sober
        is CalendarViewMode.Custom ->
            program.dayInfo[date]?.loggedCheckIns?.firstOrNull { it.id == mode.customCheckIn.id }?.completion
        is CalendarViewMode.Split -> program.dayInfo[date]?.sober
    }

/**
 * One day cell — colored by the active check-in (weed or a custom check-in),
 * today bordered, selected outlined, with a thin stage-gradient marker bar
 * underneath when the day falls in a program stage (iOS stage markers).
 */
@Composable
private fun DayNode(program: Program, date: PlainDate, today: PlainDate, selectedDay: PlainDate, viewMode: CalendarViewMode, revision: Int, modifier: Modifier, onSelect: (PlainDate) -> Unit) {
    val isCustom = viewMode is CalendarViewMode.Custom
    val activeGradient = if (viewMode is CalendarViewMode.Custom) viewMode.customCheckIn.gradient else Clear30Gradients.clear30
    // iOS MultiCheckInDayNode.fillStyle: sober = gradient, smoked (checked in,
    // not sober) = GRAY (iOS has no red calendar state), no check-in = the
    // dimmer lowOpacity so gray smoked days stay distinguishable.
    val fill = when (nodeCompletion(program, date, viewMode, revision)) {
        true -> CalendarNodeFillStyle.Gradient(activeGradient)
        false -> if (isCustom) CalendarNodeFillStyle.GrayFlat else CalendarNodeFillStyle.Gray
        null -> CalendarNodeFillStyle.LowOpacity
    }
    val stageBrush = program.stageMap[date]?.gradient
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StandardCalendarNode(
                label = date.day,
                today = date == today,
                selected = date == selectedDay,
                enabled = date <= today,
                fillStyle = fill,
                selectedGradient = activeGradient,
                onTap = { onSelect(date) },
            )
            if (stageBrush != null && date <= today) {
                Box(
                    Modifier.width(10.dp).height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(stageBrush),
                )
            }
        }
    }
}

/**
 * UpDownButton — chevron.down rotated 180° when pointing up (iOS UpDownButton).
 * In the divider it toggles week/month; in the progress bar it scrolls to top.
 */
@Composable
private fun UpDownButton(isDown: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (isDown) 0f else 180f, Anim.default(), label = "chevron")
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Clear30Colors.opacityGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sfSymbol("chevron.down"),
            contentDescription = "Toggle calendar",
            tint = Clear30Colors.text,
            modifier = Modifier.size(10.dp).rotate(rotation),
        )
    }
}

/** Month nav chevron — the same gray-pill treatment as UpDownButton, with press-scale. */
@Composable
private fun ChevronBtn(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale(onClick = onClick)
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Clear30Colors.opacityGray),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), contentDescription = description, tint = Clear30Colors.text, modifier = Modifier.size(11.dp))
    }
}

/** Divider line + the week/month toggle chevron (hidden while the progress bar shows). */
@Composable
private fun FeedDivider(showToggle: Boolean, isDown: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Clear30Colors.opacityGrayFlattened.copy(alpha = 0.5f)))
        if (showToggle) UpDownButton(isDown = isDown, onClick = onToggle)
    }
}

/**
 * One swipeable page in the Today feed. A lesson is fully flattened into its
 * containers (iOS `buildItems`) — each becomes its own page and counts toward the
 * progress bar. Rendering for each lives in feed/FeedContentCards.kt.
 */
private sealed interface FeedItem {
    data object CheckIn : FeedItem
    data class CatchUp(val groups: List<List<ProgramMessage>>) : FeedItem
    data class JournalEntriesPage(val entries: List<org.clear30.data.model.JournalEntry>) : FeedItem
    data class Video(val msg: ProgramMessage) : FeedItem
    data class Message(val msg: ProgramMessage) : FeedItem
    data class Carousel(val images: List<String>) : FeedItem
    data class Guides(val msg: ProgramMessage) : FeedItem
    data class Meditation(val med: org.clear30.data.model.ProgramMeditation) : FeedItem
    data class InstagramVideos(val videos: List<org.clear30.data.model.ProgramVideo>) : FeedItem
    data class Reddit(val res: org.clear30.data.model.ProgramResource) : FeedItem
    data class YouTube(val res: org.clear30.data.model.ProgramResource) : FeedItem
    data class Claire(val prompt: org.clear30.data.model.ProgramClairePrompt) : FeedItem
    data class Perk(val perk: org.clear30.data.model.ProgramMemberPerk) : FeedItem
    /** Prompts still unanswered + the entries that answered the rest (iOS JournalFeedView). */
    data class Journal(
        val prompts: List<String>,
        val entries: List<org.clear30.data.model.JournalEntry>,
    ) : FeedItem
    data object FeedEnd : FeedItem
    data object Community : FeedItem
}

/**
 * Feed position that survives tab switches (the tab host disposes TodayTab's
 * composition when another tab shows). Process-lifetime only — a fresh launch
 * starts at today / page 0 as before, and state saved on a previous calendar
 * day is dropped on re-entry ([resetIfStale]).
 */
private object TodayTabUiState {
    var savedPage: Int = 0
    var savedDay: PlainDate? = null
    var savedOn: PlainDate? = null
    var savedCommunityPosts: List<Post> = emptyList()

    /**
     * The calendar day the forced/auto check-in sheet was last auto-presented
     * on (W69) — the once-per-day guard. Compared against today directly, so a
     * value from a previous day never suppresses today's auto-present.
     */
    var autoCheckInShownOn: PlainDate? = null

    /**
     * Separate guard for the FORCED-YESTERDAY branch. iOS keeps branches 2 and
     * 4 independent, so consuming one must not suppress the other (otherwise
     * completing yesterday's forced check-in swallows today's auto-present).
     */
    var forcedYesterdayShownOn: PlainDate? = null

    fun resetIfStale(today: PlainDate) {
        if (savedOn != null && savedOn != today) {
            savedPage = 0
            savedDay = null
            savedCommunityPosts = emptyList()
            savedOn = null
        }
    }
}

// ── Date helpers ─────────────────────────────────────────────────────────────

private fun firstOfMonth(d: PlainDate): PlainDate = PlainDate(d.year, d.month, 1)

/** The 7 PlainDates of [d]'s week, Sunday-first. */
private fun weekDates(d: PlainDate): List<PlainDate> {
    val ld = LocalDate(d.year, d.month, d.day)
    val sunday = ld.minus(DatePeriod(days = ld.dayOfWeek.isoDayNumber % 7))
    return (0..6).map { i ->
        val x = sunday.plus(DatePeriod(days = i))
        PlainDate(x.year, x.monthNumber, x.dayOfMonth)
    }
}

/** "June" in the current year, else "June 2025" (iOS `heading`). */
private fun monthHeading(anchor: PlainDate, today: PlainDate): String {
    val name = MONTH_NAMES[anchor.month - 1]
    return if (anchor.year == today.year) name else "$name ${anchor.year}"
}

/** Direction-aware month transition: the new month slides in while the old slides out. */
private fun monthSlide(forward: Boolean): androidx.compose.animation.ContentTransform =
    if (forward) {
        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
    } else {
        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
    }

