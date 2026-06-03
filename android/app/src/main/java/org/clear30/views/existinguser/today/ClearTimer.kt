package org.clear30.views.existinguser.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.data.model.DateSpan
import org.clear30.data.model.Program
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.util.now

/**
 * ClearTimer — ported in spirit from the TimerWidget / DopamineTimer. Shows the
 * live time since `program.lastSmoked` using the ported [DateSpan] formatter,
 * ticking every second.
 */
@Composable
fun ClearTimer(program: Program, modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { tick++; delay(1000) }
    }

    @Suppress("UNUSED_EXPRESSION") tick
    val span = DateSpan(startDate = program.lastSmoked, endDate = now())
    val text = span.textRepresentation.ifBlank { "Just now" }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SmallText("Time clear", color = Color.White)
        Heading2(text, color = Color.White)
    }
}
