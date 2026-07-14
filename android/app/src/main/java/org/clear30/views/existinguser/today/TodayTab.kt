package org.clear30.views.existinguser.today

import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import org.clear30.data.model.CalendarViewMode
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Post
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.unlocked
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.util.now
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.cardStyle
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
    var showCheckInSheet by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<PlainDate?>(null) }
    var showMultiCheckIn by remember { mutableStateOf(false) }
    var showMidPilotAssessment by remember { mutableStateOf(false) }
    // In-app overlays opened from the inlined content cards (YouTube now plays
    // inline within its card).
    var webUrl by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    var journalPrompt by remember { mutableStateOf<String?>(null) }
    var communityPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var viewMode by remember { mutableStateOf<CalendarViewMode>(program.latestCalendarViewMode ?: CalendarViewMode.Weed) }

    val today = PlainDate.from(now())
    var selectedDay by remember { mutableStateOf(today) }
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
        val contentStart = program.currentBreak?.startDate ?: program.startDate
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
        org.clear30.data.NotificationHandler.scheduleContent(userInfo, allMessages)
        org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.openedCalendar)
    }

    // Auto-prompt the catch-up flow when 2+ recent days are unlogged (iOS multi-check-in).
    LaunchedEffect(Unit) {
        if (missedCheckInDays(program).size >= 2) showMultiCheckIn = true
    }

    // Mid-pilot assessment (F2 — iOS TodayFeedView.handlePopups:290-300): a
    // school user with 10+ checked-in days who hasn't completed it. On-load
    // only for v1 (iOS also re-checks after check-ins); deferred while another
    // sheet is up.
    LaunchedEffect(showMultiCheckIn, showCheckInSheet) {
        if (userInfo.schoolId != null &&
            userInfo.midPilotAssessmentCompleted != true &&
            program.numDaysCheckedIn >= 10 &&
            !showMultiCheckIn && !showCheckInSheet && !showMidPilotAssessment
        ) {
            kotlinx.coroutines.delay(500)
            showMidPilotAssessment = true
        }
    }

    // Recent community posts for the in-feed carousel (iOS TodayFeedCommunity).
    LaunchedEffect(Unit) {
        communityPosts = SupabaseController.getCommunityFeed(start = 0, end = 5, sortBy = "recent").getOrNull().orEmpty()
    }

    // Day's feed content. `refresh`/`selectedDay` re-derive it. Newest day
    // first, but READING order (unlockOn ascending) within a day — a plain
    // `.reversed()` would also flip within-day order, putting a day's
    // assessment-response messages before its core message.
    val messages = remember(selectedDay, refresh) {
        // School messages merge into the same feed (iOS ProgramContent.swift:96):
        // within a day, core content first, school content appended after.
        (program.contentInfo.values.flatMap { it.messages } + program.schoolMessages).unlocked
            .sortedWith(
                compareByDescending<org.clear30.data.model.ProgramMessage> { PlainDate.from(it.unlockOn) }
                    .thenBy { it.isSchoolMessage }
                    .thenBy { it.unlockOn },
            )
    }
    val hasCommunity = communityPosts.isNotEmpty()
    // Full component separation (iOS `buildItems`): every container in a lesson is
    // its own swipe page (and so part of the progress bar), in this exact order —
    // video, message, carousel, guides, meditation, each reddit, each youtube, each
    // claire prompt, each member perk, then the journal prompts.
    val feedItems = remember(messages, hasCommunity) {
        buildList {
            add(FeedItem.CheckIn)
            messages.forEach { m ->
                if (!m.videoURL.isNullOrBlank()) add(FeedItem.Video(m))
                add(FeedItem.Message(m))
                m.carouselImages?.takeIf { it.isNotEmpty() }?.let { add(FeedItem.Carousel(it)) }
                if (m.programPageInfo.isNotEmpty()) add(FeedItem.Guides(m))
                m.meditation?.let { add(FeedItem.Meditation(it)) }
                m.reddits.forEach { add(FeedItem.Reddit(it)) }
                m.youTubes.forEach { add(FeedItem.YouTube(it)) }
                m.clairePrompts.forEach { add(FeedItem.Claire(it)) }
                m.memberPerks?.forEach { add(FeedItem.Perk(it)) }
                m.journalPrompts?.takeIf { it.isNotEmpty() }?.let { add(FeedItem.Journal(it)) }
            }
            // iOS TodayFeedViewModel appends a feedEnd celebration after the day's
            // messages; the community carousel follows as bonus browsing.
            if (messages.isNotEmpty()) add(FeedItem.FeedEnd)
            if (hasCommunity) add(FeedItem.Community)
        }
    }
    val totalFeedItems = feedItems.size
    val topicOfTheDay = messages.firstOrNull()?.let { "${it.topicEmoji ?: ""} ${it.topicTitle}".trim() }

    // Vertical card pager — each feed item is a full page you swipe through (iOS
    // FeedView, `.scrollTargetBehavior(.paging)`). Reset to the top on day change.
    val pagerState = rememberPagerState(pageCount = { totalFeedItems })
    LaunchedEffect(selectedDay) { pagerState.scrollToPage(0) }

    // Page index drives the top progress bar (iOS `updateFeedProgress`: index > 0).
    val currentIndex by remember { derivedStateOf { pagerState.currentPage } }
    val showProgressBar by remember { derivedStateOf { showWeekView && pagerState.currentPage > 0 } }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        @Suppress("UNUSED_EXPRESSION") refresh

        // Pending post-assessment card (iOS PopUps.swift:31-33) — tap opens the flow.
        val postAssessmentText = program.postAssessmentCardText
        if (postAssessmentText != null && onOpenPostAssessment != null) {
            org.clear30.views.existinguser.postassessment.PostAssessmentPopupCard(
                text = postAssessmentText,
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
                onClick = onOpenPostAssessment,
            )
        }

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
            onSelectDay = { date ->
                selectedDay = date
                if (date != today) dayDetail = date
            },
            onMonthStep = { months -> monthAnchor = monthAnchor.adding(months = months) },
            onScrollToTop = { scope.launch { pagerState.animateScrollToPage(0) } },
            onShare = { shareProgress(it, program) },
        )

        FeedDivider(showToggle = !showProgressBar, isDown = showWeekView) {
            Haptics.lightImpact()
            if (!showWeekView) {
                // collapsing to week
            } else {
                monthAnchor = firstOfMonth(selectedDay) // open month on the selected day's month
            }
            showWeekView = !showWeekView
        }

        if (!showWeekView) {
            // Month mode: a single centered day-preview card (iOS `dayPreview`).
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CheckInDayCard(
                    selectedDay = selectedDay,
                    program = program,
                    showStreak = selectedDay == today,
                    revision = refresh,
                    onCheckIn = { showCheckInSheet = true },
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
                    // deck to swipe through, not a single static card.
                    contentPadding = PaddingValues(vertical = Dimens.cardSpacing * 2.5f),
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
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    // iOS `isFeedFocused`: while a card is the settled feed page it gets
                    // the CardStyle glow halo in its content gradient (TodayFeedViews.swift
                    // passes glowGradient per card type; the video card has no glow).
                    val glow = if (page == pagerState.settledPage) Clear30Gradients.clear30 else null
                    Box(
                        Modifier.fillMaxSize().scrollStackItem(pageOffset, baseScale = 0.92f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (val item = feedItems.getOrNull(page)) {
                            FeedItem.CheckIn -> CheckInDayCard(
                                selectedDay = selectedDay,
                                program = program,
                                showStreak = selectedDay == today,
                                revision = refresh,
                                onCheckIn = { showCheckInSheet = true },
                                onCheckInChange = onCheckInChange,
                            )
                            is FeedItem.Video -> VideoFeedCard(item.msg, userInfo)
                            is FeedItem.Message -> MessageContentCard(item.msg, program, userInfo, glow = glow)
                            is FeedItem.Carousel -> CarouselFeedCard(item.images, glow = glow)
                            is FeedItem.Guides -> GuidesFeedCard(item.msg, glow = glow)
                            is FeedItem.Meditation -> MeditationFeedCard(item.med, program, glow = glow?.let { Clear30Gradients.meditation })
                            is FeedItem.Reddit -> RedditFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.reddit }, onOpen = { redditUrl = it })
                            is FeedItem.YouTube -> YouTubeFeedCard(item.res, userInfo, glow = glow?.let { Clear30Gradients.youtube }, onOpenWeb = { webUrl = it })
                            is FeedItem.Claire -> ClairePromptFeedCard(item.prompt, userInfo, glow = glow?.let { Clear30Gradients.claire })
                            is FeedItem.Perk -> MemberPerkFeedCard(item.perk, glow = glow?.let { Clear30Gradients.supplements }, onOpenWeb = { webUrl = it })
                            is FeedItem.Journal -> JournalPromptsFeedCard(item.prompts, glow = glow?.let { Clear30Gradients.journals }, onJournal = { journalPrompt = it })
                            FeedItem.FeedEnd -> FeedEndCelebration(
                                message = messages.firstOrNull(),
                                focused = page == pagerState.settledPage,
                                alreadyComplete = (program.contentInfo[selectedDay]?.progress ?: 0.0) >= 1.0,
                                showCta = true,
                                unreadMessages = messages.count { !it.visited },
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
                                onOpenCommunity = { org.clear30.AppState.requestTab("COMMUNITY") },
                                onOpenPost = { post ->
                                    org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Post(post.id))
                                    org.clear30.AppState.requestTab("COMMUNITY")
                                },
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

    if (showCheckInSheet) {
        CheckInSheet(
            logger = logger,
            userInfo = userInfo,
            program = program,
            onDismiss = {
                showCheckInSheet = false
                refresh++
                // After checking in, drop the feed onto the first lesson (page 1 —
                // page 0 is the check-in card) so today's content is front-and-center.
                scope.launch { if (totalFeedItems > 1) pagerState.animateScrollToPage(1) }
            },
        )
    }

    dayDetail?.let { d ->
        DayDetailSheet(
            date = d,
            program = program,
            userInfo = userInfo,
            logger = logger,
            onDismiss = { dayDetail = null; refresh++ },
            onCheckInToday = { dayDetail = null; showCheckInSheet = true },
        )
    }

    if (showMultiCheckIn) {
        val missed = remember { missedCheckInDays(program) }
        if (missed.isNotEmpty()) {
            MultiCheckInSheet(
                name = userInfo.name,
                dates = missed,
                logger = logger,
                onDismiss = { showMultiCheckIn = false; refresh++ },
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
    journalPrompt?.let { prompt ->
        JournalOnTopicDialog(
            prompt = prompt,
            onDismiss = { journalPrompt = null },
            onSave = { text ->
                journalEntries.entries.add(
                    org.clear30.data.model.JournalEntry(title = prompt, content = text, date = now()),
                )
                scope.launch { Clear30Store.save(journalEntries) }
                org.clear30.data.Logger.logEvent(
                    userInfo.loggingID,
                    org.clear30.data.LogEventType.createdJournalEntry,
                    mapOf(org.clear30.data.LogEventExtraDataType.TYPE to "text"),
                )
                journalPrompt = null
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
    onShare: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
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
                ChevronBtn("chevron.left") { onMonthStep(-1) }
                ChevronBtn("chevron.right") { onMonthStep(1) }
                Spacer(Modifier.size(Dimens.cardSpacing / 2))
                SharePill { onShare(context) }
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
    val fill = when (nodeCompletion(program, date, viewMode, revision)) {
        true -> CalendarNodeFillStyle.Gradient(activeGradient)
        false -> if (isCustom) CalendarNodeFillStyle.GrayFlat else CalendarNodeFillStyle.Gradient(Clear30Gradients.red)
        null -> CalendarNodeFillStyle.Gray
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

@Composable
private fun ChevronBtn(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Clear30Colors.opacityGray.copy(alpha = 0.5f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
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
 * SharePill — iOS `TinyTextButton(text: "Share", background: true,
 * icon: "square.and.arrow.up")` (Home.swift:66): TinyText + 10pt icon on a gray
 * CardStyle pill (corner 12, h10/v5 padding) with press-scale, the whole button
 * at TinyTextButton's 0.7 opacity (Buttons.swift:304 — an iOS-source alpha).
 */
@Composable
private fun SharePill(onClick: () -> Unit) {
    Row(
        Modifier
            .alpha(0.7f)
            .pressScale(onClick = onClick)
            .cardStyle(color = Clear30Colors.opacityGray, cornerRadius = 12.dp, padding = false)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        TinyText("Share", color = Clear30Colors.text)
        Icon(sfSymbol("square.and.arrow.up"), contentDescription = "Share", tint = Clear30Colors.text, modifier = Modifier.size(10.dp))
    }
}

/**
 * One swipeable page in the Today feed. A lesson is fully flattened into its
 * containers (iOS `buildItems`) — each becomes its own page and counts toward the
 * progress bar. Rendering for each lives in feed/FeedContentCards.kt.
 */
private sealed interface FeedItem {
    data object CheckIn : FeedItem
    data class Video(val msg: ProgramMessage) : FeedItem
    data class Message(val msg: ProgramMessage) : FeedItem
    data class Carousel(val images: List<String>) : FeedItem
    data class Guides(val msg: ProgramMessage) : FeedItem
    data class Meditation(val med: org.clear30.data.model.ProgramMeditation) : FeedItem
    data class Reddit(val res: org.clear30.data.model.ProgramResource) : FeedItem
    data class YouTube(val res: org.clear30.data.model.ProgramResource) : FeedItem
    data class Claire(val prompt: org.clear30.data.model.ProgramClairePrompt) : FeedItem
    data class Perk(val perk: org.clear30.data.model.ProgramMemberPerk) : FeedItem
    data class Journal(val prompts: List<String>) : FeedItem
    data object FeedEnd : FeedItem
    data object Community : FeedItem
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

private fun shareProgress(context: android.content.Context, program: Program) {
    val sober = program.dayInfo.values.count { it.sober == true }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Clear30 progress: $sober days clear\n\nhttps://clear30.org")
    }
    context.startActivity(Intent.createChooser(intent, "Share progress").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}
