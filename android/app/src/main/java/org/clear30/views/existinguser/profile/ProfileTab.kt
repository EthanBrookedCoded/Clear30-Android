package org.clear30.views.existinguser.profile

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.clear30.data.supabase.updateYourWhy
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
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
 * which also hosts the Android-only management rows (symptoms, start-date,
 * share calendar) so the main profile body stays faithful to iOS.
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
                Heading1(userInfo.name.ifBlank { "You" })
                Spacer(Modifier.weight(1f))
                CircleIconButton("gearshape.fill") { showSettings = true }
            }

            // Pending post-assessment card (iOS Profile.swift:181-184).
            val postAssessmentText = program.postAssessmentCardText
            if (postAssessmentText != null && onOpenPostAssessment != null) {
                org.clear30.views.existinguser.postassessment.PostAssessmentPopupCard(
                    text = postAssessmentText,
                    onClick = onOpenPostAssessment,
                )
            }

            // ===== Overall Progress =====
            SectionLabel("Overall Progress")
            UserWhyCard(userInfo)
            DopamineTimer(program, userInfo)
            HealthCardsRow(program, userInfo) { hp ->
                healthTimeline = hp to hp.hasNew
                // iOS Profile.handleHealthProgress: stamp the visit so the
                // hasNew highlight clears after this open.
                hp.lastVisited = now()
                refresh++
                scope.launch { runCatching { Clear30Store.save(program) } }
            }
            AchievementsSection(userInfo)

            // ===== Your Program / Your Break =====
            val calendarBreak = program.currentBreak
            SectionLabel(if (calendarBreak != null) "Your Break" else "Your Program")
            ProgramCard(program, userInfo)
            // Calendar — iOS branches: in-break → the 30-day snake calendar
            // (Profile.swift programBreakLayout), Life → the Roman month calendar
            // (lifeLayout). Both sit between the program card and the stat cards.
            if (calendarBreak != null) {
                ProfileSnakeCalendar(height = 275.dp, program = program, programBreak = calendarBreak)
            } else {
                ProfileCalendar(program)
            }
            // iOS: if a started break has computable savings, show Days + Money saved
            // with a full-width New Break button below; otherwise Days + New Break.
            val lastBreak = program.lastBreak
            val moneySaved = lastBreak?.takeIf { it.startDate <= now() }
                ?.let { program.getTotalMoneySavedOverBreak(it) }
            // Starting a new break is only offered in the Life program — i.e. when
            // there's no current break (iOS shows "New Break" solely in lifeLayout,
            // never during an active Clear30). Adolescent mode never starts breaks.
            val canStartBreak = program.currentBreak == null && userInfo.mode != AppMode.ADOLESCENT
            if (moneySaved != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    ProfileDaysWithoutWeed(program.numDaysSober, Modifier.weight(1f))
                    ProfileMoneySaved(moneySaved, Modifier.weight(1f))
                }
                if (canStartBreak) {
                    ProfileNewBreakButton(Modifier.fillMaxWidth()) { showNewBreak = true }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    ProfileDaysWithoutWeed(program.numDaysSober, Modifier.weight(1f))
                    if (canStartBreak) {
                        ProfileNewBreakButton(Modifier.weight(1f)) { showNewBreak = true }
                    }
                }
            }

            // In-break management (Restart / Change start date / End) or the Day-0
            // "Start break now" — renders nothing in Life. Wired to ProgramTimelineHandler.
            ProfileBreakOptions(program, userInfo) { refresh++ }

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
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("Your Why") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Text("What's your reason for taking a break? You'll see it here to stay grounded.")
                    androidx.compose.material3.OutlinedTextField(
                        draft, { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("My why is…") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } },
        )
    }
}

// MARK: - Program card (iOS `ProgramCard`, Life branch)

@Composable
private fun ProgramCard(program: Program, userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var showSwitchConfirm by remember { mutableStateOf(false) }
    var detailInfo by remember { mutableStateOf<org.clear30.data.model.ProgramDetailSheetInfo?>(null) }
    @Suppress("UNUSED_EXPRESSION") refresh // read so toggles recompose
    val currentBreak = program.currentBreak

    // iOS info button → detail sheet about the current program / break.
    detailInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { detailInfo = null },
            title = { Text(info.title) },
            text = { Text(info.description) },
            confirmButton = { TextButton(onClick = { detailInfo = null }) { Text("Got it") } },
        )
    }

    // iOS `switchCoreProgram()` shows a yes/no alert before flipping the core
    // program. (The backend `updateLifeProgram` assessment submit + message
    // refetch is a follow-up — this flips the mode locally + persists.)
    if (showSwitchConfirm) {
        val target = if (program.coreModeration) "weed free" else "moderation"
        AlertDialog(
            onDismissRequest = { showSwitchConfirm = false },
            title = { Text("Switch to $target?") },
            text = { Text("Do you want to switch to the $target program?") },
            confirmButton = {
                TextButton(onClick = {
                    program.coreModeration = !program.coreModeration
                    refresh++
                    scope.launch { Clear30Store.save(program) }
                    showSwitchConfirm = false
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { showSwitchConfirm = false }) { Text("Cancel") } },
        )
    }

    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            if (currentBreak != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SmallText(currentBreak.name)
                    Spacer(Modifier.weight(1f))
                    InfoButton { detailInfo = program.detailSheetInfo }
                }
                currentBreak.breakDescription?.let {
                    SmallText(it, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    Badge("Day ${currentBreak.currentBreakDay}", gradientBackground = false)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SmallText("Life")
                    Spacer(Modifier.weight(1f))
                    InfoButton { detailInfo = program.detailSheetInfo }
                }
                SmallText(
                    program.programDescription ?: "Your long term support program.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
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
        .padding(horizontal = 10.dp, vertical = 5.dp)
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
private fun ProfileMoneySaved(moneySaved: Int, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
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
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading1("Settings")
                Spacer(Modifier.weight(1f))
                CircleIconButton("xmark") { onClose() }
            }
            SettingsSection(userInfo, program, onSignOut, onClose)
            SymptomsSection(userInfo)
            ProgramStartDatePicker(program, userInfo)
            ShareCalendarRow(program, userInfo)
        }
    }
}
