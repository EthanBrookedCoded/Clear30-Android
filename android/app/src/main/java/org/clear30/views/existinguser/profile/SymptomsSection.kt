package org.clear30.views.existinguser.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.SymptomInfo
import org.clear30.data.model.SymptomInfos
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * SymptomsSection — ported from the Symptoms picker on Profile.
 *
 * Renders each symptom in [SymptomInfos.symptomInfos] as a gradient card the
 * user can toggle "selected" for. Selection persists locally via
 * [Clear30Store.save] and fires the `loggedSymptom` analytic with the symptom
 * key as extra data. The accountability-text frequency, tip rendering, and
 * Reddit/prompt cross-links from the full iOS Symptoms screen land later — the
 * core toggle is enough for the engine to drive scheduled SMS reminders
 * against the user's currently-selected symptom set.
 */
@Composable
fun SymptomsSection(userInfo: UserInfo) {
    var infos by remember { mutableStateOf<SymptomInfos?>(null) }
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Pair<String, SymptomInfo>?>(null) }

    LaunchedEffect(Unit) {
        infos = Clear30Store.loadSymptomInfos()
        Logger.logEvent(userInfo.loggingID, LogEventType.openedSymptoms)
    }

    val i = infos ?: return  // first paint — load hasn't landed
    if (i.symptomInfos.isEmpty()) return  // no symptoms defined yet (program sync hasn't populated)

    detail?.let { (key, symptom) ->
        androidx.activity.compose.BackHandler { detail = null }
        SymptomDetailScreen(key, symptom, userInfo, onBack = { detail = null })
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Symptoms")
        TinyText(
            "Tap to read tips and stories. Long-press the check to mark it active.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        i.symptomInfos.entries.sortedBy { it.key }.forEach { (key, symptom) ->
            SymptomRow(
                key = key,
                symptom = symptom,
                onOpen = { detail = key to symptom },
                onToggle = { onNow ->
                    symptom.selected = onNow
                    scope.launch { Clear30Store.save(i) }
                    if (onNow) {
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.loggedSymptom,
                            mapOf(LogEventExtraDataType.SYMPTOM to key),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SymptomRow(
    key: String,
    symptom: SymptomInfo,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val selected = symptom.selected == true
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        gradient = if (selected) symptom.getGradient() else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallText(
                key.replaceFirstChar { it.uppercase() },
                color = if (selected) Color.White else Clear30Colors.text,
            )
            Spacer(Modifier.weight(1f))
            // Standalone selection toggle — separate clickable from the row tap
            // so opening the detail doesn't accidentally flip the selection.
            Box(
                Modifier.clickable { onToggle(!selected) }.padding(8.dp),
            ) {
                SmallText(
                    if (selected) "✓" else "○",
                    color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        }
    }
}
