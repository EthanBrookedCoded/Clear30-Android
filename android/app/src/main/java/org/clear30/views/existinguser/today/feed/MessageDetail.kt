package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.JournalEntries
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
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.InlineVideoPlayer
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.VideoThumbnail
import org.clear30.views.components.RedditDialog
import org.clear30.views.components.WebViewDialog
import org.clear30.views.components.YouTubeDialog
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.youTubeId
import org.clear30.views.existinguser.support.MeditationPlayer
import org.clear30.util.now
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * MessageDetail — opens a single [ProgramMessage] full-bleed. Renders the body,
 * the page-info pages (intro / how-to / why-it-matters style — flattened to a
 * vertical stack rather than the iOS pager since vertical reading is the
 * dominant Android idiom), and any Reddit/YouTube/Meditation/Claire-prompt
 * cross-links from `allResources` — each opens in the system browser.
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
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // YouTube links play in an embedded in-app IFrame instead of opening the
    // browser/YouTube app — set the id to open the overlay.
    var youTubeVideoId by remember { mutableStateOf<String?>(null) }
    // Reddit threads / other links open in the in-app web viewer.
    var webUrl by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    // Meditation plays inline in-app (auto-plays) instead of opening externally.
    var playingMeditation by remember { mutableStateOf<ProgramMeditation?>(null) }
    // Tapping a "Journal on this" prompt opens a composer seeded with the prompt.
    var journalPrompt by remember { mutableStateOf<String?>(null) }

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1(message.topicTitle)
        }
        if (message.subtitle.isNotBlank()) {
            SmallText(message.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        // Lead video — plays in-app. Direct files (.mp4/.mov, Supabase storage)
        // play inline via ExoPlayer; a YouTube URL opens the embedded player.
        message.videoURL?.let { url ->
            val ytId = youTubeId(url)
            if (ytId != null) {
                VideoThumbnail(message.thumbnailURL, Modifier.fillMaxWidth()) {
                    Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.URL to url))
                    youTubeVideoId = ytId
                }
            } else {
                InlineVideoPlayer(url, message.thumbnailURL, Modifier.fillMaxWidth()) {
                    Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.URL to url))
                }
            }
        }

        if (message.message.isNotBlank()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) { SmallText(message.message) }
        }

        // Image carousel — horizontally scrollable (iOS carousel_images).
        message.carouselImages?.takeIf { it.isNotEmpty() }?.let { images -> ImageCarousel(images) }

        // Page info — collapsed to a vertical sequence of cards (iOS used a
        // horizontal pager; vertical is the better fit for read-then-scroll
        // on Android).
        message.programPageInfo.forEach { page ->
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                    Heading3(page.title)
                    SmallText(page.body)
                }
            }
        }

        if (message.hasReddits) {
            Heading3("Stories on Reddit")
            message.reddits.forEach { res -> LinkRow(res, uriHandler, userInfo, LogEventType.openedRedditThread, onOpen = { redditUrl = it }) }
        }
        if (message.hasYouTubes) {
            Heading3("Watch")
            message.youTubes.forEach { res ->
                val ytId = youTubeId(res.url)
                if (ytId != null) {
                    YouTubeRow(res) {
                        Logger.logEvent(userInfo.loggingID, LogEventType.openedYouTubeVideo, mapOf(LogEventExtraDataType.URL to res.url))
                        youTubeVideoId = ytId
                    }
                } else {
                    LinkRow(res, uriHandler, userInfo, LogEventType.openedYouTubeVideo, onOpen = { webUrl = it })
                }
            }
        }
        message.meditation?.let { med ->
            Heading3("Meditation")
            if (playingMeditation?.url == med.url) {
                // In-app player — auto-plays through ExoPlayer (no external jump).
                MeditationPlayer(med, program, onClose = { playingMeditation = null })
            } else {
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.listenedToMeditation,
                            mapOf(LogEventExtraDataType.MEDITATION_NAME to med.name),
                        )
                        playingMeditation = med
                    },
                    gradient = Clear30Gradients.meditation,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        Icon(sfSymbol("play.fill"), contentDescription = null)
                        SmallText(med.name)
                    }
                }
            }
        }
        if (message.hasOnlyResources) {
            Heading3("Resources")
            message.onlyResources.forEach { res -> LinkRow(res, uriHandler, userInfo, LogEventType.openedContent, onOpen = { webUrl = it }) }
        }

        // Claire prompts — tap to open Claire (cross-tab via the deep-link route).
        if (message.clairePrompts.isNotEmpty()) {
            Heading3("Talk it through with Claire")
            message.clairePrompts.forEach { prompt -> ClairePromptCard(prompt, userInfo) }
        }

        // Journal prompts — tap to write a journal entry seeded with the prompt.
        message.journalPrompts?.takeIf { it.isNotEmpty() }?.let { prompts ->
            Heading3("Journal on this")
            prompts.forEach { p -> JournalPromptCard(p) { journalPrompt = p } }
        }

        // Member perks — premium CTAs (open link, or jump to a known section).
        message.memberPerks?.takeIf { it.isNotEmpty() }?.let { perks ->
            perks.forEach { perk -> MemberPerkCard(perk, uriHandler) }
        }
    }

    // In-app overlays (kept outside the scroll so they cover the screen).
    youTubeVideoId?.let { id -> YouTubeDialog(id, onDismiss = { youTubeVideoId = null }) }
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

// JournalOnTopicDialog is shared from feed/FeedContentCards.kt (same package).

/** A "Watch" link that opens the in-app YouTube player instead of the browser. */
@Composable
private fun YouTubeRow(res: ProgramResource, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(sfSymbol("play.fill"), contentDescription = null, tint = Clear30Colors.red1)
            Column(Modifier.weight(1f)) {
                SmallText(res.title)
                TinyText(res.url, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/** Horizontally scrollable image carousel (iOS carousel_images). */
@Composable
private fun ImageCarousel(images: List<String>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        images.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.height(180.dp).width(260.dp).clip(RoundedCornerShape(Dimens.cornerRadius)),
            )
        }
    }
}

/** A Claire conversation starter; tapping opens Claire pre-seeded with the prompt. */
@Composable
private fun ClairePromptCard(prompt: ProgramClairePrompt, userInfo: UserInfo) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedContent, mapOf(LogEventExtraDataType.EXTRA to prompt.title))
            org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Claire(prompt.prompt))
            org.clear30.AppState.requestTab("SUPPORT")
        },
        gradient = Clear30Gradients.claire,
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

@Composable
private fun JournalPromptCard(prompt: String, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(sfSymbol("square.and.pencil"), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.5f))
            SmallText(prompt, Modifier.weight(1f))
        }
    }
}

/** Premium perk CTA — opens its link if present, else jumps to a known section. */
@Composable
private fun MemberPerkCard(perk: ProgramMemberPerk, uri: androidx.compose.ui.platform.UriHandler) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            perk.imageURL?.let { img ->
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(Dimens.cornerRadius)),
                )
            }
            perk.title?.let { SmallText(it, color = Color.White) }
            perk.subtitle?.let { TinyText(it, color = Color.White.copy(alpha = 0.75f)) }
            DefaultButton(perk.buttonText, gradient = Clear30Gradients.button) {
                perk.buttonLink?.let { uri.openUri(it) }
                    ?: when (perk.sheetType) {
                        "groups", "group" -> org.clear30.AppState.requestTab("GROUPS")
                        "claire" -> { org.clear30.AppState.requestSubRoute(org.clear30.data.DeepLinkRoute.Claire()); org.clear30.AppState.requestTab("SUPPORT") }
                        "community" -> org.clear30.AppState.requestTab("COMMUNITY")
                        else -> {}
                    }
            }
        }
    }
}

@Composable
private fun LinkRow(
    res: ProgramResource,
    uri: androidx.compose.ui.platform.UriHandler,
    userInfo: UserInfo,
    event: LogEventType,
    onOpen: ((String) -> Unit)? = null,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable {
        Logger.logEvent(
            userInfo.loggingID, event,
            mapOf(LogEventExtraDataType.URL to res.url),
        )
        // Open in-app when a handler is supplied (Reddit / resources), else fall
        // back to the system browser.
        if (onOpen != null) onOpen(res.url) else uri.openUri(res.url)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(sfSymbol("arrow.up.right"), contentDescription = null)
            Column(Modifier.weight(1f)) {
                SmallText(res.title)
                TinyText(res.url, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}
