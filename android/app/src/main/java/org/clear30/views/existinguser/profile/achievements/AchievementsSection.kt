package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getAchievementDefinitions
import org.clear30.data.supabase.getAchievementRarities
import org.clear30.data.supabase.getAchievementStats
import org.clear30.data.supabase.getUserAchievements
import org.clear30.data.supabase.withRarityAndStats
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30FullScreenCover
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.existinguser.profile.achievements.AchievementIconCard
import org.clear30.views.existinguser.profile.achievements.AchievementList
import org.clear30.views.existinguser.profile.achievements.AchievementMoreCard
import org.clear30.views.existinguser.profile.achievements.AchievementNewIconCard
import org.clear30.views.existinguser.profile.achievements.AchievementReveal
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * AchievementsSection — the Profile "Achievements" mini-display (port of
 * AchievementMiniDisplay.swift): a card with a header chevron over a horizontal
 * row of earned tiles (new ones first, glowing), tailed by a "N+ More" card.
 * Tapping the card / header / More opens the full rarity-grouped
 * [AchievementList]; tapping a tile opens the [AchievementReveal] carousel.
 *
 * Definitions (joined with their rarity + stats) and the user's earned set are
 * fetched on first visit and cached via [Clear30Store] so the AchievementEngine
 * has criteria even if the user never opens this section. `isVisited` is tracked
 * locally — earned-but-unvisited tiles render as "New" until opened.
 */
@Composable
fun AchievementsSection(userInfo: UserInfo, program: org.clear30.data.model.Program) {
    val scope = rememberCoroutineScope()
    var defs by remember { mutableStateOf<List<AchievementDefinition>>(emptyList()) }
    var earned by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }  // key -> isVisited
    var loaded by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.viewedAchievementsList)
        // Refresh the cache from the backend (definitions + server-side earns,
        // preserving local visited/unsynced state), then retroactively evaluate
        // so anything already qualified is awarded right now — this covers
        // check-ins that ran while the definitions cache was still empty.
        org.clear30.data.supabase.refreshAchievementsCache(userInfo.userID)
        runCatching { org.clear30.data.AchievementEngine.evaluateNow(userInfo, program) }

        // Display straight from the (now-merged) cache — server AND local earns.
        val ad = Clear30Store.loadAchievementData()
        defs = ad.definitions
        earned = ad.earnedAchievements.associate { it.achievementKey to (it.isVisited == true) }
        loaded = true

        // Diagnostic: defs 0 ⇒ RLS/auth (not signed in) or network; earned 0
        // with defs > 0 ⇒ genuinely nothing earned yet.
        android.util.Log.i("Achievements", "defs=${defs.size} earned=${earned.size}")
    }

    fun markVisited(key: String) {
        if (earned[key] == true) return
        earned = earned + (key to true)
        scope.launch {
            val ad = Clear30Store.loadAchievementData()
            ad.earnedAchievements = ad.earnedAchievements.map {
                if (it.achievementKey == key) it.apply { isVisited = true } else it
            }.toMutableList()
            Clear30Store.save(ad)
        }
    }

    // Earned definitions ordered new-first, capped for the preview row.
    val earnedOrdered = defs.filter { it.key in earned }
        .sortedWith(compareByDescending<AchievementDefinition> { earned[it.key] == false }.thenBy { it.displayOrder })
    val preview = earnedOrdered.take(6)
    val moreCount = otherAchievementsCount(earnedOrdered.size, preview.size)

    // iOS only renders the mini display once something is earned
    // (`if achievementData.hasEarnedAchievements`) — no spinner / empty state.
    if (loaded && preview.isNotEmpty()) {
        MiniDisplayCard(
            preview = preview,
            moreCount = moreCount,
            isNew = { earned[it] == false },
            onOpenList = { showList = true },
            onOpenAchievement = { revealKey = it.key },
        )
    }

    // Full rarity-grouped list.
    if (showList) {
        Clear30FullScreenCover(onDismiss = { showList = false }) {
            AchievementList(
                defs = defs,
                earned = earned,
                onBack = { showList = false },
                onOpen = { revealKey = it.key },
            )
        }
    }

    // Reveal carousel over the earned achievements.
    revealKey?.let { key ->
        Clear30FullScreenCover(onDismiss = { revealKey = null }) {
            AchievementReveal(
                achievements = earnedOrdered,
                startKey = key,
                isVisited = { earned[it] == true },
                onVisited = { markVisited(it) },
                onClose = { revealKey = null },
            )
        }
    }
}

@Composable
private fun MiniDisplayCard(
    preview: List<AchievementDefinition>,
    moreCount: Int,
    isNew: (String) -> Boolean,
    onOpenList: () -> Unit,
    onOpenAchievement: (AchievementDefinition) -> Unit,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale(onClick = onOpenList)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallText("Achievements", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                Icon(
                    sfSymbol("chevron.right"),
                    contentDescription = "All achievements",
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.size(Dimens.cardSpacing))
            LazyRow(
                // Widen the clip + re-pad via contentPadding so the first/last
                // tiles' soft shadows aren't cut at the rail edges.
                modifier = Modifier.scrollShadowBleed(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.scrollShadowFix),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                items(preview, key = { it.key }) { def ->
                    val tile = Modifier.size(60.dp).pressScale { onOpenAchievement(def) }
                    if (isNew(def.key)) AchievementNewIconCard(def, tile) else AchievementIconCard(def, tile)
                }
                if (moreCount > 0) {
                    item {
                        AchievementMoreCard(moreCount, Modifier.size(60.dp).pressScale(onClick = onOpenList))
                    }
                }
            }
        }
    }
}

/** otherAchievementsCount — earned beyond the preview, rounded down to 10 if ≥10. */
private fun otherAchievementsCount(totalEarned: Int, showing: Int): Int {
    val remaining = (totalEarned - showing).coerceAtLeast(0)
    return if (remaining >= 10) (remaining / 10) * 10 else remaining
}
