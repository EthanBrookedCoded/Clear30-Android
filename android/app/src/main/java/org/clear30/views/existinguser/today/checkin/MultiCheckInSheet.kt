package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.clear30.data.CheckInLogger
import org.clear30.data.model.CheckInDefaults
import org.clear30.data.model.LoggedCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.time.Duration.Companion.hours

private val MULTI_MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val MULTI_WEEKDAY_ABBR = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Days that have no weed check-in yet, from program start up to yesterday — iOS
 * `datesWithoutCheckIn` scans the whole program; we cap the catch-up list at the
 * last ~31 days so a long-running program never produces an unwieldy list.
 * Returns empty for a brand-new user (no dayInfo yet) so we never nag on day one.
 */
fun missedCheckInDays(program: Program): List<PlainDate> {
    if (program.dayInfo.isEmpty()) return emptyList()
    val yesterday = PlainDate.from(now()).adding(days = -1)
    val programStart = PlainDate.from(program.startDate)
    val windowStart = yesterday.adding(days = -30)
    val from = if (windowStart >= programStart) windowStart else programStart
    if (from > yesterday) return emptyList()

    val result = mutableListOf<PlainDate>()
    var d = from
    while (d <= yesterday) {
        if (program.dayInfo[d]?.sober == null) result.add(d)
        d = d.adding(days = 1)
    }
    return result
}

/**
 * MultiCheckInSheet — ported from iOS `MultiCheckIn.swift`. A "let's catch up"
 * batch flow for missed days. iOS uses a tappable month calendar; this Android
 * port lists each missed day with a 💨 Smoked / 🤩 Clear toggle (defaulting to
 * clear, like iOS), then a single confirm that writes all of them at once.
 */
@Composable
fun MultiCheckInSheet(
    name: String,
    dates: List<PlainDate>,
    logger: CheckInLogger,
    onDismiss: () -> Unit,
) {
    // Per-day sober choice, default true (didn't smoke) — matches iOS.
    val sober = remember { mutableStateMapOf<PlainDate, Boolean>().apply { dates.forEach { put(it, true) } } }

    fun confirm() {
        dates.forEach { date ->
            val didntSmoke = sober[date] ?: true
            logger.logCheckIns(
                date.dateObject,
                listOf(
                    LoggedCheckIn(
                        id = CheckInDefaults.weed.id,
                        completion = didntSmoke,
                        timestamp = date.dateObject + 12.hours,
                    ),
                ),
            )
        }
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            ) {
                SmallText("Welcome back${if (name.isBlank()) "" else " $name"}.", color = Clear30Colors.text.copy(alpha = 0.5f))
                Heading3("Let's catch up on check-ins!")

                Spacer(Modifier.height(Dimens.cardSpacing * 2))

                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    dates.forEach { date ->
                        val didntSmoke = sober[date] ?: true
                        Clear30Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    SmallText(prettyShortDate(date), color = Clear30Colors.text)
                                    TinyText(weekdayShort(date), color = Clear30Colors.text.copy(alpha = 0.5f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                                    TogglePill("💨", selected = !didntSmoke, gradient = Clear30Gradients.grayFlat) { sober[date] = false }
                                    TogglePill("🤩", selected = didntSmoke, gradient = Clear30Gradients.clear30) { sober[date] = true }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.cardSpacing))

                DefaultButton(
                    "Confirm ${dates.size} check-in${if (dates.size == 1) "" else "s"}",
                    gradient = Clear30Gradients.clear30,
                    modifier = Modifier.fillMaxWidth(),
                ) { confirm() }

                Spacer(Modifier.height(Dimens.cardSpacing / 2))
            }
        }
    }
}

@Composable
private fun TogglePill(emoji: String, selected: Boolean, gradient: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp)
            .clip(RoundedCornerShape(Dimens.cornerRadius * 0.8f))
            .background(if (selected) gradient else Clear30Gradients.gray)
            .clickable { Haptics.lightImpact(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        SmallText(emoji, color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.5f))
    }
}

private fun prettyShortDate(date: PlainDate): String =
    "${MULTI_MONTH_ABBR[date.month - 1]} ${date.day}"

private fun weekdayShort(date: PlainDate): String =
    MULTI_WEEKDAY_ABBR[(LocalDate(date.year, date.month, date.day).dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]
