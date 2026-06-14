package org.clear30.views.existinguser.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.clear30.data.CheckInLogger
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.GiganticText
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * CheckInSheet — ported from the iOS fullscreen check-in flow. A modal bottom
 * sheet that asks "did you smoke today?" and, if yes, captures a puff count
 * via a quick +/− stepper. The Today tab raises this when the user taps the
 * day's row OR the inline check-in card.
 *
 * Submission goes through [CheckInLogger.logWeedCheckIn] which:
 *   • writes ProgramDayInfo locally
 *   • syncs day_info / last_smoked to Supabase
 *   • runs the achievement engine
 *   • fires the celebration confetti via PopupManager when a milestone hits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(
    logger: CheckInLogger,
    userInfo: UserInfo,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picked by remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    var puffs by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedCheckInSheet)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Clear30Colors.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            when (picked) {
                null -> {
                    Heading1("How did today go?")
                    SmallText(
                        "One tap. Be honest with yourself — a slip is data, not a failure.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    OutcomeButton(
                        title = "I didn't smoke today",
                        emoji = "✅",
                        gradient = Clear30Gradients.clear30,
                    ) { picked = true }
                    OutcomeButton(
                        title = "I smoked today",
                        emoji = "💨",
                        gradient = Clear30Gradients.red,
                    ) { picked = false }
                }

                true -> {
                    // Sober — write and dismiss immediately. Confetti runs
                    // through PopupManager via CheckInLogger so the user sees
                    // it on the underlying screen after dismiss.
                    LaunchedEffect(Unit) {
                        logger.logWeedCheckIn(true)
                        onDismiss()
                    }
                }

                false -> {
                    // Smoked — capture puff count before persisting.
                    Heading1("How much?")
                    SmallText(
                        "Rough count is fine. Tracking the amount helps the trend, not the moment.",
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    PuffStepper(value = puffs, onValueChange = { puffs = it })
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    StretchedButton(
                        if (puffs == 0) "Log without counting" else "Log $puffs puff${if (puffs == 1) "" else "s"}",
                        gradient = Clear30Gradients.red,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        logger.logWeedCheckIn(false)
                        Logger.logEvent(
                            userInfo.loggingID,
                            LogEventType.smokedCheckIn,
                            mapOf(LogEventExtraDataType.CHECK_IN_AMOUNT to puffs.toString()),
                        )
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeButton(title: String, emoji: String, gradient: androidx.compose.ui.graphics.Brush, onClick: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), gradient = gradient) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            GiganticText(emoji)
            SmallText(title, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun PuffStepper(value: Int, onValueChange: (Int) -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepperButton("−") { if (value > 0) onValueChange(value - 1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GiganticText(value.toString())
                TinyText("puffs", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
            StepperButton("+") { onValueChange(value + 1) }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
    ) {
        Heading1(label)
    }
}
