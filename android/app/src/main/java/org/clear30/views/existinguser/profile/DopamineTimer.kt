package org.clear30.views.existinguser.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import kotlin.math.min

/**
 * DopamineTimer — ported from the iOS dopamine-recovery widget.
 *
 * Time-since-last-use as a progress ring filling toward 72h (the rough
 * window the science we cite is anchored to). Re-poll every minute so the
 * ring eases forward without being expensive — sub-minute precision isn't
 * visible at this scale.
 */
@Composable
fun DopamineTimer(program: Program, userInfo: UserInfo) {
    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedDopamineTimer)
    }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); nowMs = System.currentTimeMillis() }
    }

    val elapsedHours = ((nowMs - program.lastSmoked.toEpochMilliseconds()) / 3_600_000.0).coerceAtLeast(0.0)
    val pct = min(1.0, elapsedHours / 72.0).toFloat()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Dopamine recovery")
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                ProgressRing(pct)
                Column {
                    SmallText("${"%.1f".format(elapsedHours)}h clear", color = Color.White)
                    TinyText(
                        if (pct >= 1f) "Receptors fully reset (72h+)"
                        else "${(pct * 100).toInt()}% to a full reset",
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(pct: Float) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
            // Track
            drawCircle(color = Color.White.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
            // Fill — 0..360 deg sweep, starting at top.
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * pct,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f),
            )
        }
        TinyText("${(pct * 100).toInt()}%", color = Color.White)
    }
}
