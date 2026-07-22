package org.clear30.views.existinguser.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Instant
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.Stage
import org.clear30.data.model.unlocked
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.existinguser.profile.relativeFutureString
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import androidx.compose.ui.unit.dp

/**
 * Shared "library" layout primitives — the iOS Support library pattern used by the
 * Reddit / YouTube / Meditations / Messages / Prompts screens: content filtered by
 * program break (iOS `BreakFilterOption`, chosen through [LibraryFilterSheet]),
 * then **sectioned by stage** (iOS `ProgramMessageSectionCard`).
 *
 * [buildLibraryTabs] does the break→stage bucketing once; each screen supplies an
 * [extract] that pulls its item type (a meditation, a message, a resource) out of a
 * stage's unlocked messages.
 */
internal data class LibrarySectionData<T>(val stage: Stage?, val items: List<T>)
internal data class LibraryTabData<T>(
    val name: String,
    /** Break start month shown right-aligned in the filter sheet (iOS `BreakFilterOption.dateText`). */
    val dateText: String?,
    val sections: List<LibrarySectionData<T>>,
    /** Earliest still-locked unlock date for this tab's content type (iOS `nextUnlockDate`). */
    val nextUnlockDate: Instant?,
)

internal fun <T> buildLibraryTabs(
    program: Program,
    extract: (unlockedMessages: List<ProgramMessage>) -> List<T>,
): List<LibraryTabData<T>> {
    fun sectionsFor(entries: List<Map.Entry<PlainDate, ContentInfo>>): List<LibrarySectionData<T>> {
        // Group by stage, NEWEST FIRST — iOS renders both the section loop and
        // the items inside each section `.reversed()` (AllMessagesView.swift:139,
        // W18), so the latest stage and latest content lead.
        val grouped = LinkedHashMap<String, MutableList<ContentInfo>>()
        entries.sortedByDescending { it.key }.forEach { e ->
            grouped.getOrPut(e.value.stage?.title ?: "") { mutableListOf() }.add(e.value)
        }
        return grouped.map { (_, group) ->
            val msgs = group.flatMap { it.messages }.unlocked
            LibrarySectionData(group.firstNotNullOfOrNull { it.stage }, extract(msgs))
        }.filter { it.items.isNotEmpty() }
    }

    // Earliest locked message that would yield items of this library's type
    // (iOS `earliestUnlock`, e.g. AllRedditsView.swift:178-180).
    fun nextUnlockIn(entries: List<Map.Entry<PlainDate, ContentInfo>>): Instant? =
        entries.flatMap { it.value.messages }
            .filter { !it.unlocked && extract(listOf(it)).isNotEmpty() }
            .minOfOrNull { it.unlockOn }

    // Newest break first (iOS AllMessagesView.swift:222-229 `.reversed()`), and
    // the HALF-OPEN [start, end) window — endDate is the first day OUT of the
    // break; `<=` double-listed the main break's Day-0 lesson in the
    // Preparation tab and leaked the first Life topic into the Clear30 tab.
    val tabs = program.breaks.sortedByDescending { it.startDate }.mapNotNull { br ->
        val lo = PlainDate.from(br.startDate)
        val hi = PlainDate.from(br.endDate)
        val entries = program.contentInfo.entries.filter { it.key >= lo && it.key < hi }
        val sections = sectionsFor(entries)
        if (sections.isEmpty()) null
        else LibraryTabData(br.name, monthName(lo.month), sections, nextUnlockIn(entries))
    }.toMutableList()

    // "Better Life Program" — the core (outside-any-break) content, inserted first
    // while the user is IN the core program, else appended (iOS
    // AllMessagesView.swift:242-249).
    val coreEntries = program.getCoreContentInfo().entries.toList()
    val coreSections = sectionsFor(coreEntries)
    if (coreSections.isNotEmpty()) {
        val core = LibraryTabData("Better Life Program", null, coreSections, nextUnlockIn(coreEntries))
        if (program.inCoreProgram) tabs.add(0, core) else tabs.add(core)
    }
    return tabs
}

/** Default tab index = the current break's tab, else the first (iOS selects `filterOptions[0]`). */
internal fun <T> defaultLibraryTab(program: Program, tabs: List<LibraryTabData<T>>): Int {
    val currentName = program.getBreak(org.clear30.util.now())?.name
    // Tabs are newest-first, so the most-recent fallback is index 0.
    return tabs.indexOfFirst { it.name == currentName }.takeIf { it >= 0 } ?: 0
}

private fun monthName(month: Int): String = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
).getOrElse(month - 1) { "" }

// MARK: - Filter sheet (iOS FilterListSheet, MessageFilterOptions.swift:21-62)

/** One row of the filter sheet (iOS `BreakFilterOption` display data). */
internal data class LibraryFilterOption(val name: String, val dateText: String?)

/**
 * The header filter button (iOS `line.3.horizontal.decrease.circle.fill`
 * IconButton) — callers show it only when there is more than one filter option.
 */
@Composable
internal fun LibraryFilterButton(onClick: () -> Unit) {
    IconButton(
        icon = "line.3.horizontal.decrease.circle.fill",
        height = 23.dp,
        tint = Clear30Colors.text.copy(alpha = 0.5f),
        onClick = onClick,
    )
}

/**
 * Bottom-sheet list of break filter options — iOS `FilterListSheet` presented at
 * the 0.4 detent: one card row per option, the ACTIVE row filled with the Clear30
 * gradient, break rows showing their start month right-aligned at half opacity.
 */
@Composable
internal fun LibraryFilterSheet(
    options: List<LibraryFilterOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Own padding (the iOS 0.4 detent height + headingTopPadding) instead of
    // the standard sheet content padding.
    Clear30Sheet(onDismiss = onDismiss, contentPadding = false) {
        Column(
            Modifier
                .fillMaxWidth()
                // iOS presents this sheet at the 0.4 fraction detent.
                .fillMaxHeight(0.4f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            options.forEachIndexed { i, option ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().pressScale {
                        onSelect(i)
                        onDismiss()
                    },
                    gradient = if (i == selected) Clear30Gradients.clear30 else null,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SmallText(option.name)
                        Spacer(Modifier.weight(1f))
                        option.dateText?.let {
                            SmallText(it, color = LocalContentColor.current.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
        }
    }
}

// MARK: - More-content banner (iOS MoreContentBanner, Cards.swift:1004-1029)

/**
 * "More {contentType} in N days" banner shown at the top of a library list while
 * this tab still has locked content ahead. Hidden when [nextUnlockDate] is null
 * (everything unlocked / symptoms filter).
 */
@Composable
internal fun MoreContentBanner(nextUnlockDate: Instant?, contentType: String, modifier: Modifier = Modifier) {
    if (nextUnlockDate == null) return
    Row(
        modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .cardStyle(color = Clear30Colors.opacityGray, shadowColor = Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Icon(
            sfSymbol("clock.fill"),
            contentDescription = null,
            tint = Clear30Colors.text,
            modifier = Modifier.size(12.dp),
        )
        SmallText("More $contentType ${relativeFutureString(nextUnlockDate)}", color = Clear30Colors.text)
    }
}

/** Colored stage header card (iOS `ProgramMessageSectionCard`). */
@Composable
internal fun StageHeaderCard(stage: Stage?, fallbackGradient: Brush) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = stage?.gradient ?: fallbackGradient) {
        SmallText(stage?.title?.takeIf { it.isNotBlank() } ?: "Featured", color = Color.White)
    }
}
