package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.clear30.data.CheckInLogger
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.util.now
import org.clear30.views.components.CalendarNodeFillStyle
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.StandardCalendarNode
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

private val MULTI_MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
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
 * MultiCheckInSheet — rebuilt to iOS `MultiCheckIn.swift` (W1): a paged MONTH
 * CALENDAR in a card. Missed days are tappable nodes (sober → clear30 gradient,
 * smoked → gray; other days low-opacity + disabled), a selected day gets the
 * full-width Smoked / Didn't-Smoke button pair, and the sheet is confirmed with
 * the full-width slide-to-confirm control (not a button). All days default
 * sober; the last missed day starts selected; the last month page starts shown.
 */
@Composable
fun MultiCheckInSheet(
    name: String,
    dates: List<PlainDate>,
    logger: CheckInLogger,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sober = remember { mutableStateMapOf<PlainDate, Boolean>().apply { dates.forEach { put(it, true) } } }
    var selected by remember { mutableStateOf(dates.lastOrNull()) }

    val months = remember(dates) {
        dates.map { it.year to it.month }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }
    val maxRows = remember(months) {
        months.maxOfOrNull { (y, m) -> monthRows(y, m) } ?: 5
    }
    val pagerState = rememberPagerState(initialPage = (months.size - 1).coerceAtLeast(0), pageCount = { months.size })

    fun submit() {
        scope.launch {
            logger.handleMultiCheckIn(dates.map { it to (sober[it] ?: true) })
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    // Scrollable so the slide-to-confirm control can't be pushed
                    // off-screen on short phones.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            ) {
                SmallText("Welcome back${if (name.isBlank()) "" else " $name"}.", color = Clear30Colors.text.copy(alpha = 0.5f))
                Heading3("Let's catch up on Check Ins!")

                Spacer(Modifier.height(Dimens.cardSpacing * 2))

                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // Month / selected-day header (iOS :97-110).
                        val (year, month) = months.getOrElse(pagerState.currentPage) { months.lastOrNull() ?: (2026 to 1) }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Heading3("${MULTI_MONTH_NAMES[month - 1]} ")
                            selected?.let { sel ->
                                if (sel.month == month && sel.year == year) {
                                    Heading3("${sel.day}${daySuffix(sel.day)}", color = Clear30Colors.text.copy(alpha = 0.5f))
                                }
                            }
                        }
                        Spacer(Modifier.height(Dimens.cardSpacing))

                        // Weekday labels (iOS :113-120).
                        Row(Modifier.fillMaxWidth()) {
                            MULTI_WEEKDAY_ABBR.forEach { label ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    TinyText(label, color = Clear30Colors.text.copy(alpha = 0.25f))
                                }
                            }
                        }
                        Spacer(Modifier.height(Dimens.cardSpacing / 2))

                        // Paged month calendar (iOS TabView .page).
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height((55 * maxRows).dp),
                        ) { page ->
                            val (py, pm) = months[page]
                            MultiCheckInMonth(
                                year = py,
                                month = pm,
                                checkInDays = dates,
                                sober = sober,
                                selected = selected,
                                onSelect = { day ->
                                    Haptics.lightImpact()
                                    selected = if (selected == day) null else day
                                },
                            )
                        }

                        // Month nav (iOS :142-162) — only with multiple months.
                        if (months.size > 1) {
                            Row(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                                MonthNavButton("chevron.left", enabled = pagerState.currentPage > 0, Modifier.weight(1f)) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                                MonthNavButton("chevron.right", enabled = pagerState.currentPage < months.size - 1, Modifier.weight(1f)) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            }
                        }

                        // Smoked / Didn't Smoke pair for the selected day (iOS :165-188).
                        selected?.let { sel ->
                            Row(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                                MultiCheckInOptionButton(
                                    "Smoked",
                                    selectedState = sober[sel] == false,
                                    useGradient = false,
                                    modifier = Modifier.weight(1f),
                                ) { sober[sel] = false }
                                MultiCheckInOptionButton(
                                    "Didn't Smoke",
                                    selectedState = sober[sel] != false,
                                    useGradient = true,
                                    modifier = Modifier.weight(1f),
                                ) { sober[sel] = true }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.cardSpacing * 2))

                SlideToConfirmCheckIn(onConfirm = { submit() })
                Spacer(Modifier.height(Dimens.cardSpacing / 2))
            }

            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.cardSpacing / 2)) {
                IconButton("xmark", onClick = onDismiss)
            }
        }
    }
}

/** One month grid page. Missed days are tappable; everything else is inert. */
@Composable
private fun MultiCheckInMonth(
    year: Int,
    month: Int,
    checkInDays: List<PlainDate>,
    sober: Map<PlainDate, Boolean>,
    selected: PlainDate?,
    onSelect: (PlainDate) -> Unit,
) {
    val leading = LocalDate(year, month, 1).dayOfWeek.isoDayNumber - 1 // Mon-start
    val days = daysInMonth(year, month)
    Column {
        var day = 1
        while (day <= days) {
            Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically) {
                for (col in 0 until 7) {
                    val cellIndex = (day - 1) + leading
                    val isBlank = (day == 1 && col < leading) || day > days
                    if (isBlank && day == 1 && col < leading) {
                        Box(Modifier.weight(1f))
                        continue
                    }
                    if (day > days) {
                        Box(Modifier.weight(1f))
                        continue
                    }
                    val date = PlainDate(year, month, day)
                    val isCheckInDay = date in checkInDays
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StandardCalendarNode(
                            label = day,
                            enabled = isCheckInDay,
                            selected = selected == date,
                            fillStyle = when {
                                isCheckInDay && sober[date] != false -> CalendarNodeFillStyle.Gradient(Clear30Gradients.clear30)
                                isCheckInDay -> CalendarNodeFillStyle.Gray
                                else -> CalendarNodeFillStyle.LowOpacity
                            },
                            onTap = if (isCheckInDay) ({ onSelect(date) }) else null,
                        )
                    }
                    day++
                }
            }
        }
    }
}

/** Full-width chevron month-nav button (iOS TinyTextButton stretch). */
@Composable
private fun MonthNavButton(icon: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Clear30Colors.opacityGray)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            sfSymbol(icon),
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Smoked / Didn't-Smoke choice button (iOS `MultiCheckInOptionButton`). */
@Composable
private fun MultiCheckInOptionButton(
    text: String,
    selectedState: Boolean,
    useGradient: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.cornerRadius)
    Box(
        modifier
            .clip(shape)
            .then(
                if (selectedState) {
                    Modifier.background(if (useGradient) Clear30Gradients.clear30 else Clear30Gradients.grayFlat)
                } else {
                    Modifier.border(3.dp, Clear30Colors.opacityGray, shape).alpha(0.5f)
                },
            )
            .clickable { Haptics.mediumImpact(); onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        SmallText(text, color = if (selectedState && useGradient) Color.White else Clear30Colors.text)
    }
}

/**
 * Slide-to-confirm (iOS `SlideToConfirmCheckIn`): drag the 🤩 thumb across the
 * track; at ≥85% it locks, celebrates, and fires [onConfirm] after 0.55s.
 */
@Composable
private fun SlideToConfirmCheckIn(onConfirm: () -> Unit) {
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(65.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Clear30Colors.opacityGray),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val thumbPx = with(density) { 55.dp.toPx() }
        val padPx = with(density) { 5.dp.toPx() }
        val maxDrag = (constraints.maxWidth - thumbPx - 2 * padPx).coerceAtLeast(1f)

        SmallText(
            if (done) "Checked In!" else "Slide to Confirm",
            modifier = Modifier.align(Alignment.Center),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((padPx + drag).roundToInt(), 0) }
                .size(55.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Clear30Gradients.clear30)
                .pointerInput(done) {
                    if (!done) {
                        detectHorizontalDragGestures(
                            onDragEnd = { if (!done) drag = 0f },
                            onDragCancel = { if (!done) drag = 0f },
                        ) { change, amount ->
                            change.consume()
                            drag = (drag + amount).coerceIn(0f, maxDrag)
                            if (drag >= maxDrag * 0.85f && !done) {
                                done = true
                                drag = maxDrag
                                Haptics.successHeavy()
                                scope.launch {
                                    delay(550)
                                    onConfirm()
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            SmallText("🤩")
        }
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
}

private fun monthRows(year: Int, month: Int): Int {
    val leading = LocalDate(year, month, 1).dayOfWeek.isoDayNumber - 1
    return ((leading + daysInMonth(year, month)) + 6) / 7
}

private fun daySuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}
