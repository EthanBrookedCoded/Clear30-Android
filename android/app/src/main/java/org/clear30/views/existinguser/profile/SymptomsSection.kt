package org.clear30.views.existinguser.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    LaunchedEffect(Unit) {
        infos = Clear30Store.loadSymptomInfos()
        Logger.logEvent(userInfo.loggingID, LogEventType.openedSymptoms)
    }

    val i = infos ?: return  // first paint — load hasn't landed
    if (i.symptomInfos.isEmpty()) return  // no symptoms defined yet (program sync hasn't populated)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Symptoms")
        TinyText(
            "Pick what you're working through — we'll send a tip for each.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        i.symptomInfos.entries.sortedBy { it.key }.forEach { (key, symptom) ->
            SymptomRow(key, symptom) { onNow ->
                symptom.selected = onNow
                scope.launch { Clear30Store.save(i) }
                if (onNow) {
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.loggedSymptom,
                        mapOf(LogEventExtraDataType.SYMPTOM to key),
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomRow(key: String, symptom: SymptomInfo, onToggle: (Boolean) -> Unit) {
    val selected = symptom.selected == true
    Clear30Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!selected) },
        // Use the symptom's own gradient when selected; flat card otherwise so
        // the unselected set still reads as a list, not a row of buttons.
        gradient = if (selected) symptom.getGradient() else null,
    ) {
        SmallText(
            (if (selected) "✓  " else "○  ") + key.replaceFirstChar { it.uppercase() },
        )
    }
}
