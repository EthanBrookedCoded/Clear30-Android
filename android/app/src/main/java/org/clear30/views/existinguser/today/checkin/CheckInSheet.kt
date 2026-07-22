package org.clear30.views.existinguser.today

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.clear30.data.CheckInLogger
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.VariableReward
import org.clear30.data.model.CheckInDefaults
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.CustomCheckInOption
import org.clear30.data.model.LoggedCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.util.now
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * CheckInSheet — ported from iOS `CheckIn.swift` / `CheckInFullscreen.swift`.
 *
 * A fullscreen "Check in for / Today" screen. The weed [SlideToCheckIn] supports
 * **method selection** (bud / pen / edible / dab via a dropdown on the left
 * emoji) and a **puff counter** — after sliding to "Smoked" you pick how much
 * (hits / rips / mg / dabs). One additional binary [SlideToCheckIn] is rendered
 * per [Program.customCheckIns]. Once the weed check-in *and every custom
 * check-in* are filled, the whole batch is written through
 * [CheckInLogger.logCheckIns] (which records ProgramDayInfo, recomputes
 * last-smoked, stamps + returns the day's variable reward, syncs to Supabase,
 * and runs the achievement engine). Every check-in then lands on the animated
 * reward screen (CheckInRewardViews.kt): sober days get the gradient screen
 * with the static reward (weed-free timer / money saved) leading, slips get
 * the smoked-stats cards and an encouraging variable reward. Per-reward
 * confetti fires inside the reward views.
 */
@Composable
fun CheckInSheet(
    logger: CheckInLogger,
    userInfo: UserInfo,
    program: Program,
    onDismiss: () -> Unit,
    // Fired only when a check-in was actually COMPLETED (not skipped/closed) —
    // iOS scrolls the feed only in this case (CheckInViewModel.swift:136-146).
    onCompleted: (() -> Unit)? = null,
) {
    var phase by remember { mutableStateOf("slide") }
    // The rewards are generated once, at the moment of check-in (inside the
    // OnScreenCheckIn callback) — never during composition. The variable
    // generator mutates program.dayInfo (it stamps the day's reward type), so
    // invoking it from a `remember` block would re-run side effects on
    // recomposition (PARITY §18-D1).
    var variableReward by remember { mutableStateOf<VariableReward?>(null) }
    var staticReward by remember { mutableStateOf<org.clear30.data.StaticReward?>(null) }
    var rewardSober by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedCheckInSheet)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Entrance — the sheet pops in like the iOS defaultTransition (.scale +
        // .opacity on a response-0.175 spring). Dialogs otherwise appear abruptly.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(Anim.transitionSpring()) + fadeIn(Anim.transitionSpring()),
            exit = scaleOut(Anim.transitionSpring()) + fadeOut(Anim.transitionSpring()),
        ) {
        // iOS CheckInFullscreen: a sober check-in flips the reward screen onto
        // the brand gradient (white content); everything else sits on the plain
        // background.
        val gradientScreen = phase == "reward" && rewardSober == true
        Box(
            Modifier.fillMaxSize().then(
                if (gradientScreen) Modifier.background(Clear30Gradients.clear30)
                else Modifier.background(Clear30Colors.background),
            ),
        ) {
            when (phase) {
                "reward" -> CheckInRewardContent(
                    userInfo = userInfo,
                    staticReward = staticReward,
                    variableReward = variableReward,
                    sober = rewardSober,
                    onContinue = {
                        onCompleted?.invoke()
                        onDismiss()
                    },
                )
                else -> Column(
                    // T2: plain `clear30Background` behind the sliders (iOS
                    // CheckIn.swift `.background { Color.clear30Background }`) —
                    // NOT the brand gradient. navigationBarsPadding keeps the
                    // Skip pill clear of the gesture bar (W8).
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                        .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))

                    // "Check in for / Today"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SmallText("Check in for", color = Clear30Colors.text.copy(alpha = 0.5f))
                        Heading2(relativeCheckInTitle(), color = Clear30Colors.text)
                    }

                    Spacer(Modifier.height(Dimens.cardSpacing * 2))

                    // Weed check-in (method + amount) plus one slider per custom check-in.
                    OnScreenCheckIn(program) { checkIns ->
                        // logCheckIns stamps + returns the day's variable reward;
                        // the static reward is derived on the spot. Both sober days
                        // (celebratory rewards) and slips (encouraging quotes /
                        // growth) get one.
                        val today = PlainDate.from(now())
                        variableReward = logger.logCheckIns(now(), checkIns)
                        staticReward = org.clear30.data.CheckInRewardStaticGenerator.generate(userInfo, program, today)
                        rewardSober = program.dayInfo[today]?.sober
                        phase = "reward"
                    }

                    Spacer(Modifier.weight(1f))

                    // Skip Check In — gray pill on the plain background (iOS TinyTextButton).
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp))
                            .background(Clear30Colors.opacityGray)
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SmallText("Skip Check In", color = Clear30Colors.text.copy(alpha = 0.5f))
                        Icon(
                            sfSymbol("chevron.right"),
                            contentDescription = null,
                            tint = Clear30Colors.text.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.height(Dimens.cardSpacing * 2))
                }
            }
        }
        }
    }
}

/**
 * OnScreenCheckIn — the iOS `CheckIn` view: a weed slider (with method + amount)
 * followed by one slider per custom check-in. Mirrors `handleDoneCheckingIn`:
 * once every slot (weed + each custom) reports a result, the collected
 * [LoggedCheckIn]s are handed to [onDone] after a short settle delay.
 */
@Composable
private fun OnScreenCheckIn(program: Program, onDone: (List<LoggedCheckIn>) -> Unit) {
    // Keyed by slot id: "weed" for the standard check-in, the custom's id otherwise.
    val results = remember { mutableStateMapOf<String, LoggedCheckIn>() }
    val total = 1 + program.customCheckIns.size
    var fired by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        SlideToCheckIn(
            methods = CheckInMethod.entries,
            initialMethod = program.latestCheckInMethod,
        ) { completion, method, amount ->
            if (completion == null) {
                results.remove("weed")
            } else {
                results["weed"] = LoggedCheckIn(
                    id = (method ?: CheckInMethod.BUD).id,
                    amount = amount,
                    completion = completion,
                )
            }
        }

        program.customCheckIns.forEach { ci ->
            SlideToCheckIn(
                left = ci.incompleteOption,
                right = ci.completeOption,
                gradient = ci.gradient,
            ) { completion, _, _ ->
                if (completion == null) results.remove(ci.id)
                else results[ci.id] = LoggedCheckIn(id = ci.id, completion = completion)
            }
        }
    }

    LaunchedEffect(results.size) {
        // Undo (tap the committed bar) removes a result and restarts this
        // effect, cancelling the pending onDone — the flag must reset with it
        // or no later commit can ever fire (the sheet deadlocked until closed).
        if (results.size < total) fired = false
        if (results.size >= total && !fired) {
            fired = true
            delay(550)
            onDone(results.values.toList())
        }
    }
}

/**
 * AmountPicker — the iOS `AmountList` puff counter, shown after sliding the weed
 * slider to "Smoked". Lists "N hit(s) / rip(s) / mg / dab(s)" for the selected
 * method; tapping an amount records it. "Skip amount" logs the slip without a
 * quantity (iOS lets you release without holding to open the picker).
 */
@Composable
private fun AmountPicker(method: CheckInMethod, modifier: Modifier = Modifier, onPick: (Int?) -> Unit) {
    val count = 6
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.opacityGrayFlattened)
            .padding(Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        TinyText("How much?", color = Clear30Colors.text.copy(alpha = 0.5f))
        for (i in 0 until count) {
            val label = method.getAmountString(index = i) + (if (i == count - 1) "+" else "")
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.cornerRadius * 0.6f))
                    .background(Clear30Gradients.clear30)
                    .clickable { Haptics.successLight(); onPick(method.getAmount(i)) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                SmallText(label, color = Color.White)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).clickable { onPick(null) }.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            TinyText("Skip amount", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

/**
 * SlideToCheckIn — the iOS drag-to-choose control. A gray track with the two
 * option emojis at the edges and a gradient handle in the center showing a
 * ↔ icon. Drag the handle past ~85% of either side to commit: left =
 * `onResult(false, …)` (smoked / incomplete), right = `onResult(true, …)`
 * (didn't smoke / complete). Tap the committed bar to undo.
 *
 * When [methods] is non-null this is the **weed** variant: the left emoji
 * becomes a method dropdown (bud / pen / edible / dab) and committing to the
 * left reveals the [AmountPicker]. When [methods] is null it's a plain binary
 * slider used for custom check-ins.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SlideToCheckIn(
    methods: List<CheckInMethod>? = null,
    initialMethod: CheckInMethod? = null,
    left: CustomCheckInOption = CheckInDefaults.weed.incompleteOption,
    right: CustomCheckInOption = CheckInDefaults.weed.completeOption,
    gradient: Brush = Clear30Gradients.clear30,
    onResult: (completion: Boolean?, method: CheckInMethod?, amount: Int?) -> Unit,
) {
    val isWeed = methods != null
    val handleSize = 55.dp
    val pad = 5.dp
    val density = LocalDensity.current

    var methodIndex by remember {
        mutableIntStateOf(methods?.indexOf(initialMethod)?.takeIf { it >= 0 } ?: 0)
    }
    val method = methods?.getOrNull(methodIndex)
    val leftOpt = if (isWeed) (method ?: CheckInMethod.BUD).customCheckIn.incompleteOption else left
    val rightOpt = if (isWeed) (method ?: CheckInMethod.BUD).customCheckIn.completeOption else right

    var committed by remember { mutableStateOf<Boolean?>(null) }   // null = open, true = right, false = left
    var offsetX by remember { mutableFloatStateOf(0f) }
    var passedThreshold by remember { mutableStateOf(false) }
    var awaitingAmount by remember { mutableStateOf(false) }
    var chosenAmount by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val animOffset by animateFloatAsState(offsetX, spring(dampingRatio = 0.9f, stiffness = 158f), label = "slideOffset")
    val handleScale by animateFloatAsState(if (passedThreshold) 1.06f else 1f, spring(dampingRatio = 0.6f, stiffness = 320f), label = "handleScale")

    val committedText: String? = when (committed) {
        true -> "${rightOpt.emoji} ${rightOpt.name}"
        false -> "${leftOpt.emoji} ${leftOpt.name}"
        null -> null
    }
    val committedSubtext: String? =
        if (committed == false && method != null) method.getAmountString(amount = chosenAmount) else null

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val handlePx = with(density) { handleSize.toPx() }
            val padPx = with(density) { pad.toPx() }
            val threshold = (widthPx / 2 - handlePx / 2 - padPx).coerceAtLeast(1f)

            Box(Modifier.fillMaxWidth().height(handleSize + pad * 2)) {
                if (committed == null) {
                    // Track
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(Dimens.cornerRadius))
                            .background(Clear30Colors.opacityGray),
                    )
                    // Edge emojis (left becomes a method dropdown on the weed variant)
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = Dimens.cardSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (isWeed && methods != null) {
                            Box {
                                Box(
                                    Modifier.clip(RoundedCornerShape(percent = 50))
                                        .background(Clear30Colors.opacityGray)
                                        .clickable { menuOpen = true }
                                        .padding(6.dp),
                                ) { Heading2(leftOpt.emoji) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    methods.forEachIndexed { i, m ->
                                        val opt = m.customCheckIn.incompleteOption
                                        DropdownMenuItem(
                                            text = { SmallText("${opt.emoji} ${opt.name}") },
                                            onClick = { methodIndex = i; menuOpen = false; Haptics.lightImpact() },
                                            trailingIcon = {
                                                if (i == methodIndex) {
                                                    Icon(sfSymbol("checkmark"), null, Modifier.size(14.dp))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            Heading2(leftOpt.emoji)
                        }
                        Heading2(rightOpt.emoji)
                    }
                    // Handle
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(animOffset.roundToInt(), 0) }
                            .graphicsLayer { scaleX = handleScale; scaleY = handleScale }
                            .padding(pad)
                            .size(handleSize)
                            .clip(RoundedCornerShape(Dimens.cornerRadius * 0.8f))
                            .background(gradient)
                            .pointerInput(threshold, methodIndex) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dx ->
                                        offsetX = (offsetX + dx).coerceIn(-threshold, threshold)
                                        val passed = abs(offsetX) >= threshold * 0.85f
                                        if (passed != passedThreshold) {
                                            passedThreshold = passed
                                            Haptics.lightImpact()
                                        }
                                    },
                                    onDragEnd = {
                                        when {
                                            offsetX >= threshold * 0.85f -> {
                                                offsetX = threshold; committed = true; Haptics.successHeavy()
                                            }
                                            offsetX <= -threshold * 0.85f -> {
                                                // Smoked logs immediately with no amount (iOS only
                                                // shows the amount list on a deliberate hold; the
                                                // amount can be edited later from the day card).
                                                offsetX = -threshold; committed = false; Haptics.successHeavy()
                                            }
                                            else -> { offsetX = 0f; passedThreshold = false }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            sfSymbol("arrow.left.and.right"),
                            contentDescription = "Slide to check in",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { alpha = 1f - (abs(animOffset) / threshold).coerceIn(0f, 1f) },
                        )
                    }
                } else {
                    // Committed bar — full-width fill with the chosen option (and amount).
                    val barBrush = if (committed == false) Clear30Gradients.grayFlat else gradient
                    val contentColor = if (committed == false) Clear30Colors.text else Color.White
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(Dimens.cornerRadius))
                            .background(barBrush)
                            .clickable {
                                committed = null; offsetX = 0f; passedThreshold = false
                                awaitingAmount = false; chosenAmount = null
                                Haptics.lightImpact()
                                onResult(null, method, null)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DefaultText(committedText ?: "", color = contentColor)
                            committedSubtext?.let { TinyText(it, color = contentColor.copy(alpha = 0.5f)) }
                        }
                    }
                }
            }
        }

        // Puff counter — weed variant, after a slip, until an amount is chosen/skipped.
        // Pops in/out like the iOS AmountList (.transition(defaultTransition)).
        AnimatedVisibility(
            visible = awaitingAmount && method != null && chosenAmount == null,
            enter = scaleIn(Anim.transitionSpring()) + fadeIn(Anim.transitionSpring()),
            exit = scaleOut(Anim.transitionSpring()) + fadeOut(Anim.transitionSpring()),
        ) {
            method?.let { m ->
                AmountPicker(method = m, modifier = Modifier.padding(top = Dimens.cardSpacing)) { amt ->
                    chosenAmount = amt
                    awaitingAmount = false
                }
            }
        }

        if (committed == null) {
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TinyText(leftOpt.name, color = Clear30Colors.text.copy(alpha = 0.5f))
                TinyText(rightOpt.name, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }

    // "Didn't smoke" / complete commits fire immediately after the bar settles.
    LaunchedEffect(committed) {
        if (committed == true) {
            delay(250)
            onResult(true, method, null)
        }
    }
    // "Smoked" / incomplete commits fire once the amount step resolves (weed) or
    // immediately (custom check-ins, which never await an amount).
    LaunchedEffect(committed, awaitingAmount, chosenAmount) {
        if (committed == false && !awaitingAmount) {
            delay(150)
            onResult(false, method, chosenAmount)
        }
    }
}

/**
 * CheckInRewardContent — the post-check-in reward screen (iOS
 * `CheckInRewardScreen`). Renders the day's static reward (weed-free timer /
 * money saved / smoked stats) and variable reward with their full animated
 * views ([CheckInRewardStaticContainer] / [CheckInRewardVariableContainer],
 * CheckInRewardViews.kt), sequenced like iOS: the primary reward animates
 * first, and its completion reveals the secondary. On a sober day the static
 * reward leads (staticRewardFirst = sober) over the gradient screen; on a slip
 * the variable reward leads on the plain background. Per-reward confetti fires
 * inside the reward views themselves.
 */
@Composable
private fun CheckInRewardContent(
    userInfo: UserInfo,
    staticReward: org.clear30.data.StaticReward?,
    variableReward: VariableReward?,
    sober: Boolean?,
    onContinue: () -> Unit,
) {
    val name = userInfo.name.ifBlank { "friend" }
    val gradientBackground = sober == true
    val staticRewardFirst = sober == true
    val textColor = if (gradientBackground) Color.White else Clear30Colors.text

    var showRewards by remember { mutableStateOf(false) }
    var showSecondaryReward by remember { mutableStateOf(false) }
    // iOS reveals the rewards 1s after the screen lands.
    LaunchedEffect(Unit) { delay(1000); showRewards = true }

    // Entrance: the recap springs up + fades in (a livelier "done!" moment) —
    // iOS rewardSpringAnimation (response 0.45, dampingFraction 0.75).
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(if (shown) 1f else 0f, Anim.rewardSpring(), label = "rewardAppear")
    LaunchedEffect(Unit) { shown = true }

    // The primary reward's completion reveals the secondary (iOS staticFirst /
    // variableFirst chaining).
    val hasBoth = staticReward != null && variableReward != null
    val onPrimaryComplete: () -> Unit = { if (hasBoth) showSecondaryReward = true }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .graphicsLayer { alpha = appear; val s = 0.92f + 0.08f * appear; scaleX = s; scaleY = s }
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // Headline — celebratory for a clear day, encouraging for a slip. Both
        // lines CENTERED like iOS `multilineTextAlignment(.center)` (W6).
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (sober == false) {
                SmallText("Tomorrow's a fresh start, $name", color = textColor.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Heading2("🌅 Every check-in is progress.", color = textColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                SmallText("You didn't vanish, $name", color = textColor.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Heading2("🙏 Staying present is everything.", color = textColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(Dimens.cardSpacing * 3))

        if (showRewards) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                if (staticRewardFirst) {
                    staticReward?.let {
                        CheckInRewardStaticContainer(it, onCompletion = onPrimaryComplete)
                    }
                    if (variableReward != null && (showSecondaryReward || staticReward == null)) {
                        RewardAppear {
                            CheckInRewardVariableContainer(
                                variableReward, gradientBackground = !gradientBackground, userName = userInfo.name,
                            )
                        }
                    }
                } else {
                    variableReward?.let {
                        CheckInRewardVariableContainer(
                            it, gradientBackground = !gradientBackground, userName = userInfo.name,
                            onCompletion = onPrimaryComplete,
                        )
                    }
                    if (staticReward != null && (showSecondaryReward || variableReward == null)) {
                        RewardAppear {
                            CheckInRewardStaticContainer(staticReward)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.clip(RoundedCornerShape(99.dp))
                .background(if (gradientBackground) Color.White.copy(alpha = 0.25f) else Clear30Colors.opacityGray)
                .clickable { onContinue() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SmallText("Continue", color = if (gradientBackground) Color.White else textColor.copy(alpha = 0.5f))
            Icon(
                sfSymbol("chevron.right"),
                contentDescription = null,
                tint = if (gradientBackground) Color.White else textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(Dimens.cardSpacing * 2))
        Spacer(Modifier.height(Dimens.cardSpacing))
    }
}

/** Pops its content in with the default transition (iOS `.transition(defaultTransition)`). */
@Composable
private fun RewardAppear(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(Anim.transitionSpring()) + fadeIn(Anim.transitionSpring()),
        exit = scaleOut(Anim.transitionSpring()) + fadeOut(Anim.transitionSpring()),
    ) { content() }
}

/** The day being checked in for — always "Today" in this entry point. */
private fun relativeCheckInTitle(): String = "Today"
