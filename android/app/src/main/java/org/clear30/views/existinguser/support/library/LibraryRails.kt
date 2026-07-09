package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.unlocked
import org.clear30.util.daysTo
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * "Your Library" — ported from Support2.swift's library section. Dynamic content
 * rails (Meditations, YouTube, Reddit, Claire Prompts) that surface only the
 * messages the program has already *unlocked*; each rail caps at 5 cards and, when
 * fewer are unlocked, appends a "Coming soon" card with the next unlock date.
 *
 * This is why YouTube/Reddit are program-gated: a rail is hidden entirely until at
 * least one message containing that resource type unlocks, exactly like iOS.
 */
@Composable
internal fun LibrarySection(
    program: Program,
    onViewAllMeditations: () -> Unit,
    onViewAllYouTubes: () -> Unit,
    onViewAllReddits: () -> Unit,
    onViewAllClairePrompts: () -> Unit,
    onPlayMeditation: (ProgramMessage) -> Unit,
    onOpenResource: (ProgramResource) -> Unit,
    onOpenPrompt: (ProgramClairePrompt) -> Unit,
) {
    val allMessages = program.contentInfo.values.flatMap { it.messages }
    val unlockedMessages = allMessages.unlocked.reversed()

    val meditations = unlockedMessages.filter { it.meditation != null }
    val youTubes = unlockedMessages.flatMap { it.youTubes }.distinctBy { it.url }
    val reddits = unlockedMessages.flatMap { it.reddits }.distinctBy { it.url }
    val prompts = unlockedMessages.flatMap { it.clairePrompts }.distinctBy { it.id }

    fun nextUnlock(predicate: (ProgramMessage) -> Boolean) =
        allMessages.filter { !it.unlocked && predicate(it) }.minByOrNull { it.unlockOn }?.unlockOn

    Column(
        Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        if (meditations.isNotEmpty()) {
            RailSection("Meditations", "sun.horizon.fill", Clear30Colors.meditation1, onViewAllMeditations) {
                meditations.take(5).forEach { msg -> MeditationRailCard(msg) { onPlayMeditation(msg) } }
                nextUnlock { it.meditation != null }?.let { ComingSoonRailCard("meditations", it, 160.dp, 120.dp) }
            }
        }
        if (youTubes.isNotEmpty()) {
            RailSection("YouTube Videos", "play.fill", Clear30Colors.youTube1, onViewAllYouTubes) {
                youTubes.take(5).forEach { res -> YouTubeRailCard(res) { onOpenResource(res) } }
                nextUnlock { it.hasYouTubes }?.let { ComingSoonRailCard("YouTube videos", it, 240.dp, 250.dp) }
            }
        }
        if (reddits.isNotEmpty()) {
            RailSection("Reddit Threads", "text.bubble.fill", Clear30Colors.reddit1, onViewAllReddits) {
                reddits.take(5).forEach { res -> RedditRailCard(res) { onOpenResource(res) } }
                nextUnlock { it.hasReddits }?.let { ComingSoonRailCard("Reddit threads", it, 200.dp, 130.dp) }
            }
        }
        if (prompts.isNotEmpty()) {
            RailSection("Claire Prompts", "sparkles", Clear30Colors.claire1, onViewAllClairePrompts) {
                prompts.take(5).forEach { prompt -> ClairePromptRailCard(prompt) { onOpenPrompt(prompt) } }
                nextUnlock { it.hasClairePrompts }?.let { ComingSoonRailCard("Claire prompts", it, 220.dp, 160.dp) }
            }
        }
    }
}

/** Section header (icon + title + "View all") above a horizontally-scrolling rail. */
@Composable
private fun RailSection(
    title: String,
    iconName: String,
    iconTint: Color,
    onViewAll: () -> Unit,
    cards: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onViewAll).padding(vertical = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            Icon(sfSymbol(iconName), null, tint = iconTint, modifier = Modifier.size(18.dp))
            SmallText(title, color = Clear30Colors.text)
            Spacer(Modifier.weight(1f))
            TinyText("View all", color = Clear30Colors.text.copy(alpha = 0.5f))
            Icon(sfSymbol("chevron.forward"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(10.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Dimens.cardSpacing / 4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) { cards() }
    }
}

/** YouTube rail card — 16:9 thumbnail + red body with "YouTube" + emoji title. */
@Composable
private fun YouTubeRailCard(resource: ProgramResource, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.width(240.dp).height(250.dp).pressScale { onClick() },
        gradient = Clear30Gradients.youtube,
        padding = false,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(135.dp).background(Clear30Colors.opacityGrayFlattened)) {
                youTubeThumbnailUrl(resource.url)?.let { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(Dimens.cardSpacing)) {
                BrandLabel("play.fill", "YouTube")
                Spacer(Modifier.height(Dimens.cardSpacing / 2))
                SmallText(resource.title, color = Color.White, maxLines = 3)
            }
        }
    }
}

/** Reddit rail card — orange body with r/Subreddit + emoji title. */
@Composable
private fun RedditRailCard(resource: ProgramResource, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.width(200.dp).height(130.dp).pressScale { onClick() },
        gradient = Clear30Gradients.reddit,
    ) {
        Column(Modifier.fillMaxSize()) {
            BrandLabel("text.bubble.fill", extractSubreddit(resource.url) ?: "Reddit")
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            SmallText(resource.title, color = Color.White, maxLines = 3)
        }
    }
}

/** Claire prompt card — purple body with "Claire" + prompt title + Start chat pill. */
@Composable
private fun ClairePromptRailCard(prompt: ProgramClairePrompt, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.width(220.dp).height(160.dp).pressScale { onClick() },
        gradient = Clear30Gradients.claire,
    ) {
        Column(Modifier.fillMaxSize()) {
            BrandLabel("sparkles", "Claire")
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            SmallText(prompt.title, color = Color.White, maxLines = 3)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                TinyText("Start chat", color = Color.White)
                Icon(sfSymbol("arrow.right"), null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
    }
}

/** Meditation card — blue body with a play disc + emoji + meditation name. */
@Composable
private fun MeditationRailCard(message: ProgramMessage, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.width(160.dp).height(120.dp).pressScale { onClick() },
        gradient = Clear30Gradients.meditation,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(sfSymbol("play.fill"), null, tint = Clear30Colors.meditation1, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallText("${message.topicEmoji ?: "🧘"} ${message.meditation?.name ?: ""}".trim(), color = Color.White, maxLines = 2)
        }
    }
}

/** Coming-soon placeholder shown at the end of a rail with fewer than 5 unlocked. */
@Composable
private fun ComingSoonRailCard(contentType: String, nextUnlock: kotlinx.datetime.Instant, width: Dp, height: Dp) {
    Clear30Card(
        modifier = Modifier.width(width).height(height),
        color = Clear30Colors.opacityGrayFlattened,
        shadowColor = Color.Transparent,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Icon(sfSymbol("clock.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                TinyText("Coming soon", color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1)
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            TinyText("More $contentType ${relativeUnlock(nextUnlock)}", color = Clear30Colors.text.copy(alpha = 0.25f))
        }
    }
}

/** Small brand row (white glyph + label) at the top of a content card. */
@Composable
private fun BrandLabel(iconName: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Icon(sfSymbol(iconName), null, tint = Color.White, modifier = Modifier.size(16.dp))
        TinyText(label, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

// MARK: - Helpers

/**
 * Returns a callback that opens a resource IN-APP — YouTube links in the embedded
 * IFrame player, everything else (Reddit threads, articles) in the in-app web
 * viewer — and emits the overlay itself. Callers just invoke the returned lambda;
 * no more system VIEW intent bouncing out to Chrome/the YouTube app.
 */
@Composable
internal fun rememberOpenResource(): (ProgramResource) -> Unit {
    var webUrl by remember { mutableStateOf<String?>(null) }
    var ytId by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }
    webUrl?.let { u -> org.clear30.views.components.WebViewDialog(u, onDismiss = { webUrl = null }) }
    ytId?.let { id -> org.clear30.views.components.YouTubeDialog(id, onDismiss = { ytId = null }) }
    // Reddit threads open in the native in-app viewer (RedditScraper + comments),
    // falling back to the web view internally if the fetch is blocked.
    redditUrl?.let { u -> org.clear30.views.components.RedditDialog(u, onDismiss = { redditUrl = null }) }
    return { res ->
        val yt = org.clear30.views.components.youTubeId(res.url)
        when {
            res.isReddit -> redditUrl = res.url
            yt != null -> ytId = yt
            else -> webUrl = res.url
        }
    }
}

/** YouTube video id → hqdefault thumbnail (Swift `YouTubeCard.thumbnailURL`). */
internal fun youTubeThumbnailUrl(url: String): String? =
    youTubeVideoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

private fun youTubeVideoId(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.host?.contains("youtu.be") == true) return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    return uri.getQueryParameter("v")
}

/** "r/Subreddit" from a reddit thread URL (Swift `SymptomInfos.extractSubreddit`). */
internal fun extractSubreddit(url: String): String? {
    val match = Regex("reddit\\.com/r/([^/]+)").find(url) ?: return null
    return "r/${match.groupValues[1]}"
}

/** Relative phrase for a future unlock date ("tomorrow" / "in N days" / "soon"). */
private fun relativeUnlock(instant: kotlinx.datetime.Instant): String {
    val days = now().daysTo(instant)
    return when {
        days <= 0 -> "soon"
        days == 1 -> "tomorrow"
        days < 7 -> "in $days days"
        days < 14 -> "in a week"
        else -> "in ${days / 7} weeks"
    }
}
