package org.clear30.views.existinguser.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.clear30.views.theme.Dimens

/**
 * AchievementsSection — ported from the Profile achievements grid. Fetches the
 * definitions + the user's earned achievements and lists the earned ones
 * (SF-symbol icon + name). The full grid (locked/rarity tiers, detail sheet) is
 * a follow-up.
 */
@Composable
fun AchievementsSection(userInfo: UserInfo) {
    var earned by remember { mutableStateOf<List<AchievementDefinition>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AchievementDefinition?>(null) }

    LaunchedEffect(Unit) {
        val defs = SupabaseController.getAchievementDefinitions().associateBy { it.key }
        val userAchievements = SupabaseController.getUserAchievements(userInfo.userID)
        val earnedKeys = userAchievements.map { it.achievementKey }.toSet()
        earned = earnedKeys.mapNotNull { defs[it] }
        loaded = true

        // Persist the fetched definitions + earned list so the AchievementEngine
        // (fired from CheckInLogger after every check-in) has criteria to
        // evaluate against, even when the user never opens this section.
        val ad = org.clear30.data.Clear30Store.loadAchievementData()
        ad.definitions = defs.values.toMutableList()
        ad.earnedAchievements = userAchievements.toMutableList()
        org.clear30.data.Clear30Store.save(ad)
    }

    detail?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    androidx.compose.material3.Icon(sfSymbol(a.sfSymbol), contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(28.dp))
                    androidx.compose.material3.Text(a.name)
                }
            },
            text = { androidx.compose.material3.Text(a.description.ifBlank { "Earned achievement" }) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { detail = null }) {
                    androidx.compose.material3.Text("Nice")
                }
            },
        )
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Achievements")
        when {
            !loaded -> SmallText("Loading…", color = Clear30Colors.text.copy(alpha = 0.5f))
            earned.isEmpty() -> SmallText("Keep going — your first achievement is close.", color = Clear30Colors.text.copy(alpha = 0.5f))
            else -> earned.forEach { a ->
                Clear30Card(modifier = Modifier.fillMaxWidth()
                    .clickable {
                        detail = a
                        org.clear30.data.Logger.logEvent(
                            userInfo.loggingID,
                            org.clear30.data.LogEventType.openedAchievement,
                            mapOf(org.clear30.data.LogEventExtraDataType.ACHIEVEMENT_KEY to a.key),
                        )
                    }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        androidx.compose.material3.Icon(sfSymbol(a.sfSymbol), contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(28.dp))
                        Column {
                            SmallText(a.name)
                            if (a.description.isNotBlank()) TinyText(a.description, color = Clear30Colors.text.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
