package org.clear30.views.existinguser.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.AchievementEngine
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getAchievementDefinitions
import org.clear30.data.supabase.getUserAchievements
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * AchievementsSection — the Profile achievements grid. Renders three columns
 * of tiles, every active definition included: earned tiles colored by their
 * rarity gradient (or Clear30 green when no rarity is set), locked tiles
 * grayed with a lock overlay. Tapping any tile opens a detail dialog with
 * the criterion + earn date / locked hint.
 *
 * The definitions + earned set are fetched on first visit and cached locally
 * via [Clear30Store.loadAchievementData] / [Clear30Store.save] so the
 * [AchievementEngine] (run on every check-in) has criteria even when the user
 * never opens this section.
 */
@Composable
fun AchievementsSection(userInfo: UserInfo) {
    var defs by remember { mutableStateOf<List<AchievementDefinition>>(emptyList()) }
    var earnedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AchievementDefinition?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.viewedAchievementsList)
        val defsMap = SupabaseController.getAchievementDefinitions().associateBy { it.key }
        val userAchievements = SupabaseController.getUserAchievements(userInfo.userID)
        defs = defsMap.values.sortedBy { it.displayOrder }
        earnedKeys = userAchievements.map { it.achievementKey }.toSet()
        loaded = true

        // Persist for the engine — same as before, but as a side effect so
        // the UI flips loaded=true before the disk write completes.
        val ad = Clear30Store.loadAchievementData()
        ad.definitions = defsMap.values.toMutableList()
        ad.earnedAchievements = userAchievements.toMutableList()
        Clear30Store.save(ad)
    }

    detail?.let { def ->
        DetailDialog(def, earned = def.key in earnedKeys, onClose = { detail = null })
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Achievements")
        when {
            !loaded -> androidx.compose.material3.CircularProgressIndicator()
            defs.isEmpty() -> SmallText(
                "Achievements will appear once your program loads.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
            else -> {
                val active = defs.filter { it.isActive }
                TinyText(
                    "${earnedKeys.count { k -> active.any { it.key == k } }} / ${active.size} earned",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
                Grid(active, earnedKeys) { def ->
                    detail = def
                    Logger.logEvent(
                        userInfo.loggingID,
                        if (def.key in earnedKeys) LogEventType.openedAchievement else LogEventType.openedAchievement,
                        mapOf(LogEventExtraDataType.ACHIEVEMENT_KEY to def.key),
                    )
                }
            }
        }
    }
}

@Composable
private fun Grid(active: List<AchievementDefinition>, earned: Set<String>, onTap: (AchievementDefinition) -> Unit) {
    // 3-column manual grid — LazyVerticalGrid would force its own scroll
    // container, which fights the Profile's parent verticalScroll.
    val cols = 3
    active.chunked(cols).forEach { row ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            row.forEach { def ->
                Box(modifier = Modifier.weight(1f)) {
                    Tile(def, earned = def.key in earned, onTap = { onTap(def) })
                }
            }
            // Fill remaining columns so the last row aligns with the others.
            repeat(cols - row.size) { Box(modifier = Modifier.weight(1f)) {} }
        }
    }
}

@Composable
private fun Tile(def: AchievementDefinition, earned: Boolean, onTap: () -> Unit) {
    val gradient: Brush = when {
        !earned -> Clear30Gradients.gray
        def.rarity != null -> Clear30Gradients.linear(
            listOf(colorFromHex(def.rarity!!.gradientStart), colorFromHex(def.rarity!!.gradientEnd)),
            Clear30Gradients.bottomLeading,
            Clear30Gradients.topTrailing,
        )
        else -> Clear30Gradients.clear30
    }
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
        gradient = gradient,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = if (earned) 0.25f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (earned) Icon(
                    sfSymbol(def.sfSymbol),
                    contentDescription = def.name,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                ) else Icon(
                    Icons.Rounded.Lock,
                    contentDescription = "locked",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            TinyText(
                def.name,
                color = if (earned) Color.White else Color.White.copy(alpha = 0.5f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DetailDialog(def: AchievementDefinition, earned: Boolean, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    sfSymbol(def.sfSymbol),
                    contentDescription = null,
                    tint = if (earned) Clear30Colors.green else Clear30Colors.text.copy(alpha = 0.5f),
                )
                Text(def.name)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (def.description.isNotBlank()) def.description else if (earned) "Earned achievement" else "Keep going — you'll unlock this.")
                def.rarity?.let { r ->
                    TinyText(
                        "${r.name}${(def.stats?.percentageEarned)?.let { " · ${"%.1f".format(it)}% have this" }.orEmpty()}",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Nice") } },
    )
}
