package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.TinyText
import org.clear30.views.existinguser.today.MessageDetail
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * MessagesLibraryScreen — ported from the iOS AllMessagesView. The unlocked program
 * messages, filtered by program break (the **tabs**) and **sectioned by stage**, the
 * same treatment as the Reddit/YouTube/Meditations libraries. Tapping a row opens the
 * MessageDetail view; the star toggles `favorited` and persists locally.
 */
@Composable
fun MessagesLibraryScreen(
    program: Program,
    userInfo: UserInfo,
    journalEntries: org.clear30.data.model.JournalEntries? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Rev counter forces the tabs to recompute after a viewer close (progress
    // rings / day pills read in-place-mutated content).
    var rev by remember { mutableIntStateOf(0) }
    // The opened day GROUP (iOS navigates with the whole [ProgramMessage] group).
    var open by remember { mutableStateOf<List<ProgramMessage>?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedContent)
    }

    open?.let { group ->
        // System back must ALSO bump rev — the lists are remember(rev)-keyed
        // over in-place-mutated models, so skipping it left a just-unfavorited
        // message sitting in the Favorites list.
        val close: () -> Unit = { open = null; rev++ }
        androidx.activity.compose.BackHandler { close() }
        MessageDetail(program, group, userInfo, journalEntries, onBack = close)
        return
    }

    val tabs = remember(program.contentInfo.size, rev) { buildLibraryTabs(program) { it } }
    // Key the selection on the tab NAMES, not the list instance — rev bumps
    // rebuild `tabs` (same names) on every viewer close, and an instance key
    // bounced the user back to the default tab each time.
    val tabNames = tabs.map { it.name }
    val defaultIndex = remember(tabNames) { defaultLibraryTab(program, tabs) }
    var selected by remember(tabNames) { mutableStateOf(defaultIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Messages")
            Spacer(Modifier.weight(1f))
            // Break filter (iOS AllMessagesView.swift:108-115 — shown only when
            // there's more than one filter option). W77: the favorites heart was
            // removed (follows W35's in-viewer heart removal), so only the filter
            // button remains in the header.
            if (tabs.size > 1) {
                LibraryFilterButton { showFilterSheet = true }
            }
        }

        if (tabs.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    "Your daily content will appear here as you unlock it.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return
        }

        if (showFilterSheet) {
            LibraryFilterSheet(
                options = tabs.map { LibraryFilterOption(it.name, it.dateText) },
                selected = selected,
                onSelect = { selected = it },
                onDismiss = { showFilterSheet = false },
            )
        }
        val tab = tabs.getOrNull(selected) ?: tabs.first()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            // "More messages in N days" (iOS AllMessagesView.swift:132).
            MoreContentBanner(tab.nextUnlockDate, "messages")
            tab.sections.forEach { section ->
                val sectionGradient = section.stage?.gradient ?: Clear30Gradients.clear30
                StageHeaderCard(section.stage, Clear30Gradients.clear30)
                // Group same-day messages (iOS AllMessagesView: one card per DAY;
                // a second message that day — the assessment-response message —
                // becomes a badge on the card and its content follows the main
                // message inside the viewer).
                // W76: render day-groups NEWEST-first (iOS renders the group loop
                // `(0..<count).reversed()`, AllMessagesView.swift:148). `.unlocked`
                // sorts ascending (core-message-first within a day), so reversing
                // only the day order keeps the assessment message as the badge.
                val dayGroups = section.items
                    .groupBy { org.clear30.data.model.PlainDate.from(it.unlockOn) }
                    .values.reversed()
                dayGroups.forEach { group ->
                    val primary = group.first()
                    val sub = group.getOrNull(1)
                    MessageRow(
                        msg = primary,
                        program = program,
                        gradient = sectionGradient,
                        badge = sub?.let { "${it.topicEmoji ?: "💬"} ${it.topicTitle}" },
                        onOpen = { open = group },
                    )
                }
            }
        }
    }
}

/** iOS `MessageCard` (AllMessagesView.swift:361-521): "emoji title" left, and a
 *  Day-N pill right — gradient-filled + checkmark once the day's feed was
 *  completed; otherwise outlined with a partial PROGRESS RING (the stage-gradient
 *  outline drawn at 0.25 opacity, with the completed fraction re-stroked at full
 *  opacity — iOS `outlineTrim`, AllMessagesView.swift:470-485). */
@Composable
private fun MessageRow(
    msg: ProgramMessage,
    program: Program,
    gradient: Brush = Clear30Gradients.clear30,
    badge: String? = null,
    onOpen: () -> Unit,
) {
    val day = program.getBreak(msg.unlockOn)?.let { "Day ${it.getBreakDay(msg.unlockOn)}" }
        ?: "${msg.unlockOn.let { org.clear30.data.model.PlainDate.from(it) }.month}/${org.clear30.data.model.PlainDate.from(msg.unlockOn).day}"
    val progress = program.contentInfo[org.clear30.data.model.PlainDate.from(msg.unlockOn)]?.progress ?: 0.0
    val complete = progress >= 1.0
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                SmallText("${msg.topicEmoji ?: "💬"} ${msg.topicTitle}")
                // Same-day sub-message pill (iOS MessageCard badge).
                badge?.let {
                    Row(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                            .background(Clear30Colors.opacityGray)
                            .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                    ) {
                        TinyText(it, color = Clear30Colors.text.copy(alpha = 0.75f))
                    }
                }
            }
            Row(
                Modifier
                    .padding(start = Dimens.cardSpacing / 2)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                    .then(
                        if (complete) {
                            // iOS completed pill: gradient fill + white outline @ 0.5
                            // (AllMessagesView.swift:459-468).
                            Modifier
                                .background(gradient)
                                .border(
                                    3.dp,
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(99.dp),
                                )
                        } else {
                            Modifier.progressRing(gradient, progress.toFloat())
                        },
                    )
                    .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText(day, color = if (complete) androidx.compose.ui.graphics.Color.White else Clear30Colors.text)
                Spacer(Modifier.width(Dimens.cardSpacing / 2))
                Icon(
                    sfSymbol(if (complete) "checkmark" else "arrow.right"),
                    contentDescription = null,
                    tint = if (complete) androidx.compose.ui.graphics.Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/**
 * The iOS MessageCard pill outline: the full stadium outline stroked with the
 * stage [gradient] at 0.25 opacity, then the leading [progress] fraction of the
 * SAME path re-stroked at full opacity (SwiftUI `.trim(from: 0, to: progress)`
 * via CardStyle `outlineTrim`).
 */
internal fun Modifier.progressRing(gradient: Brush, progress: Float): Modifier = drawBehind {
    // iOS outlineWidth: 3 (AllMessagesView.swift:473,481).
    val sw = 3.dp.toPx()
    val inset = sw / 2
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = inset,
                top = inset,
                right = size.width - inset,
                bottom = size.height - inset,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((size.height - sw) / 2),
            ),
        )
    }
    drawPath(path, brush = gradient, alpha = 0.25f, style = Stroke(sw))
    val trim = progress.coerceIn(0f, 1f)
    if (trim > 0f) {
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val segment = androidx.compose.ui.graphics.Path()
        measure.getSegment(0f, measure.length * trim, segment, true)
        drawPath(segment, brush = gradient, style = Stroke(sw, cap = StrokeCap.Round))
    }
}
