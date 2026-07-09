package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.Stage
import org.clear30.data.model.unlocked
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
internal enum class ResourceKind(val title: String, val emptyMsg: String) {
    REDDIT("Reddit Threads", "Reddit threads will appear here as your content unlocks."),
    YOUTUBE("YouTube", "YouTube videos will appear here as your content unlocks.");
}

/** A stage section within a tab — its stage header + the resources beneath it. */
private data class ResourceSection(val stage: Stage?, val resources: List<ProgramResource>)

/** A break-level tab and the stage sections it contains. */
private data class ResourceTab(val name: String, val sections: List<ResourceSection>)

/**
 * ResourcesScreen — ported to match iOS AllRedditsView / AllYouTubesView. Content is
 * filtered by program break (the **tabs**, iOS's BreakFilterOption sheet), then
 * **sectioned by stage** (a colored stage header card, iOS `ProgramMessageSectionCard`),
 * and laid out as a **2-column grid** of branded cards (iOS `RedditCard` / `YouTubeCard`).
 * Each card opens its link via the system VIEW intent.
 */
@Composable
internal fun ResourcesScreen(program: Program, kind: ResourceKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val brandGradient = if (kind == ResourceKind.REDDIT) Clear30Gradients.reddit else Clear30Gradients.youtube

    // Build the break tabs + their stage sections from the user's unlocked content.
    val tabs = remember(program.contentInfo.size, kind) { buildResourceTabs(program, kind) }
    // Default to the current break's tab (iOS opens on the active break), else the last.
    val defaultIndex = remember(tabs) {
        val currentName = program.getBreak(org.clear30.util.now())?.name
        tabs.indexOfFirst { it.name == currentName }.takeIf { it >= 0 } ?: tabs.lastIndex.coerceAtLeast(0)
    }
    var selected by remember(tabs) { mutableStateOf(defaultIndex) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(kind.title)
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(kind.emptyMsg, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            return
        }

        // Tab row (break filter) — only shown when there's more than one (iOS shows
        // the filter affordance only when filterOptions.count > 1).
        if (tabs.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                tabs.forEachIndexed { i, tab ->
                    TabPill(tab.name, selected = i == selected, gradient = brandGradient) { selected = i }
                }
            }
        }

        val tab = tabs.getOrNull(selected) ?: tabs.first()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            tab.sections.forEach { section ->
                StageSectionCard(section.stage, brandGradient)
                // 2-column grid: chunk into pairs, one Row each.
                section.resources.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        ResourceGridCell(kind, pair[0], Modifier.weight(1f)) { open(pair[0].url) }
                        if (pair.size > 1) {
                            ResourceGridCell(kind, pair[1], Modifier.weight(1f)) { open(pair[1].url) }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
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

/** Build break-filtered, stage-sectioned resource tabs (iOS getSectionedReddits). */
private fun buildResourceTabs(program: Program, kind: ResourceKind): List<ResourceTab> {
    fun resourcesIn(entries: List<ContentInfo>): List<ProgramResource> =
        entries.flatMap { it.messages }.unlocked.flatMap { it.allResources }
            .filter { if (kind == ResourceKind.REDDIT) it.isReddit else it.isYouTube }
            .distinctBy { it.url }

    fun sectionsFor(entries: List<Map.Entry<PlainDate, ContentInfo>>): List<ResourceSection> {
        // Preserve stage order by first-seen date.
        val grouped = LinkedHashMap<String, MutableList<ContentInfo>>()
        entries.sortedBy { it.key }.forEach { e ->
            grouped.getOrPut(e.value.stage?.title ?: "") { mutableListOf() }.add(e.value)
        }
        return grouped.map { (_, group) ->
            ResourceSection(group.firstNotNullOfOrNull { it.stage }, resourcesIn(group))
        }.filter { it.resources.isNotEmpty() }
    }

    val byBreak = program.breaks.sortedBy { it.startDate }.mapNotNull { br ->
        val lo = PlainDate.from(br.startDate)
        val hi = PlainDate.from(br.endDate)
        val entries = program.contentInfo.entries.filter { it.key >= lo && it.key <= hi }
        val sections = sectionsFor(entries)
        if (sections.isEmpty()) null else ResourceTab(br.name, sections)
    }
    if (byBreak.isNotEmpty()) return byBreak

    // Fallback: no break-scoped content — show everything in one tab, stage-sectioned.
    val all = sectionsFor(program.contentInfo.entries.toList())
    return if (all.isEmpty()) emptyList() else listOf(ResourceTab("Your Program", all))
}

/** A selectable break tab pill (iOS BreakFilterOption). */
@Composable
private fun TabPill(name: String, selected: Boolean, gradient: Brush, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(if (selected) Modifier.background(gradient) else Modifier.background(Clear30Colors.opacityGray))
            .pressScale(onClick = onClick)
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
    ) {
        SmallText(name, color = if (selected) Color.White else Clear30Colors.text)
    }
}

/** Colored stage header card (iOS `ProgramMessageSectionCard`). */
@Composable
private fun StageSectionCard(stage: Stage?, fallbackGradient: Brush) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = stage?.gradient ?: fallbackGradient) {
        SmallText(stage?.title?.takeIf { it.isNotBlank() } ?: "Featured", color = Color.White)
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
