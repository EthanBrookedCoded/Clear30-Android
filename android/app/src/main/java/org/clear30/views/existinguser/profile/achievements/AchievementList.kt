package org.clear30.views.existinguser.profile.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.clear30.data.model.AchievementDefinition
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * AchievementList — the full Achievements screen (port of AchievementList.swift).
 * Definitions grouped by rarity (sorted by displayOrder), each group a section
 * header (rarity badge + "earned of total") over a 2-column grid. Cards render in
 * their state — earned-viewed / earned-new / locked — earned first. Unearned
 * Legendary achievements are hidden (only their placeholders would otherwise leak
 * the surprise), and Legendary sections drop the "X of Y" count.
 */
@Composable
fun AchievementList(
    defs: List<AchievementDefinition>,
    earned: Map<String, Boolean>,   // key -> isVisited (absent == not earned)
    onBack: () -> Unit,
    onOpen: (AchievementDefinition) -> Unit,
) {
    val scroll = rememberScrollState()

    // Show every rarity section — including Legendary — so the gold/orange
    // Legendary group is always visible (its cards stay locked until earned).
    val visible = defs.sortedBy { it.computedRarity.displayOrder }

    val groups = visible.groupBy { it.computedRarity.id }
        .entries.sortedBy { it.value.first().computedRarity.displayOrder }

    Column(
        Modifier.fillMaxSize()
            .background(Clear30Colors.background)
            .padding(top = Dimens.headingTopPadding, start = Dimens.horizontalPadding, end = Dimens.horizontalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton("chevron.backward", onClick = onBack)
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            Heading1("Achievements")
        }

        if (defs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SmallText(
                    "Achievements are still syncing — make sure you're signed in and connected, then check back.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return@Column
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll)
                .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding * 4),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            groups.forEach { (_, group) ->
                val rarity = group.first().computedRarity
                val allOfRarity = defs.filter { it.computedRarity.id == rarity.id }
                RaritySection(
                    rarity = rarity,
                    achievements = group,
                    earnedOfRarity = allOfRarity.count { it.key in earned },
                    totalOfRarity = allOfRarity.size,
                    earned = earned,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun RaritySection(
    rarity: org.clear30.data.model.AchievementRarity,
    achievements: List<AchievementDefinition>,
    earnedOfRarity: Int,
    totalOfRarity: Int,
    earned: Map<String, Boolean>,
    onOpen: (AchievementDefinition) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AchievementBadge(rarity = rarity, gradientBackground = true)
            Spacer(Modifier.weight(1f))
            SmallText("$earnedOfRarity of $totalOfRarity", color = Clear30Colors.text.copy(alpha = 0.25f))
        }

        // Earned first, then by display order — same ordering as iOS.
        val sorted = achievements.sortedWith(
            compareByDescending<AchievementDefinition> { it.key in earned }.thenBy { it.displayOrder },
        )
        sorted.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                rowItems.forEach { def ->
                    Box(Modifier.weight(1f)) { CardFor(def, earned, onOpen) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Pick the card state for [def]: earned-viewed / earned-new / locked. */
@Composable
private fun CardFor(def: AchievementDefinition, earned: Map<String, Boolean>, onOpen: (AchievementDefinition) -> Unit) {
    when {
        def.key !in earned ->
            AchievementPlaceholderCard(def, modifier = Modifier.fillMaxWidth())
        earned[def.key] == true ->
            AchievementCard(def, modifier = Modifier.fillMaxWidth().pressScale { onOpen(def) })
        else ->
            AchievementNewCard(def, modifier = Modifier.fillMaxWidth().pressScale { onOpen(def) })
    }
}
