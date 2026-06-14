package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.data.model.DateSpan
import org.clear30.data.model.Program
import org.clear30.views.components.GiganticText
import org.clear30.views.components.MiniText
import org.clear30.views.components.TinyText
import org.clear30.util.now

/**
 * ClearTimer — count-up timer that lives on the Today hero card. Renders the
 * span since `program.lastSmoked` as a strip of brand badges (D / H / M / S),
 * matching the iOS TimerWidget's bigger-is-bigger hierarchy. Each unit's
 * number renders as `GiganticText`, with the unit label in MiniText below;
 * empty leading units collapse so a fresh streak reads "23H 04M 12S" rather
 * than "0D 23H 04M 12S".
 *
 * The "Time clear" eyebrow stays present so the user always knows what they're
 * looking at — DateSpan formatting alone can read ambiguously on day 1.
 */
@Composable
fun ClearTimer(program: Program, modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { tick++; delay(1000) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    val span = DateSpan(startDate = program.lastSmoked, endDate = now())
    val components = span.components

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TinyText("TIME CLEAR", color = Color.White.copy(alpha = 0.75f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val totalDays = components.months * 30 + components.days
            // Hide leading zero badges so the timer reads cleanly at day 1.
            val showDays = totalDays > 0
            val showHours = showDays || components.hours > 0
            val showMinutes = showHours || components.minutes > 0
            if (showDays) UnitBadge(totalDays.toString(), "D")
            if (showHours) UnitBadge(components.hours.toString().padStart(2, '0'), "H")
            if (showMinutes) UnitBadge(components.minutes.toString().padStart(2, '0'), "M")
            UnitBadge(components.seconds.toString().padStart(2, '0'), "S")
        }
    }
}

@Composable
private fun UnitBadge(value: String, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            GiganticText(value, color = Color.White)
        }
        MiniText(unit, color = Color.White.copy(alpha = 0.75f))
    }
}
