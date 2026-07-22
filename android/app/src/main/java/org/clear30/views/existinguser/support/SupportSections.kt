package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.SymptomInfo
import org.clear30.data.model.SymptomInfos
import org.clear30.data.model.unlocked
import org.clear30.util.firstEmoji
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Daily Topics — ported from Support2.swift `dailyMessagesSection`. The three most
 * recent unlocked program messages as tappable cards (each badged with the date it
 * appeared), followed by a "See all messages" link.
 */
@Composable
internal fun DailyTopicsSection(
    program: Program,
    onOpen: (ProgramMessage) -> Unit,
    onSeeAll: () -> Unit,
) {
    // iOS `recentUnlockedMessageGroups(limit: 3)` (ProgramContent.swift:134-147):
    // the 3 most-recent fully-unlocked DAY groups, newest-first. W78: a day's
    // second message (the assessment-response message) folds into the primary
    // card as a badge — the same same-day grouping the full Messages list uses
    // (W51) — instead of surfacing as its own Daily Topic row. Opening passes the
    // primary; SupportTab's OpenMessage route rebuilds the whole day group from it.
    val recentGroups = program.contentInfo.values
        .map { it.messages }
        .filter { group -> group.isNotEmpty() && group.all { it.unlocked } }
        .sortedByDescending { it.first().unlockOn }
        .take(3)
    if (recentGroups.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        recentGroups.forEach { group ->
            val primary = group.first()
            val sub = group.getOrNull(1)
            Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onOpen(primary) }, gradient = Clear30Gradients.clear30) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                        SmallText("${primary.topicEmoji ?: "💬"} ${primary.topicTitle}", color = Color.White, maxLines = 2)
                        // Same-day sub-message pill (iOS MessageCard badge).
                        sub?.let {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))
                                    .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
                            ) {
                                TinyText("${it.topicEmoji ?: "💬"} ${it.topicTitle}", color = Color.White, maxLines = 1)
                            }
                        }
                    }
                    // Day-N + progress pill matching the full message list (H37),
                    // white-on-gradient variant for this clear30 card.
                    val day = program.getBreak(primary.unlockOn)?.let { "Day ${it.getBreakDay(primary.unlockOn)}" }
                        ?: primary.unlockOn.let { org.clear30.data.model.PlainDate.from(it) }.let { "${it.month}/${it.day}" }
                    val progress = program.contentInfo[org.clear30.data.model.PlainDate.from(primary.unlockOn)]?.progress ?: 0.0
                    val complete = progress >= 1.0
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp))
                            .then(
                                if (complete) Modifier.background(Color.White)
                                else Modifier.progressRing(SolidColor(Color.White), progress.toFloat()),
                            )
                            .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        TinyText(day, color = if (complete) Clear30Colors.text else Color.White, maxLines = 1)
                        Icon(
                            sfSymbol(if (complete) "checkmark" else "arrow.right"),
                            null,
                            tint = if (complete) Clear30Colors.text else Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        // See all messages — opens the full message library.
        Row(
            Modifier.fillMaxWidth().pressScale { onSeeAll() }.padding(vertical = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            TinyText("See all messages", color = Clear30Colors.text.copy(alpha = 0.5f))
            Icon(sfSymbol("chevron.right"), null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * Symptom support — ported from `SymptomCarouselView` / `SymptomCarouselCard`
 * (SymptomCardsView.swift). A horizontal carousel of full symptom cards: a glass
 * emoji circle cheated up from center, with the symptom name + an "N tips" pill
 * pinned bottom-left over the symptom's brand gradient. Tap opens the symptom
 * detail.
 *
 * Ordering mirrors iOS `updateSymptoms`: the symptoms the user selected during
 * the onboarding assessment come first, then Insomnia (the default lead), then
 * alphabetical — so the carousel "starts with insomnia and the onboarding".
 */
@Composable
internal fun SymptomSupportSection(infos: SymptomInfos, program: Program, onOpen: (String, SymptomInfo) -> Unit) {
    // The symptom option strings the user picked in the assessment "Symptoms"
    // question (e.g. "Insomnia", "Anxiety"), lower-cased for fuzzy matching
    // against the emoji-prefixed carousel keys ("😴 Insomnia").
    val selected = remember(program) {
        val resp = program.currentBreak?.getAssessmentResponse(AssessmentQuestionID.SYMPTOMS.raw)
        resp?.responses?.mapNotNull { resp.question.options.getOrNull(it) }
            ?.map { it.trim().lowercase() }?.toSet().orEmpty()
    }
    fun normalized(key: String): String =
        (key.firstEmoji?.let { key.removePrefix(it) } ?: key).trim().lowercase()
    fun isSelected(key: String): Boolean {
        val n = normalized(key)
        return selected.any { it == n || it.contains(n) || n.contains(it) }
    }
    val entries = infos.symptomInfos.entries.sortedWith(
        compareByDescending<Map.Entry<String, SymptomInfo>> { isSelected(it.key) }
            .thenByDescending { normalized(it.key).let { n -> n.contains("insomnia") || n.contains("sleep") } }
            .thenBy { it.key },
    )
    if (entries.isEmpty()) return
    // iOS presents these through FeedView (horizontal paging, S13): one
    // full-width card per snapping page, neighbors at 0.9 scale / 0.5 opacity,
    // page dots below — the same treatment as the assessment testimonials rail.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { entries.size })
    var settledPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != settledPage) {
            settledPage = pagerState.currentPage
            org.clear30.views.theme.Haptics.lightImpact()
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 4)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) { page ->
            val offset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
            val (key, info) = entries[page]
            SymptomCard(
                key, info,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.1f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.5f * offset
                    },
            ) { onOpen(key, info) }
        }
        if (entries.size > 1) {
            SymptomPageDots(
                count = entries.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Dimens.cardSpacing),
            )
        }
    }
}

/** FeedView `pageDots` — small dots in a translucent capsule, current darker. */
@Composable
private fun SymptomPageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) Clear30Colors.text.copy(alpha = 0.75f)
                        else Clear30Colors.text.copy(alpha = 0.25f),
                    ),
            )
        }
    }
}

@Composable
private fun SymptomCard(key: String, info: SymptomInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val emoji = key.firstEmoji ?: "🩺"
    val title = (key.firstEmoji?.let { key.removePrefix(it) } ?: key).trim().replaceFirstChar { it.uppercase() }
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(info.getGradient())
            .pressScale { onClick() },
    ) {
        // Glass emoji circle — cheated up from center (iOS offset y -16).
        Box(
            Modifier.align(Alignment.Center).offset(y = (-16).dp)
                .size(74.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Heading1(emoji)
        }
        // Title + "N tips" pill pinned bottom-left.
        Column(
            Modifier.align(Alignment.BottomStart).padding(Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            SmallText(title, color = Color.White, maxLines = 1)
            if (info.tips.isNotEmpty()) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
                ) {
                    TinyText("${info.tips.size} tips", color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

private val DAILY_TOPIC_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Jun 25" — the date a topic appeared (its unlock day), in the device timezone. */
private fun Instant.shortDate(): String {
    val date = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${DAILY_TOPIC_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}
