package org.clear30.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.clear30.data.LocalStore
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.views.theme.Clear30Colors
import org.clear30.util.adding
import org.clear30.util.now

/**
 * StatsWidget — the Android (Glance) equivalent of the iOS WidgetKit stats
 * widgets (TimerWidget/HealthWidget). Reads the persisted [Program] and shows
 * the current sober streak. The calendar widgets (Snake/Roman) port similarly
 * with their own Glance layouts (TODO).
 *
 * iOS `WidgetCenter.reloadAllTimelines()` maps to updating this from the app via
 * `StatsWidget().updateAll(context)` (e.g. after a check-in).
 */
class StatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val program = LocalStore.load(Program.STORE_KEY, Program.serializer()) ?: Program()
        val streak = currentSoberStreak(program)
        val daysClear = program.dayInfo.values.count { it.sober == true }
        provideContent { Content(streak, daysClear) }
    }

    @Composable
    private fun Content(streak: Int, daysClear: Int) {
        val onColor = ColorProvider(Clear30Colors.background)
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Clear30Colors.green))
                .cornerRadius(21.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$streak", style = TextStyle(color = onColor, fontWeight = FontWeight.Bold, fontSize = 44.sp))
            Text("day streak", style = TextStyle(color = onColor))
            Text("$daysClear days clear", style = TextStyle(color = onColor))
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}

private fun currentSoberStreak(program: Program): Int {
    var streak = 0
    var date = now()
    if (program.dayInfo[PlainDate.from(date)]?.sober != true) date = date.adding(days = -1)
    while (program.dayInfo[PlainDate.from(date)]?.sober == true) {
        streak++; date = date.adding(days = -1)
    }
    return streak
}
