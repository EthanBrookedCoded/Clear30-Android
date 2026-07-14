package org.clear30.views.existinguser.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.LoggedCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.allWeedCheckIns
import org.clear30.data.model.checkIns
import org.clear30.util.nearestHour
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.roundToInt

/**
 * CheckInDayCard — ported from CheckInDayCard.swift (stateful card). The hero
 * card of the Today feed, showing the day's check-in state:
 *
 *   • Unlogged        → "Check in for Today" button.
 *   • Logged          → one [CheckInStatusRow] per weed method (with inline
 *     amount stepper, "smoked again" `plus.circle.fill`, and remove
 *     `xmark.circle.fill`), the slip timestamps beneath (notch + time + amount,
 *     each opening the detail editor), then one row per custom check-in (with
 *     streak 🔥 and remove) or a placeholder "+" row for customs added after
 *     the day was logged.
 *   • Detail editor   → method menu, amount stepper, hour ruler, Remove — the
 *     back button commits any changes through [onCheckInChange].
 *
 * [onCheckInChange] mirrors iOS `CheckInViewModel.updateCheckIn(old:new:)`:
 * `(old, null)` removes, `(null, new)` adds ("smoked again"), `(old, new)`
 * replaces. When null, all edit affordances hide (read-only card).
 *
 * Divergence: the iOS group-member rows and the "Custom Check In" upsell row
 * are not ported here (groups render elsewhere on Android).
 */
@Composable
fun CheckInDayCard(
    selectedDay: PlainDate,
    program: Program,
    showStreak: Boolean,
    onCheckIn: () -> Unit,
    onCheckInChange: ((old: LoggedCheckIn?, new: LoggedCheckIn?) -> Unit)? = null,
    // Mutation counter (TodayTab's `refresh`). `program` is mutated in place, so
    // the card's args never change by equality after a check-in edit and strong
    // skipping would freeze it on the pre-edit state; a changed revision forces
    // recomposition.
    revision: Int = 0,
) {
    val info: ProgramDayInfo? = program.dayInfo[selectedDay]
    val isToday = selectedDay == PlainDate.from(now())
    val sober = info?.sober
    val onGradient = sober == true
    val canEditValues = onCheckInChange != null

    // Detail editor state (iOS showDetailFor + detailUpdated*). Keyed on
    // `revision` too: an external data change closes the editor, and the read
    // keeps `revision` a *used* parameter — the Compose compiler excludes unused
    // params from the skip comparison, which would leave the card stale after a
    // check-in edit (program is mutated in place, so no other arg ever changes).
    var showDetailFor by remember(selectedDay, revision) { mutableStateOf<LoggedCheckIn?>(null) }
    var detailUpdatedTimestamp by remember { mutableStateOf<Instant?>(null) }
    var detailUpdatedAmount by remember { mutableStateOf<Int?>(null) }
    var detailUpdatedMethod by remember { mutableStateOf<CheckInMethod?>(null) }

    fun showDetails(checkIn: LoggedCheckIn) {
        detailUpdatedAmount = checkIn.amount
        detailUpdatedTimestamp = checkIn.timestamp
        detailUpdatedMethod = checkIn.method
        showDetailFor = checkIn
    }

    // Commit (or remove) the edited check-in — iOS `hideDetails(remove:)`.
    fun hideDetails(remove: Boolean = false) {
        val checkIn = showDetailFor ?: return
        showDetailFor = null

        if (remove) {
            onCheckInChange?.invoke(checkIn, null)
            return
        }
        var updated = checkIn
        detailUpdatedTimestamp?.takeIf { it != updated.timestamp }?.let { updated = updated.copy(timestamp = it) }
        detailUpdatedAmount?.takeIf { it != updated.amount }?.let { updated = updated.copy(amount = it) }
        detailUpdatedMethod?.takeIf { it != updated.method }?.let { method ->
            // Method change = a new check-in under the new method id.
            updated = LoggedCheckIn(
                id = method.id,
                amount = updated.amount,
                completion = updated.completion,
                timestamp = updated.timestamp,
            )
        }
        if (updated != checkIn) onCheckInChange?.invoke(checkIn, updated)
    }

    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        gradient = if (onGradient) Clear30Gradients.clear30 else null,
        outlineGradient = if (onGradient) Clear30Gradients.white else Clear30Gradients.clear30,
        outlineOpacity = 0.5f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            // Badge or back button.
            val detail = showDetailFor
            if (detail == null) {
                DateBadge(selectedDay, isToday)
            } else {
                Box(
                    Modifier
                        .pressScale { hideDetails() }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray)
                        .padding(6.dp),
                ) {
                    Icon(
                        sfSymbol("chevron.left"),
                        contentDescription = "Back",
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            val detailCompletion = detail?.completion
            val detailMethod = detail?.method
            val detailTimestamp = detail?.timestamp
            if (detail != null && detailCompletion != null && detailMethod != null && detailTimestamp != null) {
                // ── Detail editor ────────────────────────────────────────────
                CheckInDetailEditor(
                    completion = detailCompletion,
                    method = detailMethod,
                    timestamp = detailTimestamp,
                    onGradient = onGradient,
                    updatedMethod = detailUpdatedMethod,
                    updatedAmount = detailUpdatedAmount,
                    onMethodChange = { detailUpdatedMethod = it },
                    onAmountChange = { detailUpdatedAmount = it },
                    onTimestampChange = { detailUpdatedTimestamp = it },
                    onRemove = { hideDetails(remove = true) },
                )
            } else if (info != null && sober != null) {
                // ── Overview ─────────────────────────────────────────────────
                val allWeedCheckIns = info.loggedCheckIns.allWeedCheckIns
                val methods = allWeedCheckIns.mapNotNull { it.method }.distinct()

                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    methods.forEach { method ->
                        val checkIns = info.loggedCheckIns.checkIns(method)
                        val mainCheckIn = checkIns.firstOrNull()
                        val completion = mainCheckIn?.completion
                        if (mainCheckIn != null && completion != null) {
                            WeedCheckInRows(
                                weedCheckIn = mainCheckIn,
                                timestamps = checkIns.filter { it.timestamp != null },
                                completion = completion,
                                method = method,
                                onGradient = onGradient,
                                canEditValues = canEditValues,
                                onAmountChange = { checkIn, amount ->
                                    onCheckInChange?.invoke(checkIn, checkIn.copy(amount = amount))
                                },
                                onSmokedAgain = { checkIn ->
                                    onCheckInChange?.invoke(null, LoggedCheckIn(id = checkIn.id, completion = false))
                                },
                                onRemove = { checkIn -> onCheckInChange?.invoke(checkIn, null) },
                                onShowDetails = { showDetails(it) },
                            )
                        }
                    }

                    program.customCheckIns.forEach { customCheckIn ->
                        CustomCheckInRow(
                            customCheckIn = customCheckIn,
                            dayInfo = info,
                            program = program,
                            showStreak = showStreak,
                            onGradient = onGradient,
                            canEditValues = canEditValues,
                            onRemove = { checkIn -> onCheckInChange?.invoke(checkIn, null) },
                            onCheckIn = onCheckIn,
                        )
                    }
                }
            } else {
                // ── Unlogged → check-in button ───────────────────────────────
                Box(
                    Modifier.fillMaxWidth()
                        .padding(top = Dimens.cardSpacing / 2)
                        .pressScale { onCheckIn() }
                        .clip(RoundedCornerShape(Dimens.cornerRadius * 0.9f))
                        .background(Clear30Colors.opacityGray)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SmallText("Check in for ${if (isToday) "Today" else "this day"}", color = Clear30Colors.text)
                }
            }
        }
    }
}

// MARK: - Weed check-in rows

/** One method's status row + its slip timestamps (iOS `weedCheckInView`). */
@Composable
private fun WeedCheckInRows(
    weedCheckIn: LoggedCheckIn,
    timestamps: List<LoggedCheckIn>,
    completion: Boolean,
    method: CheckInMethod,
    onGradient: Boolean,
    canEditValues: Boolean,
    onAmountChange: (LoggedCheckIn, Int) -> Unit,
    onSmokedAgain: (LoggedCheckIn) -> Unit,
    onRemove: (LoggedCheckIn) -> Unit,
    onShowDetails: (LoggedCheckIn) -> Unit,
) {
    val option = if (completion) method.customCheckIn.completeOption else method.customCheckIn.incompleteOption
    val gradient = if (completion) Clear30Gradients.clear30 else Clear30Gradients.grayFlat
    val hasTimestamps = timestamps.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Box(Modifier.padding(top = Dimens.cardSpacing / 2)) {
            CheckInStatusRow(emoji = option.emoji, text = option.name, gradient = gradient, onGradient = onGradient) {
                // Amount stepper (when there's an amount but no session timestamps).
                val amountNum = weedCheckIn.amount
                if (amountNum != null && !hasTimestamps) {
                    CheckInAmountStepper(
                        value = amountNum,
                        label = method.amountLabel,
                        onValueChange = { onAmountChange(weedCheckIn, it) },
                    )
                }
                if (canEditValues && hasTimestamps && !completion) {
                    // "Smoked again" — appends another session for this method.
                    EditIcon("plus.circle.fill", onGradient) { onSmokedAgain(weedCheckIn) }
                } else if (canEditValues) {
                    // Remove the whole check-in.
                    EditIcon("xmark.circle.fill", onGradient) { onRemove(weedCheckIn) }
                }
            }
        }

        // Per-session timestamps (slips only) — each opens the detail editor.
        if (hasTimestamps && !completion) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                timestamps.forEachIndexed { index, checkIn ->
                    val timestamp = checkIn.timestamp ?: return@forEachIndexed
                    Row(
                        Modifier.padding(start = Dimens.cardSpacing),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        SubItemNotch(heightOverride = if (index == 0) null else 45.dp)
                        TinyTextButton(
                            text = hourString(timestamp),
                            icon = "chevron.right",
                        ) { onShowDetails(checkIn) }
                        val amountString = method.getAmountString(checkIn.amount)
                        if (amountString != null) {
                            TinyText(
                                amountString,
                                color = LocalContentColor.current.copy(alpha = 0.25f),
                                modifier = Modifier.padding(top = Dimens.cardSpacing / 4),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One custom check-in row, or the "+" placeholder when the day predates it. */
@Composable
private fun CustomCheckInRow(
    customCheckIn: CustomCheckIn,
    dayInfo: ProgramDayInfo,
    program: Program,
    showStreak: Boolean,
    onGradient: Boolean,
    canEditValues: Boolean,
    onRemove: (LoggedCheckIn) -> Unit,
    onCheckIn: () -> Unit,
) {
    val loggedCheckIn = dayInfo.loggedCheckIns.firstOrNull { it.id == customCheckIn.id }
    val completion = loggedCheckIn?.completion

    if (loggedCheckIn != null && completion != null) {
        val option = if (completion) customCheckIn.completeOption else customCheckIn.incompleteOption
        val gradient = if (completion) customCheckIn.gradient else Clear30Gradients.grayFlat
        val streak = if (showStreak) latestConsecutiveDaysCompleted(program, customCheckIn) else 0
        CheckInStatusRow(emoji = option.emoji, text = option.name, gradient = gradient, onGradient = onGradient) {
            if (streak > 1) {
                SmallText("🔥 $streak", color = LocalContentColor.current.copy(alpha = 0.5f))
            }
            if (canEditValues) {
                EditIcon("xmark.circle.fill", onGradient) { onRemove(loggedCheckIn) }
            }
        }
    } else {
        // Placeholder — this day was checked in before the custom check-in existed.
        Box(Modifier.alpha(0.25f).pressScale { Haptics.mediumImpact(); onCheckIn() }) {
            CheckInStatusRow(iconName = "plus.circle.fill", text = customCheckIn.name, onGradient = onGradient)
        }
    }
}

// MARK: - Detail editor

/** The per-session editor (iOS `detailView`): method menu, amount, hour, remove. */
@Composable
private fun CheckInDetailEditor(
    completion: Boolean,
    method: CheckInMethod,
    timestamp: Instant,
    onGradient: Boolean,
    updatedMethod: CheckInMethod?,
    updatedAmount: Int?,
    onMethodChange: (CheckInMethod) -> Unit,
    onAmountChange: (Int) -> Unit,
    onTimestampChange: (Instant) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Method menu on the status chip.
            var menuOpen by remember { mutableStateOf(false) }
            val currentMethod = updatedMethod ?: method
            val option = if (completion) currentMethod.customCheckIn.completeOption
            else currentMethod.customCheckIn.incompleteOption
            val gradient = if (completion) Clear30Gradients.clear30 else Clear30Gradients.grayFlat
            Box(Modifier.weight(1f)) {
                Box(Modifier.pressScale { menuOpen = true }) {
                    CheckInStatusRow(emoji = option.emoji, text = option.name, gradient = gradient, onGradient = onGradient)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CheckInMethod.entries.forEach { m ->
                        val opt = m.customCheckIn.incompleteOption
                        DropdownMenuItem(
                            text = { SmallText("${opt.emoji} ${opt.name}") },
                            onClick = { onMethodChange(m); menuOpen = false; Haptics.lightImpact() },
                            trailingIcon = {
                                if (m == currentMethod) Icon(sfSymbol("checkmark"), null, Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }

            // Amount stepper / "Add Amount".
            if (updatedAmount != null) {
                CheckInAmountStepper(
                    value = updatedAmount,
                    label = currentMethod.amountLabel,
                    onValueChange = onAmountChange,
                )
            } else {
                TinyTextButton(text = "Add Amount", icon = "plus.circle.fill") { onAmountChange(1) }
            }
        }

        // Hour ruler (iOS HourPickerView).
        HourPicker(initialTimestamp = timestamp, onGradient = onGradient) { onTimestampChange(it) }

        // Remove.
        Box(
            Modifier.fillMaxWidth().pressScale { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
            ) {
                Icon(
                    sfSymbol("minus.circle.fill"),
                    contentDescription = null,
                    tint = Clear30Colors.red1,
                    modifier = Modifier.size(14.dp),
                )
                TinyText("Remove", color = Clear30Colors.red1)
            }
        }
    }
}

// MARK: - Components

/**
 * CheckInStatusRow — iOS `CheckInStatus`: an emoji (or SF-symbol) chip on the
 * method/custom gradient, the status text, and trailing edit content.
 */
@Composable
fun CheckInStatusRow(
    emoji: String? = null,
    iconName: String? = null,
    text: String,
    gradient: Brush? = null,
    onGradient: Boolean = false,
    rightContent: @Composable () -> Unit = {},
) {
    val placeholder = iconName != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val chipBg: Brush? = when {
            placeholder -> null
            onGradient -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)))
            else -> gradient
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(if (chipBg != null) Modifier.background(chipBg) else Modifier)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (iconName != null) {
                Icon(
                    sfSymbol(iconName),
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(22.dp),
                )
            } else if (emoji != null) {
                SmallText(emoji)
            }
        }
        Spacer(Modifier.width(Dimens.cardSpacing / 2))
        SmallText(text)
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) { rightContent() }
    }
}

/** The tappable edit icon at a row's trailing edge (remove / smoked-again). */
@Composable
private fun EditIcon(icon: String, onGradient: Boolean, onClick: () -> Unit) {
    Icon(
        sfSymbol(icon),
        contentDescription = null,
        tint = if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.text.copy(alpha = 0.25f),
        modifier = Modifier.size(22.dp).pressScale(onClick = onClick),
    )
}

/** iOS `CheckInAmountPicker` — chevron stepper for "N hits/rips/mg/dabs". */
@Composable
fun CheckInAmountStepper(
    value: Int,
    label: String,
    min: Int = 1,
    onValueChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Clear30Colors.opacityGray)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Icon(
            sfSymbol("chevron.down"),
            contentDescription = "Less",
            tint = Clear30Colors.text.copy(alpha = if (value <= min) 0.25f else 0.5f),
            modifier = Modifier.size(14.dp).pressScale(haptic = false) {
                if (value > min) { Haptics.lightImpact(); onValueChange(value - 1) }
            },
        )
        TinyText("$value $label${if (value == 1) "" else "s"}", color = Clear30Colors.text)
        Icon(
            sfSymbol("chevron.up"),
            contentDescription = "More",
            tint = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp).pressScale(haptic = false) {
                Haptics.lightImpact(); onValueChange(value + 1)
            },
        )
    }
}

/** Tiny text + icon inline button (iOS `TinyTextButton`, no background). */
@Composable
private fun TinyTextButton(text: String, icon: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.alpha(0.75f).pressScale(haptic = false, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        TinyText(text)
        if (icon != null) {
            Icon(sfSymbol(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(10.dp))
        }
    }
}

/**
 * SubItemNotch — the rounded-L connector in front of each timestamp row (iOS
 * `SubItemNotch` / `RoundedLShape`). [heightOverride] stretches the vertical
 * leg up to the previous row.
 */
@Composable
private fun SubItemNotch(heightOverride: Dp? = null) {
    val notchSize = Dimens.cardSpacing
    val cornerRadius = Dimens.cornerRadius / 2
    val color = Clear30Colors.opacityGrayFlattened
    Canvas(Modifier.size(notchSize)) {
        val h = (heightOverride ?: notchSize).toPx()
        val s = notchSize.toPx()
        val r = cornerRadius.toPx().coerceAtMost(s)
        val path = Path().apply {
            moveTo(0f, s - h)               // extends above the box when overridden
            lineTo(0f, s - r)
            quadraticBezierTo(0f, s, r, s)  // rounded corner
            lineTo(s, s)
        }
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * DateBadge — the date badge at the top-left of the day card. iOS
 * (CheckInDayCard.swift `badge`) renders it as a full CardStyle pill — clear30
 * gradient, white outline at 0.5 — regardless of the day's logged state.
 */
@Composable
private fun DateBadge(date: PlainDate, isToday: Boolean) {
    Box(
        Modifier
            .cardStyle(
                gradient = Clear30Gradients.clear30,
                outlineGradient = Clear30Gradients.white,
                outlineOpacity = 0.5f,
                padding = false,
            )
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
    ) {
        SmallText(
            if (isToday) "Today" else "${MONTHS_SHORT[date.month - 1]} ${date.day}",
            color = Color.White,
        )
    }
}

// MARK: - Hour picker

/**
 * HourPicker — Compose port of the iOS `HourPickerView` infinite hour ruler:
 * drag horizontally to scrub the session's hour; ticks scroll under a fixed
 * center capsule showing the selected hour, snapping hour-by-hour with a
 * haptic per detent. The selected hour is written back onto the session's
 * timestamp (minutes/seconds preserved).
 */
@Composable
private fun HourPicker(
    initialTimestamp: Instant,
    onGradient: Boolean,
    onHourSelected: (Instant) -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val initialHour = remember(initialTimestamp) { initialTimestamp.toLocalDateTime(tz).hour }
    val hourWidth = 80.dp
    val density = LocalDensity.current
    val hourWidthPx = with(density) { hourWidth.toPx() }

    var offsetPx by remember(initialTimestamp) { mutableFloatStateOf(0f) }
    val hourDelta = (-offsetPx / hourWidthPx).roundToInt()
    val currentHour = ((initialHour + hourDelta) % 24 + 24) % 24

    var lastHapticHour by remember { mutableStateOf(initialHour) }
    LaunchedEffect(currentHour) {
        if (currentHour != lastHapticHour) {
            lastHapticHour = currentHour
            Haptics.mediumImpact()
        }
        onHourSelected(withHour(initialTimestamp, currentHour, tz))
    }

    val tickColor = if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray
    val accent = if (onGradient) Color.White else Clear30Colors.blue

    Box(
        Modifier.fillMaxWidth().height(80.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    // Snap to the nearest detent. Recomputed from the live state —
                    // this lambda is captured once, so composition-derived values
                    // would be stale here.
                    onDragEnd = { offsetPx = (offsetPx / hourWidthPx).roundToInt() * hourWidthPx },
                ) { _, dx -> offsetPx += dx }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The scrolling tick strip.
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height * 0.65f
            // Fractional remainder within the current detent.
            val rem = offsetPx + hourDelta * hourWidthPx
            val visibleHours = (size.width / hourWidthPx).toInt() / 2 + 2
            for (i in -visibleHours..visibleHours) {
                val hourX = centerX + i * hourWidthPx + rem
                // Hour tick.
                drawLine(
                    color = tickColor,
                    start = Offset(hourX, centerY - 20.dp.toPx()),
                    end = Offset(hourX, centerY + 20.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Sub-ticks between hours (30-min tick slightly taller).
                for (k in 1..5) {
                    val subX = hourX + k * hourWidthPx / 6f
                    val half = if (k == 3) 12.dp.toPx() else 7.dp.toPx()
                    drawLine(
                        color = tickColor.copy(alpha = tickColor.alpha * 0.5f),
                        start = Offset(subX, centerY - half),
                        end = Offset(subX, centerY + half),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Fixed center indicator: hour label + accent capsule.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (onGradient) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray),
            ) {
                TinyText(
                    formattedHour(currentHour),
                    color = if (onGradient) Color.White else Clear30Colors.blue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            Box(Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(99.dp)).background(accent))
        }
    }
}

// MARK: - Helpers

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "3 pm" (iOS `Int.formattedHour`). */
private fun formattedHour(hour: Int): String {
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val period = if (hour >= 12) "pm" else "am"
    return "$displayHour $period"
}

/** Nearest-hour time string for a session timestamp (iOS `nearestHourString`). */
private fun hourString(timestamp: Instant): String {
    val hour = timestamp.nearestHour.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return formattedHour(hour)
}

/** Rebuild [timestamp] with [hour], keeping the date + minutes/seconds. */
private fun withHour(timestamp: Instant, hour: Int, tz: TimeZone): Instant {
    val ldt = timestamp.toLocalDateTime(tz)
    return LocalDateTime(ldt.year, ldt.monthNumber, ldt.dayOfMonth, hour, ldt.minute, ldt.second).toInstant(tz)
}

/**
 * Latest consecutive days a custom check-in was completed, ending today (or
 * yesterday if today isn't logged yet) — iOS
 * `Program.latestConsecutiveDaysCompleted(checkIn:)`.
 */
private fun latestConsecutiveDaysCompleted(program: Program, checkIn: CustomCheckIn): Int {
    var d = PlainDate.from(now())
    fun completed(date: PlainDate): Boolean =
        program.dayInfo[date]?.loggedCheckIns?.firstOrNull { it.id == checkIn.id }?.completion == true
    if (!completed(d)) d = d.adding(days = -1)
    var streak = 0
    while (completed(d)) {
        streak++
        d = d.adding(days = -1)
    }
    return streak
}
