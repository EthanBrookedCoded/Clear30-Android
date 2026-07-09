package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val recent = program.contentInfo.values.flatMap { it.messages }.unlocked.reversed().take(3)
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        recent.forEach { msg ->
            Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onOpen(msg) }, gradient = Clear30Gradients.clear30) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    SmallText("${msg.topicEmoji ?: "💬"} ${msg.topicTitle}", color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 4),
                    ) {
                        TinyText(msg.unlockOn.shortDate(), color = Color.White, maxLines = 1)
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Dimens.cardSpacing / 4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        entries.forEach { (key, info) -> SymptomCard(key, info) { onOpen(key, info) } }
    }
}

@Composable
private fun SymptomCard(key: String, info: SymptomInfo, onClick: () -> Unit) {
    val emoji = key.firstEmoji ?: "🩺"
    val title = (key.firstEmoji?.let { key.removePrefix(it) } ?: key).trim().replaceFirstChar { it.uppercase() }
    Box(
        Modifier.size(width = 200.dp, height = 210.dp)
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
