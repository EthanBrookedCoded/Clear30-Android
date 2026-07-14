package org.clear30.views.existinguser.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.updateLastSmoked
import org.clear30.util.now
import org.clear30.views.components.SmallText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.softShadow
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import org.clear30.views.theme.Lexend
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * DopamineTimer — ported 1:1 from iOS `DopamineTimer.swift` (Profile usage:
 * `targetDate: program.lastSmoked, iconName: "flame.fill"`).
 *
 * A blue (meditation gradient) card titled **"Weed free streak"** with a flame
 * icon and an adjust button, counting up from `lastSmoked` as a stack of
 * [TimerBar] rows (days/hours/minutes/seconds). Each bar fills white to
 * `value / max`; the value text reads blue (the meditation accent) over the
 * fill and white over the translucent track — re-ticking every second.
 *
 * Tapping the adjust button (`slider.vertical.3`) enters **edit mode**: the
 * flame hides, the title becomes "Slide each to edit", every bar becomes a
 * drag-to-set slider, and a Cancel / Save pair appears. Save recomputes
 * `program.lastSmoked` from the dragged components (now − the elapsed time) and
 * persists it locally + to Supabase, exactly like iOS `onDateChanged →
 * resetLastSmoked`. Cancel discards the edits.
 */
@Composable
fun DopamineTimer(program: Program, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedDopamineTimer)
    }

    var isEditing by remember { mutableStateOf(false) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Pause the tick while editing so the dragged values aren't overwritten
    // (iOS `if !self.isEditing { updateTimeComponents() }`).
    LaunchedEffect(isEditing) {
        while (!isEditing) { delay(1_000); nowMs = System.currentTimeMillis() }
    }

    val elapsed = ((nowMs - program.lastSmoked.toEpochMilliseconds()) / 1000).coerceAtLeast(0)
    val liveDays = (elapsed / 86_400).toInt()
    val liveHours = ((elapsed % 86_400) / 3_600).toInt()
    val liveMinutes = ((elapsed % 3_600) / 60).toInt()
    val liveSeconds = (elapsed % 60).toInt()

    // Editable component state — seeded from the live values when edit begins.
    var eDays by remember { mutableIntStateOf(0) }
    var eHours by remember { mutableIntStateOf(0) }
    var eMinutes by remember { mutableIntStateOf(0) }
    var eSeconds by remember { mutableIntStateOf(0) }
    // Snapshot of the seed so Save can no-op when nothing was dragged — the day
    // bar caps at 30, so a >30-day streak must NOT be truncated just by opening
    // and saving the editor without changing anything.
    var seed by remember { mutableStateOf(intArrayOf(0, 0, 0, 0)) }

    fun beginEdit() {
        eDays = liveDays.coerceAtMost(30)
        eHours = liveHours
        eMinutes = liveMinutes
        eSeconds = liveSeconds
        seed = intArrayOf(eDays, eHours, eMinutes, eSeconds)
        isEditing = true
    }

    fun saveEdit() {
        isEditing = false
        nowMs = System.currentTimeMillis()
        // Unchanged → leave lastSmoked alone (avoids truncating long streaks).
        if (eDays == seed[0] && eHours == seed[1] && eMinutes == seed[2] && eSeconds == seed[3]) return
        val total = eDays.toLong() * 86_400 + eHours.toLong() * 3_600 + eMinutes.toLong() * 60 + eSeconds.toLong()
        val newLastSmoked: Instant = now().minus(total.seconds)
        program.lastSmoked = newLastSmoked
        scope.launch {
            Clear30Store.save(program)
            SupabaseController.updateLastSmoked(newLastSmoked)
        }
        Logger.logEvent(userInfo.loggingID, LogEventType.openedDopamineTimer)
    }

    Box(Modifier.fillMaxWidth().cardStyle(gradient = Clear30Gradients.meditation)) {
        Column(Modifier.fillMaxWidth()) {
            // Header — flame • "Weed free streak" / "Slide each to edit" • adjust
            Row(
                Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                if (!isEditing) {
                    Icon(
                        sfSymbol("flame.fill"),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp),
                    )
                }
                SmallText(
                    if (isEditing) "Slide each to edit" else "Weed free streak",
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                if (!isEditing) {
                    Icon(
                        sfSymbol("slider.vertical.3"),
                        contentDescription = "Edit",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { Haptics.lightImpact(); beginEdit() },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                if (isEditing) {
                    TimerBar(eDays, 30, "Day", editable = true) { eDays = it }
                    TimerBar(eHours, 24, "Hour", editable = true) { eHours = it }
                    TimerBar(eMinutes, 60, "Minute", editable = true) { eMinutes = it }
                    TimerBar(eSeconds, 60, "Second", editable = true) { eSeconds = it }
                } else {
                    if (liveDays > 0) TimerBar(liveDays, 30, "Day")
                    TimerBar(liveHours, 24, "Hour")
                    TimerBar(liveMinutes, 60, "Minute")
                    TimerBar(liveSeconds, 60, "Second")
                }
            }

            // Cancel / Save (iOS edit footer).
            AnimatedVisibility(visible = isEditing, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    EditActionButton("Cancel", null, Modifier.weight(1f)) {
                        Haptics.lightImpact(); isEditing = false; nowMs = System.currentTimeMillis()
                    }
                    EditActionButton("Save", "checkmark", Modifier.weight(1f)) {
                        Haptics.successLight(); saveEdit()
                    }
                }
            }
        }
    }
}

/** White pill button on the gradient card (iOS `TextIconButton` — solid `clear30Button` fill, dark text). */
@Composable
private fun EditActionButton(text: String, icon: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.button)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = Dimens.cardSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(sfSymbol(icon), contentDescription = null, tint = Clear30Colors.text, modifier = Modifier.size(16.dp))
        }
        SmallText(text, color = Clear30Colors.text)
    }
}

/**
 * One unit row — translucent white track with a white fill at `value / max`;
 * "N Units" text reads blue where it overlaps the fill, white elsewhere. In
 * [editable] mode the whole bar is a drag-to-set slider (iOS DragGesture).
 */
@Composable
private fun TimerBar(
    value: Int,
    max: Int,
    unit: String,
    editable: Boolean = false,
    onValueChange: (Int) -> Unit = {},
) {
    val label = "$value $unit${if (value != 1) "s" else ""}"
    val shape = RoundedCornerShape(14.dp)

    // The value steps once per second, but we animate the fill toward it over the
    // full tick interval with linear easing — so the seconds bar sweeps
    // continuously instead of snapping. While editing we snap (no ramp) so the
    // fill tracks the finger 1:1.
    val target = (value.toFloat() / max).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (editable) 0 else 1000, easing = LinearEasing),
        label = "timerFill-$unit",
    )

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(40.dp)
            // iOS: `.shadow(color: .clear30Shadow.opacity(0.75), radius: 5)` — a
            // faint lift, not a halo (clear30Shadow is already black @ 0.2).
            .softShadow(
                Clear30Colors.shadow.copy(alpha = Clear30Colors.shadow.alpha * 0.75f),
                cornerRadius = 14.dp,
                blurRadius = 5.dp,
            )
            // iOS masks the whole composited bar ONCE (DopamineTimer.swift
            // `.mask(RoundedRectangle(...))`), so the fill is a plain left-aligned
            // rect under the container clip — clipping the fill independently made
            // its corner radii collapse on narrow fills.
            .clip(shape),
    ) {
        val full = maxWidth
        val fillW = full * fraction
        val widthPx = constraints.maxWidth.toFloat()

        // Track
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
        // Fill — plain rect; the container clip rounds its outer corners.
        Box(Modifier.width(fillW).fillMaxHeight().background(Color.White))

        // White label over the translucent track. No ellipsis / no wrap — the bar
        // is full-width so the text always fits; forcing `Visible` + single line
        // kills the stray "…" the ellipsizing DefaultText produced.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            BarLabel(label, Color.White)
        }
        // Blue label clipped to the filled portion (laid out at full width so it
        // lines up); as the white fill grows the words read blue under it.
        Box(Modifier.width(fillW).fillMaxHeight().clipToBounds()) {
            Box(Modifier.width(full).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                BarLabel(label, Clear30Colors.meditation1)
            }
        }

        // Edit overlay — drag (or tap) anywhere on the bar to set the value 0..max
        // (iOS DragGesture(minimumDistance: 0)).
        if (editable) {
            fun setFromX(x: Float) {
                if (widthPx <= 0f) return
                val v = (x / widthPx * max).roundToInt().coerceIn(0, max)
                if (v != value) { Haptics.lightImpact(); onValueChange(v) }
            }
            Box(
                Modifier.matchParentSize()
                    .pointerInput(max) { detectTapGestures { offset -> setFromX(offset.x) } }
                    .pointerInput(max) {
                        detectHorizontalDragGestures(onDragStart = { offset -> setFromX(offset.x) }) { change, _ ->
                            setFromX(change.position.x)
                        }
                    },
            )
        }
    }
}

/** Bar text — Lexend 19sp, single line, never ellipsized (overflow visible). */
@Composable
private fun BarLabel(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 11.dp),
        color = color,
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}
