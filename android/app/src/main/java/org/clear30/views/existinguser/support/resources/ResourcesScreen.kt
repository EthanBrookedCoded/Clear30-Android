package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.datetime.Instant
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.SymptomInfos
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Which resource shelf to render — Reddit threads or YouTube videos. */
internal enum class ResourceKind(val title: String, val contentType: String, val emptyMsg: String) {
    REDDIT("Reddit Threads", "Reddit threads", "Reddit threads will appear here as your content unlocks."),
    YOUTUBE("YouTube", "YouTube videos", "YouTube videos will appear here as your content unlocks.");
}

/** A rendered section — stage title + gradient (or symptom name + gradient) and its resources. */
private data class ResourceDisplaySection(val title: String, val gradient: Brush, val resources: List<ProgramResource>)

/**
 * ResourcesScreen — ported to match iOS AllRedditsView / AllYouTubesView. Content is
 * filtered by program break through the [LibraryFilterSheet] (iOS `FilterListSheet`),
 * then **sectioned by stage** (a colored stage header card, iOS
 * `ProgramMessageSectionCard`), and laid out as a **2-column grid** of branded cards
 * (iOS `RedditCard` / `YouTubeCard`, Cards.swift:201-345). The Reddit library
 * additionally offers a "Symptoms" filter (AllRedditsView.swift:153-159) that
 * re-sections the list per symptom, alphabetically, using each symptom's gradient.
 * Cards open their link in the in-app viewers.
 */
@Composable
internal fun ResourcesScreen(
    program: Program,
    kind: ResourceKind,
    symptomInfos: SymptomInfos? = null,
    onBack: () -> Unit,
) {
    val brandGradient = if (kind == ResourceKind.REDDIT) Clear30Gradients.reddit else Clear30Gradients.youtube

    // Break/core tabs + their stage sections (shared library skeleton).
    val tabs = remember(program.contentInfo.size, kind) {
        buildLibraryTabs(program) { msgs ->
            msgs.flatMap { it.allResources }
                .filter { if (kind == ResourceKind.REDDIT) it.isReddit else it.isYouTube }
                .distinctBy { it.url }
        }
    }
    // Reddit gets an extra "Symptoms" filter option (iOS AllRedditsView.swift:153-159).
    val symptoms = if (kind == ResourceKind.REDDIT) symptomInfos?.symptomInfos.orEmpty() else emptyMap()
    val options = tabs.map { LibraryFilterOption(it.name, it.dateText) } +
        if (symptoms.isNotEmpty()) listOf(LibraryFilterOption("Symptoms", null)) else emptyList()

    val defaultIndex = remember(tabs) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabs) { mutableStateOf(defaultIndex) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // In-app viewers — YouTube links play in the embedded IFrame player, Reddit
    // threads (and anything else) open in the in-app web viewer.
    var webUrl by remember { mutableStateOf<String?>(null) }
    var ytVideoId by remember { mutableStateOf<String?>(null) }
    var redditUrl by remember { mutableStateOf<String?>(null) }

    fun open(url: String) {
        val yt = org.clear30.views.components.youTubeId(url)
        when {
            url.contains("reddit.com") && url.contains("comments") -> redditUrl = url
            yt != null -> ytVideoId = yt
            else -> webUrl = url
        }
    }

    // The sections to display for the current filter, plus the tab's next-unlock
    // date (null on the Symptoms filter, hiding the banner — matches iOS).
    val symptomsSelected = selected >= tabs.size
    val sections: List<ResourceDisplaySection>
    val nextUnlockDate: Instant?
    if (symptomsSelected) {
        // Per-symptom alphabetical sections (iOS getSectionedReddits(from: symptomInfos),
        // AllRedditsView.swift:223-233): keys sorted, resources sorted by title.
        sections = symptoms.keys.sorted().map { key ->
            val symptom = symptoms.getValue(key)
            ResourceDisplaySection(
                title = key,
                gradient = symptom.getGradient(),
                resources = symptom.reddits.map { ProgramResource(title = it.key, url = it.value) }.sortedBy { it.title },
            )
        }.filter { it.resources.isNotEmpty() }
        nextUnlockDate = null
    } else {
        val tab = tabs.getOrNull(selected) ?: tabs.firstOrNull()
        sections = tab?.sections.orEmpty().map { section ->
            ResourceDisplaySection(
                title = section.stage?.title?.takeIf { it.isNotBlank() } ?: "Featured",
                gradient = section.stage?.gradient ?: brandGradient,
                resources = section.items,
            )
        }
        nextUnlockDate = tab?.nextUnlockDate
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(kind.title)
            Spacer(Modifier.weight(1f))
            // Filter button — only when there's more than one option (iOS
            // AllRedditsView.swift:48-55 / AllYouTubesView.swift:48-55).
            if (options.size > 1) {
                LibraryFilterButton { showFilterSheet = true }
            }
        }

        if (showFilterSheet) {
            LibraryFilterSheet(
                options = options,
                selected = selected,
                onSelect = { selected = it },
                onDismiss = { showFilterSheet = false },
            )
        }

        if (sections.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(kind.emptyMsg, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            return
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            // "More Reddit threads / YouTube videos in N days" (iOS
            // AllRedditsView.swift:63 / AllYouTubesView.swift:63).
            MoreContentBanner(nextUnlockDate, kind.contentType)
            sections.forEach { section ->
                SectionCard(section.title, section.gradient)
                // 2-column grid: chunk into pairs, one Row each.
                section.resources.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        pair.forEach { res ->
                            ResourceGridCell(kind, res, Modifier.weight(1f)) { open(res.url) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
        }
    }

    // In-app overlays (Reddit thread / YouTube video) — no leaving the app.
    webUrl?.let { u -> org.clear30.views.components.WebViewDialog(u, onDismiss = { webUrl = null }) }
    ytVideoId?.let { id -> org.clear30.views.components.YouTubeDialog(id, onDismiss = { ytVideoId = null }) }
    redditUrl?.let { u -> org.clear30.views.components.RedditDialog(u, onDismiss = { redditUrl = null }) }
}

/** Colored section header card (iOS `ProgramMessageSectionCard` — stage OR symptom). */
@Composable
private fun SectionCard(title: String, gradient: Brush) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = gradient) {
        SmallText(title, color = Color.White)
    }
}

/** One grid cell — a RedditCard or YouTubeCard. */
@Composable
private fun ResourceGridCell(kind: ResourceKind, res: ProgramResource, modifier: Modifier, onClick: () -> Unit) {
    when (kind) {
        ResourceKind.REDDIT -> RedditCard(res, modifier, onClick)
        ResourceKind.YOUTUBE -> YouTubeCard(res, modifier, onClick)
    }
}

/** iOS `RedditCard` — brand glyph + r/subreddit subtitle, then the thread title. */
@Composable
private fun RedditCard(res: ProgramResource, modifier: Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.fillMaxHeight().pressScale(onClick = onClick)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Icon(sfSymbol("text.bubble.fill"), contentDescription = null, tint = Clear30Colors.reddit1, modifier = Modifier.height(18.dp))
                extractSubreddit(res.url)?.let { TinyText(it, color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1) }
            }
            SmallText(res.title, maxLines = 4)
        }
    }
}

/** iOS `YouTubeCard` — 16:9 thumbnail, then brand glyph + title. */
@Composable
private fun YouTubeCard(res: ProgramResource, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.opacityGrayFlattened).pressScale(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Clear30Colors.opacityGray)) {
            youTubeThumbnailUrl(res.url)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)) {
                Icon(sfSymbol("play.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.height(16.dp))
            }
        }
        Row(
            Modifier.padding(Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            Icon(sfSymbol("play.rectangle.fill"), contentDescription = null, tint = Clear30Colors.youTube1, modifier = Modifier.height(16.dp))
            SmallText(res.title, maxLines = 3)
        }
    }
}

// extractSubreddit + youTubeThumbnailUrl are shared helpers defined in LibraryRails.kt
// (same package) — reused here rather than redefined.
