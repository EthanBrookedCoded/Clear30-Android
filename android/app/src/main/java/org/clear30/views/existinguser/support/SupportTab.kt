package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import org.clear30.views.components.pressScale
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.RemoteFeedbackConfig
import org.clear30.data.model.UserInfo
import org.clear30.data.model.decodeTo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.fetchSymptomInfos
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * SupportTab — ported from Support2.swift (the support hub). Sections, in order:
 * Immediate support (Cravings / Sleep / Claire), Human support (Dr. Fred / Gerad),
 * a content library (Meditations / YouTube / Reddit / Claire Prompts), and
 * Help & Feedback. Sub-screens (Claire, Dr Fred, Meditations, Reddit, YouTube,
 * Messages, Claire prompts) are reached through a real [SupportRoute] BACK STACK,
 * so back from a nested screen (symptom → Claire) returns to where you were.
 */
private sealed interface SupportRoute {
    data object Hub : SupportRoute
    data class Claire(val initialInput: String = "") : SupportRoute
    data object Cravings : SupportRoute
    data object Sleep : SupportRoute
    data object DrFred : SupportRoute
    data object PeerSupport : SupportRoute
    data object Meditations : SupportRoute
    data object Reddits : SupportRoute
    data object YouTubes : SupportRoute
    data object Messages : SupportRoute
    data object AllPrompts : SupportRoute
    data object Wishlist : SupportRoute
    data class Feedback(
        val config: org.clear30.data.model.RemoteFeedbackConfig? = null,
        val forceFreeForm: Boolean = false,
    ) : SupportRoute
    data class Symptom(val key: String, val info: org.clear30.data.model.SymptomInfo) : SupportRoute
    data class OpenMessage(val message: org.clear30.data.model.ProgramMessage) : SupportRoute
    data object SchoolInfo : SupportRoute
    data class SchoolActivityDetail(val activity: org.clear30.data.model.SchoolDataActivity) : SupportRoute
    data class SchoolResourceDetail(val resource: org.clear30.data.model.SchoolDataResource) : SupportRoute
}

@Composable
fun SupportTab(program: Program, userInfo: UserInfo, journalEntries: org.clear30.data.model.JournalEntries) {
    // Real back stack (iOS NavigationPath) — back pops one level instead of
    // jumping to the hub, so symptom → Claire → back lands on the symptom page.
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<SupportRoute>(SupportRoute.Hub) }
    val route = backStack.last()
    val push: (SupportRoute) -> Unit = { backStack.add(it) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    // Hoisted above the Hub `if` so the hub's scroll position survives while a
    // nested route is on top (returning doesn't reset to the top).
    val hubScroll = rememberScrollState()

    // The `feedback-method` experiment payload drives the feedback card + sheet
    // (iOS Support2.feedbackMonster). Loaded from the persisted controller —
    // refreshExperiments keeps it current on app load. `feedbackLoaded` gates
    // the card so the default monster doesn't flash before the async load lands.
    var feedbackConfig by remember { mutableStateOf<org.clear30.data.model.RemoteFeedbackConfig?>(null) }
    var feedbackLoaded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val experiments = org.clear30.data.Clear30Store.loadExperimentController()
        if (experiments.showFeature(org.clear30.data.model.ExperimentKey.feedbackMethod)) {
            feedbackConfig = experiments.getFeature(org.clear30.data.model.ExperimentKey.feedbackMethod)
                ?.payload?.decodeTo<org.clear30.data.model.RemoteFeedbackConfig>()
        }
        feedbackLoaded = true
    }
    // Meditation tapped on the hub's library rail — opens the standard sheet.
    var meditationSheet by remember { mutableStateOf<org.clear30.data.model.ProgramMeditation?>(null) }
    // "Slip up?" hero → the full slipped sheet (iOS SlippedSheet).
    var showSlipped by remember { mutableStateOf(false) }

    // Pull the program's messages from Supabase so the library rails populate even
    // if the user opens Support before the Today tab (mirrors TodayTab's fetch).
    var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var symptomInfos by remember { mutableStateOf<org.clear30.data.model.SymptomInfos?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Main break's start, not the start-soon bridge's (see TodayTab note).
        val contentStart = (program.currentBreakNotStartSoon ?: program.currentBreak)?.startDate ?: program.startDate
        if (org.clear30.data.ProgramMessageHandler.ensureContent(program, contentStart, userInfo.name)) refresh++
        // Pull the REAL symptom infos from the backend (`get_symptom_infos`, the
        // emoji-Name → {tips,reddits,prompts} map) — the same source iOS uses. Cache
        // them; fall back to the last cache, then the built-in set, so the carousel
        // is always populated even offline / pre-fetch.
        val remote = org.clear30.data.supabase.SupabaseController.fetchSymptomInfos()
        val loadedSymptoms = org.clear30.data.Clear30Store.loadSymptomInfos()
        symptomInfos = when {
            remote != null -> { org.clear30.data.Clear30Store.save(remote); remote }
            loadedSymptoms.symptomInfos.isNotEmpty() -> loadedSymptoms
            else -> org.clear30.data.model.SymptomInfos.builtIn()
        }
    }

    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(sub) {
        when (sub) {
            is org.clear30.data.DeepLinkRoute.Claire -> {
                val prompt = (sub as org.clear30.data.DeepLinkRoute.Claire).prompt
                // Replace an already-open Claire instead of stacking a second
                // one (double-back + dropped prompt otherwise).
                if (backStack.last() is SupportRoute.Claire) {
                    backStack[backStack.lastIndex] = SupportRoute.Claire(prompt)
                } else {
                    push(SupportRoute.Claire(prompt))
                }
            }
            is org.clear30.data.DeepLinkRoute.DrFred -> push(SupportRoute.DrFred)
            is org.clear30.data.DeepLinkRoute.Meditation -> push(SupportRoute.Meditations)
            is org.clear30.data.DeepLinkRoute.Messages -> push(SupportRoute.Messages)
            else -> return@LaunchedEffect
        }
        org.clear30.AppState.requestSubRoute(null)
    }
    androidx.compose.runtime.key(route) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val event = when (route) {
                is SupportRoute.Meditations -> LogEventType.openedMeditations
                is SupportRoute.Reddits -> LogEventType.openedReddit
                is SupportRoute.YouTubes -> LogEventType.openedYouTube
                else -> null
            }
            event?.let { Logger.logEvent(userInfo.loggingID, it) }
        }
    }
    androidx.activity.compose.BackHandler(enabled = backStack.size > 1) { back() }
    // Claire renders as an OVERLAY above the route it was pushed from, so that
    // route (e.g. the message-viewer page you were on) stays composed and back
    // returns exactly where you were instead of remounting at the list.
    val claireRoute = route as? SupportRoute.Claire
    val baseRoute = if (claireRoute == null) route else backStack.getOrNull(backStack.lastIndex - 1) ?: SupportRoute.Hub
    when (baseRoute) {
        is SupportRoute.Claire -> Unit // only reachable as the overlay below
        is SupportRoute.Cravings -> CravingHub(program, userInfo, onBack = back)
        is SupportRoute.Sleep -> CravingResources(program, userInfo, MeditationResourceKind.SLEEP, onBack = back)
        is SupportRoute.DrFred -> DrFredChat(userInfo, onBack = back)
        is SupportRoute.PeerSupport -> PeerSupportChat(userInfo, onBack = back)
        is SupportRoute.Meditations -> MeditationsScreen(program, onBack = back)
        is SupportRoute.Reddits ->
            ResourcesScreen(program, kind = ResourceKind.REDDIT, symptomInfos = symptomInfos, onBack = back)
        is SupportRoute.YouTubes -> ResourcesScreen(program, kind = ResourceKind.YOUTUBE, onBack = back)
        is SupportRoute.Messages -> MessagesLibraryScreen(program, userInfo, journalEntries, onBack = back)
        is SupportRoute.AllPrompts ->
            AllPromptsScreen(program, onBack = back, onOpenPrompt = { push(SupportRoute.Claire(it.prompt)) })
        is SupportRoute.Wishlist -> FeatureWishlist(userInfo, onBack = back)
        is SupportRoute.Feedback ->
            FeedbackScreen(userInfo, config = baseRoute.config, forceFreeForm = baseRoute.forceFreeForm, onBack = back)
        is SupportRoute.Symptom ->
            org.clear30.views.existinguser.profile.SymptomDetailScreen(
                baseRoute.key, baseRoute.info, userInfo, onBack = back,
                onAskClaire = { prompt -> push(SupportRoute.Claire(prompt)) },
            )
        is SupportRoute.OpenMessage -> {
            // Open the whole day GROUP (iOS navigates with [ProgramMessage]) so
            // same-day sub-messages (assessment responses) follow the main one.
            val group = program.contentInfo[org.clear30.data.model.PlainDate.from(baseRoute.message.unlockOn)]
                ?.messages?.takeIf { baseRoute.message in it } ?: listOf(baseRoute.message)
            org.clear30.views.existinguser.today.MessageDetail(program, group, userInfo, journalEntries, onBack = back)
        }
        is SupportRoute.SchoolInfo -> {
            val schoolData = userInfo.schoolData
            if (schoolData == null) back()
            else SchoolInfoScreen(
                schoolData = schoolData,
                program = program,
                userInfo = userInfo,
                onBack = back,
                onOpenMessage = { push(SupportRoute.OpenMessage(it)) },
                onOpenActivity = { push(SupportRoute.SchoolActivityDetail(it)) },
                onOpenResource = { push(SupportRoute.SchoolResourceDetail(it)) },
            )
        }
        is SupportRoute.SchoolActivityDetail -> {
            val a = baseRoute.activity
            SchoolInfoDetailScreen(
                title = a.title,
                gradient = userInfo.schoolData?.schoolGradient() ?: Clear30Gradients.clear30,
                orgName = a.org_name,
                subtitle = a.subtitle,
                date = a.date_time,
                repeats = a.repeats,
                location = a.location,
                phoneNumber = a.phone_number,
                email = a.email,
                link = a.link,
                facilitatedBy = a.facilitated_by,
                subLinks = a.sub_links,
                description = a.description,
                onBack = back,
            )
        }
        is SupportRoute.SchoolResourceDetail -> {
            val r = baseRoute.resource
            SchoolInfoDetailScreen(
                title = r.title,
                gradient = userInfo.schoolData?.schoolGradient() ?: Clear30Gradients.clear30,
                subtitle = r.subtitle,
                location = r.location,
                phoneNumber = r.phone_number,
                link = r.link,
                description = r.description,
                badge = r.badge,
                onBack = back,
            )
        }
        is SupportRoute.Hub -> Unit
    }

    if (baseRoute is SupportRoute.Hub) {
    val context = LocalContext.current
    val openResource = rememberOpenResource()
    Column(
        Modifier.fillMaxSize().verticalScroll(hubScroll)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        @Suppress("UNUSED_EXPRESSION") refresh

        // Heading (search removed per Thatcher — intentional divergence from
        // iOS Support2, which keeps a search feature; §17-Q23).
        Heading1("Support")

        // Immediate support — iOS order: slipped, craving, sleep, Claire
        // (Support2.swift:156-168).
        SectionLabel("Immediate support")
        SupportHero("leaf.fill", "Slip up?", "A moment of support", Clear30Gradients.slipped, Clear30Colors.slipped1) {
            showSlipped = true
        }
        SupportHero("flame.fill", "Cravings?", "Help is here", Clear30Gradients.red, Clear30Colors.red2) { push(SupportRoute.Cravings) }
        SupportHero("moon.fill", "Trouble Sleeping?", "Wind down", Clear30Gradients.sleep, Clear30Colors.sleep1) { push(SupportRoute.Sleep) }
        ClaireCompact { seed -> push(SupportRoute.Claire(seed)) }

        // Human support
        Spacer(Modifier.height(Dimens.cardSpacing))
        SectionLabel("Human support")
        HumanSupportRow("Dr. Fred", "Addiction specialist", "Tap to chat", Clear30Gradients.fred, org.clear30.R.drawable.fred) { push(SupportRoute.DrFred) }
        HumanSupportRow("Gerad", "Your accountability buddy", "Tap to chat", Clear30Gradients.buddy, org.clear30.R.drawable.gerad) { push(SupportRoute.PeerSupport) }

        // Symptom support — horizontal carousel of symptom cards (iOS symptomSupportSection).
        symptomInfos?.takeIf { it.symptomInfos.isNotEmpty() }?.let { infos ->
            Spacer(Modifier.height(Dimens.cardSpacing))
            SectionLabel("Symptom support")
            SymptomSupportSection(infos, program) { key, info -> push(SupportRoute.Symptom(key, info)) }
        }

        SectionDivider()

        // Your School — first item of the library block when the user belongs to
        // a school (iOS Support2.swift `schoolInfoSection`).
        if (userInfo.schoolData != null) {
            SchoolInfoHubSection(userInfo) { push(SupportRoute.SchoolInfo) }
        }

        // Your Library — dynamic content rails (gated on unlocked program content),
        // mirroring iOS Support2.swift. YouTube/Reddit/Claire-prompt/Meditation rails
        // only appear once the program unlocks messages that contain them.
        SectionLabel("Daily Topics")
        // The three most recent unlocked messages, each badged with the date it
        // appeared, plus a "See all messages" link. The dynamic library rails
        // (Meditations / YouTube / Reddit / Claire) follow — self-titled and gated
        // on unlocked content (so they're hidden until that content unlocks).
        DailyTopicsSection(
            program,
            onOpen = { msg -> push(SupportRoute.OpenMessage(msg)) },
            onSeeAll = { push(SupportRoute.Messages) },
        )
        LibrarySection(
            program = program,
            onViewAllMeditations = { push(SupportRoute.Meditations) },
            onViewAllYouTubes = { push(SupportRoute.YouTubes) },
            onViewAllReddits = { push(SupportRoute.Reddits) },
            onViewAllClairePrompts = { push(SupportRoute.AllPrompts) },
            onPlayMeditation = { msg -> msg.meditation?.let { meditationSheet = it } },
            onOpenResource = openResource,
            onOpenPrompt = { prompt -> push(SupportRoute.Claire(prompt.prompt)) },
        )

        SectionDivider()

        // Help & Feedback
        Spacer(Modifier.height(Dimens.cardSpacing))
        SectionLabel("Help & Feedback")
        GradientActionButton(iconName = "questionmark.bubble.fill", gradient = Clear30Gradients.red, title = "Contact Support") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@clear30.org")))
            }
        }
        GradientActionButton(iconName = "checklist.checked", gradient = Clear30Gradients.symptomCard, title = "Feature Wishlist") {
            push(SupportRoute.Wishlist)
        }
        // iOS Support2.feedbackMonster: experiment-config card when the
        // `feedback-method` experiment is live, else the default monster card.
        // Gated on feedbackLoaded so the wrong card never flashes while the
        // async experiment load is in flight.
        // Submitted-state is device-local (comms.feedback SELECT is admin-only).
        val fc = feedbackConfig
        when {
            !feedbackLoaded -> Unit
            fc != null -> {
                val submitted = userInfo.getCachedBool(fc.cacheKey) ||
                    (fc.type == "text" && userInfo.gaveFeedback == true) // legacy flag
                val completionConfig = fc.completion
                when {
                    submitted && completionConfig != null -> FeedbackConfigCard(completionConfig, Clear30Gradients.journals) {
                        push(SupportRoute.Feedback(completionConfig))
                    }
                    submitted -> FeedbackMonsterCard(gaveFeedback = true) { push(SupportRoute.Feedback()) }
                    else -> FeedbackConfigCard(fc, Clear30Gradients.reddit) {
                        push(SupportRoute.Feedback(fc, forceFreeForm = true))
                    }
                }
            }
            else -> FeedbackMonsterCard(gaveFeedback = userInfo.gaveFeedback == true) { push(SupportRoute.Feedback()) }
        }
        Spacer(Modifier.height(Dimens.cardSpacing))
    }

    meditationSheet?.let { med ->
        MeditationPage(meditation = med, program = program, onDismiss = { meditationSheet = null })
    }

    if (showSlipped) {
        org.clear30.views.existinguser.support.slipped.SlippedSheet(
            userInfo = userInfo,
            program = program,
            callbacks = org.clear30.views.existinguser.support.slipped.SlippedCallbacks(
                onDismiss = { showSlipped = false },
                onOpenClaire = { push(SupportRoute.Claire("")) },
                onOpenDrFred = { push(SupportRoute.DrFred) },
                onOpenPeerSupport = { push(SupportRoute.PeerSupport) },
            ),
        )
    }
    } // end Hub

    // Claire overlay — composed LAST so it draws (and registers its BackHandler)
    // above the underlying route, which stays alive underneath. The pointerInput
    // consumes taps in the chat's dead zones (gutters/header) — a background
    // modifier alone doesn't hit-test, so taps there would fall through to the
    // hidden screen below.
    claireRoute?.let { claire ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Clear30Colors.background)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            androidx.activity.compose.BackHandler { back() }
            // key: a new prompt while Claire is already open must rebuild the
            // chat with the new seed instead of keeping the old input.
            androidx.compose.runtime.key(claire.initialInput) {
                ClaireChat(onBack = back, userInfo = userInfo, program = program, initialInput = claire.initialInput)
            }
        }
    }
}

/** Dim section label (iOS `SmallText(...).opacity(0.5)`). */
@Composable
private fun SectionLabel(text: String) {
    SmallText(text, color = Clear30Colors.text.copy(alpha = 0.5f))
}

/**
 * 1px divider between the hub's major sections. iOS pads the divider by
 * `sectionSpacing` (cardSpacing * 2) on both sides (Support2.swift:125-131,
 * 985-989); the parent column's `spacedBy(cardSpacing)` supplies half, so the
 * internal padding supplies the other half.
 */
@Composable
private fun SectionDivider() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing)
            .height(1.dp).background(Clear30Colors.opacityGray),
    )
}

/** Cravings / Sleep hero — white icon disc on a gradient card with title + subtitle. */
@Composable
private fun SupportHero(icon: String, title: String, subtitle: String, gradient: Brush, iconTint: Color, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }, gradient = gradient) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(sfSymbol(icon), null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SmallText(title, color = Color.White)
                TinyText(subtitle, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

/** Claire compact card — sparkles + Chat + a row of feeling emojis, all opening Claire. */
@Composable
private fun ClaireCompact(onOpen: (String) -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.claire) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Icon(sfSymbol("sparkles"), null, tint = Color.White, modifier = Modifier.size(18.dp))
                SmallText("Claire", color = Color.White)
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            TinyText("Chat or tap how you're feeling", color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.height(Dimens.cardSpacing))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .height(Dimens.cardSpacing * 3)
                        .clip(RoundedCornerShape(Dimens.cornerRadius / 1.5f))
                        .background(Color.White)
                        .clickable { onOpen("") }
                        .padding(horizontal = Dimens.cardSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    Icon(sfSymbol("text.bubble.fill"), null, tint = Clear30Colors.claire1, modifier = Modifier.size(17.dp))
                    TinyText("Chat", color = Clear30Colors.claire1)
                }
                listOf("🤩", "🤔", "🥺", "🤢", "🫥", "😡", "🫨", "🫠").forEach { emoji ->
                    Box(
                        Modifier
                            .size(Dimens.cardSpacing * 3)
                            .clip(RoundedCornerShape(Dimens.cornerRadius / 1.5f))
                            .background(Color.White.copy(alpha = 0.5f))
                            .clickable { onOpen("Claire, I'm feeling $emoji right now.") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Heading2(emoji)
                    }
                }
            }
        }
    }
}

/** Human support row — avatar photo + name/role/preview on a gradient card. */
@Composable
private fun HumanSupportRow(name: String, role: String, preview: String, gradient: Brush, avatarRes: Int, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }, gradient = gradient) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(avatarRes),
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SmallText(name, color = Color.White)
                TinyText(role, color = Color.White.copy(alpha = 0.75f))
                TinyText(preview, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
            }
            Icon(sfSymbol("chevron.forward"), null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

/** Library section row — icon + title + "View all", navigates to the sub-screen. */
@Composable
private fun LibraryRow(title: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Icon(sfSymbol(icon), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        SmallText(title, color = Clear30Colors.text)
        Spacer(Modifier.weight(1f))
        TinyText("View all", color = Clear30Colors.text.copy(alpha = 0.5f))
        Icon(sfSymbol("chevron.forward"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
    }
}

/**
 * Config-driven feedback card (iOS BrowseButton over RemoteFeedbackConfig):
 * remote card title + image — an SF symbol when `card_image_is_symbol`, else an
 * app asset referenced by its iOS name (e.g. "Feedback Monster Hungry Cutoff" →
 * feedback_monster_hungry_cutoff).
 */
@Composable
private fun FeedbackConfigCard(config: RemoteFeedbackConfig, gradient: Brush, onClick: () -> Unit) {
    val context = LocalContext.current
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }, gradient = gradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallText(config.cardTitle, color = Color.White, modifier = Modifier.weight(1f))
            if (config.cardImageIsSymbol) {
                Icon(sfSymbol(config.cardImage), null, tint = Color.White, modifier = Modifier.size(40.dp))
            } else {
                val resId = remember(config.cardImage) { drawableByAssetName(context, config.cardImage) }
                if (resId != 0) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(resId),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.size((config.cardImageWidth ?: 72.0).dp),
                    )
                }
            }
        }
    }
}

/** iOS imageset name → Android drawable id ("Feedback Monster Full" → feedback_monster_full). */
private fun drawableByAssetName(context: android.content.Context, name: String): Int {
    val normalized = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier(normalized, "drawable", context.packageName)
}

/**
 * Feedback Monster card. Unfed = ORANGE with the sad/hungry monster (nobody fed
 * it — decision §17-Q10); fed = yellow/journals with the full monster.
 */
@Composable
private fun FeedbackMonsterCard(gaveFeedback: Boolean, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth().pressScale { onClick() },
        gradient = if (gaveFeedback) Clear30Gradients.journals else Clear30Gradients.reddit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallText(
                if (gaveFeedback) "The Feedback Monster\nis full — thank you! 💛" else "The Feedback Monster\nis hungry — feed it!",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (gaveFeedback) org.clear30.R.drawable.feedback_monster_full_cutoff else org.clear30.R.drawable.feedback_monster_hungry_cutoff,
                ),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

