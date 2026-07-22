package org.clear30.views.existinguser.profile

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.R
import org.clear30.data.Clear30Store
import org.clear30.data.model.AppMode
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.syncProgramState
import org.clear30.data.supabase.updateYourWhy
import org.clear30.views.components.Clear30Alert
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Clear30Sheet
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.now

/**
 * ProfileTab — ported 1:1 from iOS `Profile.swift` (`lifeLayout`).
 *
 * Order mirrors iOS exactly:
 *   Header (emoji • name • gear)
 *   "Overall Progress" → Your Why card → DopamineTimer → Health → Achievements
 *   "Your Program"     → Program card  → Days-without-weed + New Break
 *   Journal & Previous Breaks browse buttons
 *
 * The gear opens the settings overlay (iOS `viewModel.activeSheet = .settings`),
 * which mirrors the iOS SettingsView rows 1:1 (§17-Q18).
 */
@Composable
fun ProfileTab(
    userInfo: UserInfo,
    program: Program,
    journalEntries: JournalEntries,
    onSignOut: () -> Unit,
    onOpenPostAssessment: (() -> Unit)? = null,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showJournal by remember { mutableStateOf(false) }
    var showPreviousBreaks by remember { mutableStateOf(false) }
    var showNewBreak by remember { mutableStateOf(false) }
    // Hidden dev tool (K52): 10 taps on the name opens the timeline shifter.
    var showDevSheet by remember { mutableStateOf(false) }
    var devTaps by remember { mutableIntStateOf(0) }
    // Tapped health category + whether the tap caught a fresh unlock (iOS
    // handleHealthProgress: `hasNew` is captured BEFORE lastVisited is stamped —
    // Swift passes a struct copy; here we capture the flag explicitly).
    var healthTimeline by remember {
        mutableStateOf<Pair<org.clear30.data.model.ProgramHealthProgress, Boolean>?>(null)
    }
    val scope = rememberCoroutineScope()
    // Bumped after a program mutation (new break) so the cards below recompute —
    // `program` is a plain model, not observable.
    var refresh by remember { mutableIntStateOf(0) }

    // Make sure the health timeline content is loaded (N2 — AllTabs also fetches
    // on app load; this covers the race when Profile renders first) and bump
    // [refresh] so the gauges/timeline re-read program.healthProgress.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            if (org.clear30.data.HealthDataHandler.ensureHealthData(userInfo, program)) refresh++
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            @Suppress("UNUSED_EXPRESSION") refresh  // subscribe so new-break updates the cards
            // Header — emoji • name • gear (iOS `header`)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                UserEmojiPickerButton(userInfo)
                Heading1(
                    userInfo.name.ifBlank { "You" },
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        devTaps++
                        if (devTaps >= 10) { devTaps = 0; showDevSheet = true }
                    },
                )
                Spacer(Modifier.weight(1f))
                CircleIconButton("gearshape.fill") { showSettings = true }
            }

            if (showDevSheet) {
                TimelineDevSheet(program, onDismiss = { showDevSheet = false }) { refresh++ }
            }

            // Day 0 of an upcoming break (iOS Profile.displayMode →
            // .programBreakStartSoon → programBreakStartSoonLayout): the page
            // shows ONLY the green break-start countdown plus the Journal /
            // Previous Breaks buttons — no why card, timer, health,
            // achievements, program card, calendar, or stat cards.
            val startSoonBreak = remember(refresh) {
                program.currentBreakNotStartSoon?.takeIf {
                    !program.inCoreProgram && it.currentBreakDay <= 0
                }
            }
            if (startSoonBreak != null) {
                BreakStartCountdown(program, userInfo, startSoonBreak) { refresh++ }
            } else {
            // ===== Overall Progress =====
            SectionLabel("Overall Progress")
            UserWhyCard(userInfo)
            DopamineTimer(program, userInfo)
            HealthCardsRow(program, userInfo, revision = refresh) { hp ->
                healthTimeline = hp to hp.hasNew
                // iOS Profile.handleHealthProgress: stamp the visit so the
                // hasNew highlight clears after this open.
                hp.lastVisited = now()
                refresh++
                scope.launch { runCatching { Clear30Store.save(program) } }
            }
            AchievementsSection(userInfo, program)

            // ===== Your Program / Your Break =====
            val calendarBreak = program.currentBreak
            SectionLabel(if (calendarBreak != null) "Your Break" else "Your Program")
            // iOS lifeLayout (Profile.swift:181-190): a pending post-assessment
            // REPLACES the program card in this slot.
            val postAssessmentText = program.postAssessmentCardText
            if (calendarBreak == null && postAssessmentText != null && onOpenPostAssessment != null) {
                org.clear30.views.existinguser.postassessment.PostAssessmentPopupCard(
                    text = postAssessmentText,
                    onClick = onOpenPostAssessment,
                )
            } else {
                ProgramCard(program, userInfo, revision = refresh, onChanged = { refresh++ })
            }
            // Calendar — iOS branches: in-break → the 30-day snake calendar
            // (Profile.swift programBreakLayout), Life → the Roman month calendar
            // (lifeLayout). Both sit between the program card and the stat cards.
            if (calendarBreak != null) {
                ProfileSnakeCalendar(
                    height = 275.dp,
                    program = program,
                    programBreak = calendarBreak,
                    revision = refresh,
                )
            } else {
                ProfileCalendar(program)
            }
            // iOS: if a started break has computable savings, show Days + Money saved
            // with a full-width New Break button below; otherwise Days + New Break.
            val lastBreak = program.lastBreak
            val moneySaved = lastBreak?.takeIf { it.startDate <= now() }
                ?.let { program.getTotalMoneySavedOverBreak(it) }
            // "Days without weed" is break-scoped in the break layout (iOS
            // Profile.swift:148 numDaysSober(programBreak:)): day 0 / pre-break sober
            // check-ins don't count, so it reads 0 on the first day of a fresh break.
            // Life layout (no active break) keeps the all-time count.
            val daysWithoutWeed = calendarBreak?.let { maxOf(0, program.numDaysSober(it)) }
                ?: maxOf(0, program.numDaysSober)
            // Starting a new break is only offered in the Life program — i.e. when
            // there's no current break (iOS shows "New Break" solely in lifeLayout,
            // never during an active Clear30). Adolescent mode never starts breaks.
            val canStartBreak = program.currentBreak == null && userInfo.mode != AppMode.ADOLESCENT
            if (moneySaved != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    ProfileDaysWithoutWeed(daysWithoutWeed, Modifier.weight(1f))
                    ProfileMoneySaved(moneySaved, program, revision = refresh, modifier = Modifier.weight(1f)) { refresh++ }
                }
                if (canStartBreak) {
                    ProfileNewBreakButton(Modifier.fillMaxWidth()) { showNewBreak = true }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    ProfileDaysWithoutWeed(daysWithoutWeed, Modifier.weight(1f))
                    if (canStartBreak) {
                        ProfileNewBreakButton(Modifier.weight(1f)) { showNewBreak = true }
                    }
                }
            }

            // In-break management (Restart / Change start date / End) — renders
            // nothing in Life. Wired to ProgramTimelineHandler.
            ProfileBreakOptions(program, userInfo, revision = refresh) { refresh++ }
            }

            // ===== Journal & Previous Breaks (each opens its own full page) =====
            BrowseButton("Journal", "book.closed.fill", Clear30Gradients.journals) {
                showJournal = true
            }
            if (userInfo.mode != AppMode.ADOLESCENT) {
                BrowseButton("Previous Breaks", "archivebox.fill", Clear30Gradients.symptomCard) {
                    showPreviousBreaks = true
                }
            }
        }

        if (showNewBreak) {
            org.clear30.views.existinguser.profile.newbreak.BreakAssessmentFlow(
                userInfo = userInfo,
                program = program,
                onDismiss = { showNewBreak = false },
                onCreated = { refresh++; showNewBreak = false },
            )
        }

        // Full-page overlays (iOS pushes these onto the navigation stack) — slide
        // in from the right + fade, and slide back out, like a nav push.
        PageOverlay(showSettings) {
            ProfileSettingsOverlay(userInfo, program, onSignOut) { showSettings = false }
        }
        PageOverlay(showJournal) {
            JournalSection(journalEntries, userInfo, program) { showJournal = false }
        }
        PageOverlay(showPreviousBreaks) {
            PreviousBreaksSection(program) { showPreviousBreaks = false }
        }
        // Health card tap → that category's timeline detail (iOS pushes
        // NavigationDestination.healthProgressTimeline(healthProgress:)).
        PageOverlay(healthTimeline != null) {
            healthTimeline?.let { (hp, highlight) ->
                HealthTimelinePage(hp, userInfo, highlightCurrent = highlight) { healthTimeline = null }
            }
        }
    }
}

/** A full-screen page that slides in from the right + fades (iOS-style push). */
@Composable
private fun PageOverlay(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInHorizontally(animationSpec = tween(300)) { it } +
            androidx.compose.animation.fadeIn(tween(300)),
        exit = androidx.compose.animation.slideOutHorizontally(animationSpec = tween(260)) { it } +
            androidx.compose.animation.fadeOut(tween(260)),
    ) { content() }
}

// MARK: - Section label (iOS `SmallText(...).opacity(0.5)`)

@Composable
private fun SectionLabel(text: String) {
    SmallText(text, color = Clear30Colors.text.copy(alpha = 0.5f))
}

// MARK: - Circle icon button (iOS `CircleIconButton`)

@Composable
private fun CircleIconButton(icon: String, onClick: () -> Unit) {
    // iOS CircleIconButton: 37.5 circle, 18 icon (xmark shrunk to 15).
    Box(
        Modifier.size(37.5.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Clear30Colors.opacityGray)
            .pressScale { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sfSymbol(icon),
            contentDescription = icon,
            tint = Clear30Colors.text,
            modifier = Modifier.size(if (icon == "xmark") 15.dp else 18.dp),
        )
    }
}

// MARK: - Your Why (iOS `UserWhyProfile`)

@Composable
private fun UserWhyCard(userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var why by remember { mutableStateOf(userInfo.userWhy) }
    var showEditor by remember { mutableStateOf(false) }

    if (why.isNullOrBlank()) {
        // "Your Why" add button (gray, dashed-feel) — tap to write it.
        Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { showEditor = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    sfSymbol("plus.circle.fill"),
                    contentDescription = null,
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(Dimens.cardSpacing / 2))
                SmallText("Your Why", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    } else {
        Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { showEditor = true }, gradient = Clear30Gradients.clear30) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallText("Your Why", color = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        sfSymbol("pencil"),
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
                SmallText(why ?: "", color = Color.White)
            }
        }
    }

    if (showEditor) {
        var draft by remember { mutableStateOf(why ?: "") }
        Clear30Alert(
            onDismissRequest = { showEditor = false },
            title = "Your Why",
            message = "What's your reason for taking a break? You'll see it here to stay grounded.",
            confirmLabel = "Save",
            onConfirm = {
                val trimmed = draft.trim()
                userInfo.userWhy = trimmed.ifBlank { null }
                why = userInfo.userWhy
                scope.launch {
                    Clear30Store.save(userInfo)
                    // Sync to backend (iOS ProfileCards.swift `updateYourWhy`) —
                    // iOS sends the raw text, empty string included, on clear.
                    SupabaseController.updateYourWhy(trimmed)
                }
                showEditor = false
            },
            dismissLabel = "Cancel",
        ) {
            androidx.compose.material3.OutlinedTextField(
                draft, { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("My why is…") },
            )
        }
    }
}

// MARK: - Program card (iOS `ProgramCard`, Life branch)

@Composable
private fun ProgramCard(program: Program, userInfo: UserInfo, revision: Int, onChanged: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var showSwitchConfirm by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var detailInfo by remember { mutableStateOf<org.clear30.data.model.ProgramDetailSheetInfo?>(null) }
    @Suppress("UNUSED_EXPRESSION") refresh // read so toggles recompose
    // `program` is mutated in place, so `revision` is what invalidates this card
    // after break mutations — keying the read on it keeps the param genuinely
    // used (unused params are excluded from the skip comparison).
    val currentBreak = remember(revision, refresh) { program.currentBreak }

    // iOS info button → detail sheet about the current program / break; during a
    // break the sheet also offers "End Break" (iOS sheetInfoView).
    detailInfo?.let { info ->
        Clear30Alert(
            onDismissRequest = { detailInfo = null },
            title = info.title,
            message = info.description,
            confirmLabel = "Got it",
            onConfirm = { detailInfo = null },
            dismissLabel = if (currentBreak != null) "End Break" else null,
            onDismissAction = { detailInfo = null; showEndConfirm = true },
        )
    }

    // iOS `handleEnd()` (ProfileCards.swift:292-315): yes/no alert → endBreak →
    // the Life program.
    if (showEndConfirm && currentBreak != null) {
        Clear30Alert(
            onDismissRequest = { showEndConfirm = false },
            title = "End ${currentBreak.name}?",
            message = "This will end your break and put you in The Life Program.",
            confirmLabel = "End break",
            destructive = true,
            onConfirm = {
                showEndConfirm = false
                // App-lifetime scope: endBreak nulls currentBreak in place, so
                // this card recomposes mid-flight — a composition-tied scope
                // would cancel the mutation halfway through.
                org.clear30.Clear30Application.appScope.launch {
                    try {
                        org.clear30.data.LoadingCoordinator.tracked {
                            val error = org.clear30.data.ProgramTimelineHandler.endBreak(program)
                            if (error != null) org.clear30.data.AlertHandler.error(message = error)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("ProgramCard", "endBreak failed", t)
                    } finally {
                        refresh++
                        onChanged()
                    }
                }
            },
            dismissLabel = "Cancel",
        )
    }

    // iOS `switchCoreProgram()` (ProfileCards.swift:317-336): yes/no alert →
    // `program.switchCore` under the global loading scrim — submits the
    // life-short LO-Use-State assessment and re-fetches the new mode's Life
    // content; errors surface through the master alert.
    if (showSwitchConfirm) {
        val target = if (program.coreModeration) "weed free" else "moderation"
        Clear30Alert(
            onDismissRequest = { showSwitchConfirm = false },
            title = "Switch to $target?",
            message = "Do you want to switch to the $target program?",
            confirmLabel = "Switch",
            onConfirm = {
                showSwitchConfirm = false
                // App-lifetime scope — the switch submits an assessment and
                // refetches content; a composition-tied scope would cancel
                // it mid-flight on a tab switch, leaving the backend flipped
                // but the local mode unswitched.
                org.clear30.Clear30Application.appScope.launch {
                    try {
                        org.clear30.data.LoadingCoordinator.tracked {
                            val error = org.clear30.data.ProgramTimelineHandler.switchCore(
                                program,
                                clientName = userInfo.name,
                            )
                            if (error != null) org.clear30.data.AlertHandler.error(message = error)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("ProgramCard", "switchCore failed", t)
                    } finally {
                        refresh++
                    }
                }
            },
            dismissLabel = "Cancel",
        )
    }

    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            if (currentBreak != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SmallText(currentBreak.name)
                    Spacer(Modifier.weight(1f))
                    // iOS shows the ⓘ only when the BREAK has sheet info — the
                    // start-soon bridge has none, and falling back to
                    // program.detailSheetInfo displayed the Life sheet on the
                    // break card.
                    currentBreak.detailSheetInfo?.let { sheet ->
                        InfoButton { detailInfo = sheet }
                    }
                }
                currentBreak.breakDescription?.let {
                    // W13: white on the gradient card (Clear30Colors.text is
                    // black in light mode).
                    SmallText(it, color = Color.White.copy(alpha = 0.5f))
                }
                // iOS ProfileCards:143-158: "Day N" only while N is inside the
                // break (dayValid), plus the start→end date-range badge.
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    val day = currentBreak.currentBreakDay
                    val dayValid = day <= currentBreak.type.raw
                    if (dayValid) Badge("Day $day", gradientBackground = false)
                    Badge(
                        "${shortMonthDate(currentBreak.startDate.adding(days = 1))} to " +
                            shortMonthDate(currentBreak.endDate.adding(days = -1)),
                        gradientBackground = dayValid,
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SmallText("Life")
                    Spacer(Modifier.weight(1f))
                    program.detailSheetInfo?.let { sheet ->
                        InfoButton { detailInfo = sheet }
                    }
                }
                program.programDescription?.let {
                    // W13: white on the gradient card.
                    SmallText(it, color = Color.White.copy(alpha = 0.5f))
                }
                if (userInfo.mode != AppMode.ADOLESCENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        Badge(
                            if (program.coreModeration) "Moderation" else "Weed free",
                            gradientBackground = false,
                        )
                        Badge(
                            "Switch to ${if (program.coreModeration) "weed free" else "moderation"}",
                            gradientBackground = true,
                            icon = "arrow.triangle.2.circlepath",
                        ) {
                            showSwitchConfirm = true
                        }
                    }
                } else {
                    Badge("Adolescent", gradientBackground = false)
                }
            }
        }
    }
}

/** iOS `Date.shortMonthDate` — "Jul 22nd" (MMM d + ordinal suffix). */
private fun shortMonthDate(instant: kotlinx.datetime.Instant): String {
    val d = org.clear30.data.model.PlainDate.from(instant)
    val suffix = when {
        d.day in 11..13 -> "th"
        d.day % 10 == 1 -> "st"
        d.day % 10 == 2 -> "nd"
        d.day % 10 == 3 -> "rd"
        else -> "th"
    }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[d.month - 1]} ${d.day}$suffix"
}

/** iOS `detailedInfoButton` — white ⓘ on the gradient program card. */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    Icon(
        sfSymbol("info.circle.fill"),
        contentDescription = "Info",
        tint = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.size(17.dp).pressScale { onClick() },
    )
}

@Composable
private fun Badge(
    text: String,
    gradientBackground: Boolean,
    icon: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val fg = if (gradientBackground) Color.White else Color.Black
    var mod = Modifier
        .cardStyle(
            color = Color.White,
            gradient = if (gradientBackground) Clear30Gradients.clear30 else null,
            outlineGradient = if (gradientBackground) Clear30Gradients.white else Clear30Gradients.clear30,
            outlineWidth = 2.dp,
            outlineOpacity = 0.5f,
            padding = false,
        )
        .padding(horizontal = Dimens.chipHorizontalPadding, vertical = Dimens.chipVerticalPadding)
    if (onClick != null) mod = mod.pressScale { onClick() }

    Row(mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        TinyText(text, color = fg)
        if (icon != null) {
            Icon(sfSymbol(icon), contentDescription = null, tint = fg.copy(alpha = 0.5f), modifier = Modifier.size(10.dp))
        }
    }
}

// MARK: - Days without weed (iOS `ProfileDaysWithoutWeed`)

@Composable
private fun ProfileDaysWithoutWeed(days: Int, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
        ) {
            Box(
                Modifier.size(50.dp)
                    .clip(RoundedCornerShape((Dimens.cornerRadius.value * 0.75f).dp))
                    .background(if (days == 0) Clear30Gradients.gray else Clear30Gradients.clear30),
                contentAlignment = Alignment.Center,
            ) {
                Heading2("$days", color = if (days == 0) Clear30Colors.text.copy(alpha = 0.5f) else Color.White)
            }
            // iOS uses spacing -0.25*cardSpacing to stack the three words tightly.
            Column(verticalArrangement = Arrangement.spacedBy((-Dimens.cardSpacing.value * 0.25f).dp)) {
                TinyText("day${if (days == 1) "" else "s"}")
                TinyText("without", color = Clear30Colors.text.copy(alpha = 0.25f))
                TinyText("weed", color = Clear30Colors.text.copy(alpha = 0.25f))
            }
        }
    }
}

// MARK: - Money saved (iOS `ProfileMoneySaved`)

@Composable
private fun ProfileMoneySaved(
    moneySaved: Int,
    program: Program,
    revision: Int,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    @Suppress("UNUSED_EXPRESSION") revision // re-read after an adjustment saves

    // iOS handleEditMoneySaved: a numeric prompt; the desired total is stored
    // as an adjustment over the auto-calculated savings on the current break.
    if (showEditor) {
        var draft by remember { mutableStateOf("$moneySaved") }
        Clear30Alert(
            onDismissRequest = { showEditor = false },
            title = "Edit Money Saved",
            message = "Enter your total dollars saved.",
            confirmLabel = "Save",
            onConfirm = {
                val desired = draft.trim().toIntOrNull()
                val currentBreak = program.currentBreak ?: program.lastBreak
                if (desired != null && desired >= 0 && currentBreak != null) {
                    val auto = program.getAutoCalculatedMoneySavedOverBreak(currentBreak) ?: 0
                    currentBreak.moneySavedAdjustment = desired - auto
                    // Persist locally AND push to the server right away, on the
                    // app scope so it survives leaving Profile. Without the push
                    // the adjustment lived only in local state and was wiped on
                    // sign-out before the next foreground sync (iOS pushes here).
                    org.clear30.Clear30Application.appScope.launch {
                        Clear30Store.save(program)
                        SupabaseController.syncProgramState(program)
                    }
                    onChanged()
                }
                showEditor = false
            },
            dismissLabel = "Cancel",
        ) {
            androidx.compose.material3.OutlinedTextField(
                draft, { draft = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Amount") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                singleLine = true,
            )
        }
    }

    Clear30Card(modifier = modifier.pressScale { showEditor = true }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
        ) {
            RewardPigWithDollars(size = 46.dp)
            Column(verticalArrangement = Arrangement.spacedBy((-Dimens.cardSpacing.value * 0.25f).dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape((Dimens.cornerRadius.value / 2f).dp))
                        .background(Clear30Colors.opacityGray)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    SmallText("\$$moneySaved")
                }
                TinyText("saved", color = Clear30Colors.text.copy(alpha = 0.25f))
            }
        }
    }
}

/**
 * RewardPigWithDollars (CheckInRewardViews.swift) — the happy pig with the
 * single-dollar / multi-dollar / coins layers around it (all shown for the
 * money-saved card). Sizes and offsets are the iOS fractions of the frame.
 */
@Composable
private fun RewardPigWithDollars(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.single_dollar), contentDescription = null,
            modifier = Modifier.size(size * 0.75f).offset(x = -size * 0.35f, y = size * 0.45f),
        )
        Image(
            painterResource(R.drawable.multi_dollar), contentDescription = null,
            modifier = Modifier.size(size).offset(x = size * 0.35f, y = size * 0.35f),
        )
        Image(
            painterResource(R.drawable.coins), contentDescription = null,
            modifier = Modifier.size(size * 0.65f).offset(x = -size * 0.5f),
        )
        Image(painterResource(R.drawable.pig_happy), contentDescription = null, modifier = Modifier.size(size))
    }
}

// MARK: - New break button (iOS `ProfileNewBreakButton`)

@Composable
private fun ProfileNewBreakButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.pressScale { onClick() }) {
        Row(
            Modifier.fillMaxWidth().height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
        ) {
            Box(
                Modifier.size(38.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Clear30Colors.opacityGray),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    sfSymbol("plus"),
                    contentDescription = null,
                    tint = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy((-Dimens.cardSpacing.value * 0.25f).dp)) {
                TinyText("New")
                TinyText("Break")
            }
        }
    }
}

// MARK: - Browse button (iOS `BrowseButton`)

@Composable
private fun BrowseButton(title: String, icon: String, gradient: Brush, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .height(100.dp)
            .cardStyle(gradient = gradient)
            .pressScale { onClick() },
    ) {
        Icon(
            sfSymbol(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
        )
        SmallText(title, color = Color.White, modifier = Modifier.align(Alignment.BottomStart))
    }
}

// MARK: - Settings overlay (gear → iOS settings sheet)

@Composable
private fun ProfileSettingsOverlay(
    userInfo: UserInfo,
    program: Program,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    // No statusBarsPadding: renders inside AllTabs' Scaffold content, which is
    // already inset below the status bar.
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading1("Settings")
                Spacer(Modifier.weight(1f))
                CircleIconButton("xmark") { onClose() }
            }
            // Rows mirror iOS SettingsView 1:1 (§17-Q18) — the Android-only
            // symptoms / start-date / share-calendar rows were cut (the start-date
            // picker also desynced the timeline, B5).
            SettingsSection(userInfo, program, onSignOut, onClose)
        }
    }
}

/**
 * Hidden timeline dev tool (K52) — 10 taps on the Profile name opens this sheet.
 * Shifts the whole program/current day via `ProgramTimelineHandler.adjustBreakTime`
 * (the same mechanism as "Change Break Start Date"), then persists + syncs.
 */
@Composable
private fun TimelineDevSheet(program: Program, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentDay = program.currentBreak?.currentBreakDay
    var jumpTo by remember { mutableStateOf("") }
    fun shift(days: Int) {
        if (days == 0) return
        scope.launch {
            org.clear30.data.ProgramTimelineHandler.adjustBreakTime(program, days)
            Clear30Store.save(program)
            runCatching { org.clear30.data.supabase.SupabaseController.syncProgramState(program) }
            onChanged()
        }
    }
    Clear30Sheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("🛠 Timeline (dev)")
            SmallText(
                if (currentDay != null) "Current break day: $currentDay" else "No active break — start one first.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                listOf(-7, -1, 1, 7).forEach { d ->
                    DefaultButton(if (d > 0) "+$d" else "$d", gradient = Clear30Gradients.clear30, modifier = Modifier.weight(1f)) { shift(d) }
                }
            }
            if (currentDay != null) {
                androidx.compose.material3.OutlinedTextField(
                    jumpTo, { jumpTo = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { androidx.compose.material3.Text("Jump to break day N") },
                    singleLine = true,
                )
                DefaultButton("Jump", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                    jumpTo.toIntOrNull()?.let { target -> shift(target - currentDay) }
                    jumpTo = ""
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing * 2))
        }
    }
}
