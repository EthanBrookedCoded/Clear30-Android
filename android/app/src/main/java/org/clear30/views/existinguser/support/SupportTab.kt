package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * a content library (Meditations / YouTube / Reddit / Claire Prompts), Extras, and
 * Help & Feedback. Sub-screens (Claire, Dr Fred, Meditations, Reddit, YouTube,
 * Messages, Journal prompts) are reached through the local [SupportRoute] nav.
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
    data object JournalPrompts : SupportRoute
    data object Wishlist : SupportRoute
    data object Therapy : SupportRoute
    data class Feedback(
        val config: org.clear30.data.model.RemoteFeedbackConfig? = null,
        val forceFreeForm: Boolean = false,
    ) : SupportRoute
    data class Symptom(val key: String, val info: org.clear30.data.model.SymptomInfo) : SupportRoute
    data class OpenMessage(val message: org.clear30.data.model.ProgramMessage) : SupportRoute
}

@Composable
fun SupportTab(program: Program, userInfo: UserInfo, journalEntries: org.clear30.data.model.JournalEntries) {
    var route by remember { mutableStateOf<SupportRoute>(SupportRoute.Hub) }
    val back = { route = SupportRoute.Hub }

    // The `feedback-method` experiment payload drives the feedback card + sheet
    // (iOS Support2.feedbackMonster). Loaded from the persisted controller —
    // refreshExperiments keeps it current on app load.
    var feedbackConfig by remember { mutableStateOf<org.clear30.data.model.RemoteFeedbackConfig?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val experiments = org.clear30.data.Clear30Store.loadExperimentController()
        if (experiments.showFeature(org.clear30.data.model.ExperimentKey.feedbackMethod)) {
            feedbackConfig = experiments.getFeature(org.clear30.data.model.ExperimentKey.feedbackMethod)
                ?.payload?.decodeTo<org.clear30.data.model.RemoteFeedbackConfig>()
        }
    }

    // Pull the program's messages from Supabase so the library rails populate even
    // if the user opens Support before the Today tab (mirrors TodayTab's fetch).
    var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var symptomInfos by remember { mutableStateOf<org.clear30.data.model.SymptomInfos?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val contentStart = program.currentBreak?.startDate ?: program.startDate
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
            is org.clear30.data.DeepLinkRoute.Claire -> route = SupportRoute.Claire((sub as org.clear30.data.DeepLinkRoute.Claire).prompt)
            is org.clear30.data.DeepLinkRoute.DrFred -> route = SupportRoute.DrFred
            is org.clear30.data.DeepLinkRoute.Meditation -> route = SupportRoute.Meditations
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
    androidx.activity.compose.BackHandler(enabled = route !is SupportRoute.Hub) { back() }
    when (route) {
        is SupportRoute.Claire -> { ClaireChat(onBack = back, userInfo = userInfo, program = program, initialInput = (route as SupportRoute.Claire).initialInput); return }
        is SupportRoute.Cravings -> { CravingHub(program, userInfo, onBack = back); return }
        is SupportRoute.Sleep -> { CravingResources(program, userInfo, MeditationResourceKind.SLEEP, onBack = back); return }
        is SupportRoute.DrFred -> { DrFredChat(userInfo, onBack = back); return }
        is SupportRoute.PeerSupport -> { PeerSupportChat(userInfo, onBack = back); return }
        is SupportRoute.Meditations -> { MeditationsScreen(program, onBack = back); return }
        is SupportRoute.Reddits -> { ResourcesScreen(program, kind = ResourceKind.REDDIT, onBack = back); return }
        is SupportRoute.YouTubes -> { ResourcesScreen(program, kind = ResourceKind.YOUTUBE, onBack = back); return }
        is SupportRoute.Messages -> { MessagesLibraryScreen(program, userInfo, journalEntries, onBack = back); return }
        is SupportRoute.JournalPrompts -> { JournalPromptsScreen(program, journalEntries, userInfo, onBack = back); return }
        is SupportRoute.Wishlist -> { FeatureWishlist(userInfo, onBack = back); return }
        is SupportRoute.Therapy -> { BetterHelp(onBack = back); return }
        is SupportRoute.Feedback -> {
            val r = route as SupportRoute.Feedback
            FeedbackScreen(userInfo, config = r.config, forceFreeForm = r.forceFreeForm, onBack = back)
            return
        }
        is SupportRoute.Symptom -> {
            val r = route as SupportRoute.Symptom
            org.clear30.views.existinguser.profile.SymptomDetailScreen(
                r.key, r.info, userInfo, onBack = back,
                onAskClaire = { prompt -> route = SupportRoute.Claire(prompt) },
            ); return
        }
        is SupportRoute.OpenMessage -> {
            org.clear30.views.existinguser.today.MessageDetail(program, (route as SupportRoute.OpenMessage).message, userInfo, journalEntries, onBack = back); return
        }
        is SupportRoute.Hub -> Unit
    }

    val context = LocalContext.current
    val openResource = rememberOpenResource()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        @Suppress("UNUSED_EXPRESSION") refresh

        // Heading
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Heading1("Support")
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Clear30Colors.opacityGray),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol("magnifyingglass"), "Search", tint = Clear30Colors.text, modifier = Modifier.size(18.dp))
            }
        }

        // Immediate support — iOS order: slipped, craving, sleep, Claire
        // (Support2.swift:156-168). The full SlippedSheet flow isn't ported yet,
        // so the slipped hero routes to Claire with a slip-support prompt — the
        // iOS sheet's "Talk it out" activity offers Claire as a destination too.
        SectionLabel("Immediate support")
        SupportHero("leaf.fill", "Slip up?", "A moment of support", Clear30Gradients.slipped, Clear30Colors.slipped1) {
            route = SupportRoute.Claire("Claire, I slipped up and could use a moment of support.")
        }
        SupportHero("flame.fill", "Cravings?", "Help is here", Clear30Gradients.red, Clear30Colors.red2) { route = SupportRoute.Cravings }
        SupportHero("moon.fill", "Trouble Sleeping?", "Wind down", Clear30Gradients.sleep, Clear30Colors.sleep1) { route = SupportRoute.Sleep }
        ClaireCompact { seed -> route = SupportRoute.Claire(seed) }

        // Human support
        Spacer(Modifier.height(Dimens.cardSpacing))
        SectionLabel("Human support")
        HumanSupportRow("Dr. Fred", "Addiction specialist", "Tap to chat", Clear30Gradients.fred, org.clear30.R.drawable.fred) { route = SupportRoute.DrFred }
        HumanSupportRow("Gerad", "Your accountability buddy", "Tap to chat", Clear30Gradients.buddy, org.clear30.R.drawable.gerad) { route = SupportRoute.PeerSupport }

        // Symptom support — horizontal carousel of symptom cards (iOS symptomSupportSection).
        symptomInfos?.takeIf { it.symptomInfos.isNotEmpty() }?.let { infos ->
            Spacer(Modifier.height(Dimens.cardSpacing))
            SectionLabel("Symptom support")
            SymptomSupportSection(infos, program) { key, info -> route = SupportRoute.Symptom(key, info) }
        }

        SectionDivider()

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
            onOpen = { msg -> route = SupportRoute.OpenMessage(msg) },
            onSeeAll = { route = SupportRoute.Messages },
        )
        LibrarySection(
            program = program,
            onViewAllMeditations = { route = SupportRoute.Meditations },
            onViewAllYouTubes = { route = SupportRoute.YouTubes },
            onViewAllReddits = { route = SupportRoute.Reddits },
            onViewAllClairePrompts = { route = SupportRoute.Claire() },
            onPlayMeditation = { _ -> route = SupportRoute.Meditations },
            onOpenResource = openResource,
            onOpenPrompt = { prompt -> route = SupportRoute.Claire(prompt.prompt) },
        )

        SectionDivider()

        // Extras
        Spacer(Modifier.height(Dimens.cardSpacing))
        SectionLabel("Extras")
        GradientActionButton(iconName = "pencil.and.outline", gradient = Clear30Gradients.journals, title = "Journal Prompts") { route = SupportRoute.JournalPrompts }
        GradientActionButton(iconName = "doc.text.fill", gradient = Clear30Gradients.clear30, title = "Is Therapy For Me?") { route = SupportRoute.Therapy }

        // Help & Feedback
        Spacer(Modifier.height(Dimens.cardSpacing))
        SectionLabel("Help & Feedback")
        GradientActionButton(iconName = "questionmark.bubble.fill", gradient = Clear30Gradients.red, title = "Contact Support") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@clear30.org")))
            }
        }
        GradientActionButton(iconName = "checklist.checked", gradient = Clear30Gradients.symptomCard, title = "Feature Wishlist") {
            route = SupportRoute.Wishlist
        }
        // iOS Support2.feedbackMonster: experiment-config card when the
        // `feedback-method` experiment is live, else the default monster card.
        // Submitted-state is device-local (comms.feedback SELECT is admin-only).
        val fc = feedbackConfig
        if (fc != null) {
            val submitted = userInfo.getCachedBool(fc.cacheKey) ||
                (fc.type == "text" && userInfo.gaveFeedback == true) // legacy flag
            val completionConfig = fc.completion
            when {
                submitted && completionConfig != null -> FeedbackConfigCard(completionConfig, Clear30Gradients.journals) {
                    route = SupportRoute.Feedback(completionConfig)
                }
                submitted -> FeedbackMonsterCard(gaveFeedback = true) { route = SupportRoute.Feedback() }
                else -> FeedbackConfigCard(fc, Clear30Gradients.reddit) {
                    route = SupportRoute.Feedback(fc, forceFreeForm = true)
                }
            }
        } else {
            FeedbackMonsterCard(gaveFeedback = userInfo.gaveFeedback == true) { route = SupportRoute.Feedback() }
        }
        Spacer(Modifier.height(Dimens.cardSpacing))
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

/** Feedback Monster card — playful yellow card. (Insert image) marks the monster art. */
@Composable
private fun FeedbackMonsterCard(gaveFeedback: Boolean, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }, gradient = Clear30Gradients.journals) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallText(
                if (gaveFeedback) "The Feedback Monster\nis full — thank you! 💛" else "The Feedback Monster\nLoves You!",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (gaveFeedback) org.clear30.R.drawable.feedback_monster_full_cutoff else org.clear30.R.drawable.feedback_monster,
                ),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

