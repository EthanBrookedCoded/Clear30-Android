package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.Stage
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * Shared "library" layout primitives — the iOS Support library pattern used by the
 * Reddit / YouTube / Meditations / Messages screens: content filtered by program
 * break (the **tabs**, iOS BreakFilterOption), then **sectioned by stage**
 * (iOS `ProgramMessageSectionCard`).
 *
 * [buildLibraryTabs] does the break→stage bucketing once; each screen supplies an
 * [extract] that pulls its item type (a meditation, a message, a resource) out of a
 * stage's unlocked messages.
 */
internal data class LibrarySectionData<T>(val stage: Stage?, val items: List<T>)
internal data class LibraryTabData<T>(val name: String, val sections: List<LibrarySectionData<T>>)

internal fun <T> buildLibraryTabs(
    program: Program,
    extract: (unlockedMessages: List<ProgramMessage>) -> List<T>,
): List<LibraryTabData<T>> {
    fun sectionsFor(entries: List<Map.Entry<PlainDate, ContentInfo>>): List<LibrarySectionData<T>> {
        // Group by stage, preserving stage order by first-seen date.
        val grouped = LinkedHashMap<String, MutableList<ContentInfo>>()
        entries.sortedBy { it.key }.forEach { e ->
            grouped.getOrPut(e.value.stage?.title ?: "") { mutableListOf() }.add(e.value)
        }
        return grouped.map { (_, group) ->
            val msgs = group.flatMap { it.messages }.unlocked
            LibrarySectionData(group.firstNotNullOfOrNull { it.stage }, extract(msgs))
        }.filter { it.items.isNotEmpty() }
    }

    val byBreak = program.breaks.sortedBy { it.startDate }.mapNotNull { br ->
        val lo = PlainDate.from(br.startDate)
        val hi = PlainDate.from(br.endDate)
        val entries = program.contentInfo.entries.filter { it.key >= lo && it.key <= hi }
        val sections = sectionsFor(entries)
        if (sections.isEmpty()) null else LibraryTabData(br.name, sections)
    }
    if (byBreak.isNotEmpty()) return byBreak

    val all = sectionsFor(program.contentInfo.entries.toList())
    return if (all.isEmpty()) emptyList() else listOf(LibraryTabData("Your Program", all))
}

/** Default tab index = the current break's tab, else the most recent (iOS opens on the active break). */
internal fun <T> defaultLibraryTab(program: Program, tabs: List<LibraryTabData<T>>): Int {
    val currentName = program.getBreak(org.clear30.util.now())?.name
    return tabs.indexOfFirst { it.name == currentName }.takeIf { it >= 0 } ?: tabs.lastIndex.coerceAtLeast(0)
}

/** Horizontal break-tab pill row (iOS BreakFilterOption). */
@Composable
internal fun LibraryTabRow(names: List<String>, selected: Int, gradient: Brush, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Dimens.cardSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        names.forEachIndexed { i, name ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .then(if (i == selected) Modifier.background(gradient) else Modifier.background(Clear30Colors.opacityGray))
                    .pressScale(onClick = { onSelect(i) })
                    .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
            ) {
                SmallText(name, color = if (i == selected) Color.White else Clear30Colors.text)
            }
        }
    }
}

/** Colored stage header card (iOS `ProgramMessageSectionCard`). */
@Composable
internal fun StageHeaderCard(stage: Stage?, fallbackGradient: Brush) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = stage?.gradient ?: fallbackGradient) {
        SmallText(stage?.title?.takeIf { it.isNotBlank() } ?: "Featured", color = Color.White)
    }
}
