package org.clear30.views.existinguser.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.SymptomInfo
import org.clear30.data.model.SymptomTip
import org.clear30.data.model.SymptomTipSection
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * SymptomDetailScreen — ported from iOS `SymptomInfoView`. Three sections:
 *  • **Tips** — gradient cards (tip's own colours) showing the title + "Click to
 *    see more"; tapping expands the card to a full-screen reader with each
 *    section's heading/content and an optional example toggle.
 *  • **Real stories** — a 2-column grid of Reddit (r/leaves) threads, "Show more".
 *  • **Ask Claire** — a 2-column grid of Claire prompt cards; tapping "Start chat"
 *    opens Claire seeded with that prompt.
 */
@Composable
fun SymptomDetailScreen(
    key: String,
    symptom: SymptomInfo,
    userInfo: UserInfo,
    onBack: () -> Unit,
    onAskClaire: (String) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    var expandedTip by remember { mutableStateOf<SymptomTip?>(null) }
    var showAllReddits by remember { mutableStateOf(false) }
    var showAllPrompts by remember { mutableStateOf(false) }
    // Reddit "Real stories" open in the native in-app viewer, like every other
    // reddit path (it falls back to the web view itself if the fetch fails).
    var redditUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key) {
        Logger.logEvent(userInfo.loggingID, LogEventType.loggedSymptom, mapOf(LogEventExtraDataType.SYMPTOM to key))
    }

    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                IconButton("chevron.backward", onClick = onBack)
                Heading1(key.replaceFirstChar { it.uppercase() })
            }

            if (symptom.tips.isNotEmpty()) {
                Heading2("Tips")
                symptom.tips.forEach { tip -> TipCardCollapsed(tip) { expandedTip = tip } }
            }

            if (symptom.reddits.isNotEmpty()) {
                Heading2("Real stories")
                val entries = symptom.reddits.entries.toList()
                TwoColumnGrid(if (showAllReddits) entries else entries.take(2)) { e ->
                    RedditMini(e.key, e.value) {
                        Logger.logEvent(userInfo.loggingID, LogEventType.openedRedditThread, mapOf(LogEventExtraDataType.URL to e.value))
                        redditUrl = e.value
                    }
                }
                if (entries.size > 2) ShowMoreRow(showAllReddits) { showAllReddits = !showAllReddits }
            }

            if (symptom.prompts.isNotEmpty()) {
                Heading2("Ask Claire")
                val entries = symptom.prompts.entries.toList()
                TwoColumnGrid(if (showAllPrompts) entries else entries.take(2)) { e ->
                    ClaireMini(e.key) { onAskClaire(e.value) }
                }
                if (entries.size > 2) ShowMoreRow(showAllPrompts) { showAllPrompts = !showAllPrompts }
            }

            Spacer(Modifier.size(Dimens.cardSpacing * 2))
        }

        expandedTip?.let { tip ->
            BackHandler { expandedTip = null }
            TipExpandedOverlay(tip) { expandedTip = null }
        }

        redditUrl?.let { u ->
            org.clear30.views.components.RedditDialog(u, onDismiss = { redditUrl = null })
        }
    }
}

private fun tipGradient(tip: SymptomTip): Brush = Clear30Gradients.linear(
    listOf(colorFromHex(tip.color1), colorFromHex(tip.color2)),
    Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing,
)

@Composable
private fun TipCardCollapsed(tip: SymptomTip, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().cardStyle(gradient = tipGradient(tip)).pressScale { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.Top) {
                Heading3(tip.title, color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.OpenInFull, contentDescription = "Expand", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
            }
            SmallText("Click to see more", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TipExpandedOverlay(tip: SymptomTip, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Absorb taps so tapping the content doesn't close it (scrim taps do).
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(tipGradient(tip)).pressScale { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol("xmark"), contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Box(Modifier.fillMaxWidth().cardStyle(gradient = tipGradient(tip))) {
                Heading2(tip.title, color = Color.White)
            }
            tip.sections.forEach { section -> TipSectionView(section) }
            Spacer(Modifier.size(Dimens.cardSpacing * 4))
        }
    }
}

@Composable
private fun TipSectionView(section: SymptomTipSection) {
    var showExample by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (showExample) 0f else 180f, label = "exChevron")
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Heading3(section.heading)
            SmallText(section.content, color = Clear30Colors.text.copy(alpha = 0.75f))
            section.example?.takeIf { it.isNotBlank() }?.let { ex ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Clear30Colors.opacityGray)
                        .clickable { showExample = !showExample }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(sfSymbol("chevron.down"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(10.dp).rotate(chevron))
                    TinyText(if (showExample) "Hide example" else "Show example", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                if (showExample) TinyText(ex, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun RedditMini(title: String, url: String, onClick: () -> Unit) {
    val sub = url.substringAfter("/r/", "").substringBefore("/").let { if (it.isNotBlank()) "r/$it" else "Reddit" }
    // fillMaxSize: stretch to the row height set by TwoColumnGrid so both cards match.
    Clear30Card(modifier = Modifier.fillMaxSize().pressScale { onClick() }) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Clear30Colors.reddit1), contentAlignment = Alignment.Center) {
                    Icon(sfSymbol("person.2.fill"), null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
                TinyText(sub, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            SmallText(title, maxLines = 4)
        }
    }
}

@Composable
private fun ClaireMini(title: String, onStartChat: () -> Unit) {
    // fillMaxSize: stretch to the row height set by TwoColumnGrid so both cards match.
    Clear30Card(modifier = Modifier.fillMaxSize().pressScale { onStartChat() }) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                Icon(sfSymbol("sparkles"), null, tint = Clear30Colors.claire1, modifier = Modifier.size(14.dp))
                TinyText("Claire", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            SmallText(title, maxLines = 4)
            // Pin "Start chat" to the bottom of the (possibly stretched) card —
            // iOS `Spacer()` above the pill in `PromptCard`.
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)
                    .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TinyText("Start chat", color = Clear30Colors.text)
                Icon(sfSymbol("arrow.right"), null, tint = Clear30Colors.text, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun ShowMoreRow(showingAll: Boolean, onToggle: () -> Unit) {
    val chevron by animateFloatAsState(if (showingAll) 180f else 0f, label = "showMoreChevron")
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinyText(if (showingAll) "Show less" else "Show more", color = Clear30Colors.text.copy(alpha = 0.5f))
        Spacer(Modifier.size(4.dp))
        Icon(sfSymbol("chevron.down"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp).rotate(chevron))
    }
}

/**
 * Lays [items] into a 2-column grid (last odd cell left blank). The row is sized
 * to its tallest cell (`IntrinsicSize.Min`) and both cells fill that height, so
 * the two cards in a row always match — iOS `GridView(columns: 2)`, same
 * technique as the school/library grids.
 */
@Composable
private fun <T> TwoColumnGrid(items: List<T>, item: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                rowItems.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { item(it) } }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
