package org.clear30.views.existinguser.support.slipped

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * The consolidated post-slip ("I slipped") sheet — port of iOS
 * SlippedSheet.swift, surfaced from the Support tab's "Slip up?" hero.
 *
 * - Header: emoji + title + subtitle + a rotating research callout.
 * - ONE activity rendered inline (random hero per presentation).
 * - "All options" swaps the inline activity in place (no navigation).
 *
 * Open-only: this sheet never logs a slip — the daily check-in remains the
 * single place a slip is recorded.
 */
@Composable
fun SlippedSheet(
    userInfo: UserInfo,
    program: Program,
    callbacks: SlippedCallbacks,
) {
    // Goal from the "What brings you here" answer (0=Quit, 1=Break, 2=Moderate, 3=Other).
    val slipGoal = when (program.whatBringsYouHereIndex) {
        0, 1 -> SlipGoal.QUITTING
        2 -> SlipGoal.MODERATING
        else -> SlipGoal.GENERAL
    }
    val copy = remember { SlipCopy.random(slipGoal) }
    var activeActivityId by remember { mutableStateOf(SlipActivity.random().id) }
    var showOptions by remember { mutableStateOf(false) }
    // Staggered intro: title → subtitle → callout → activity.
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.slippedSheetOpened)
        // iOS scheduleIntro: 0.5 / 1.5 / 3.0 / 5.0 s.
        delay(500); step = 1
        delay(1000); step = 2
        delay(1500); step = 3
        delay(2000); step = 4
    }

    fun reveal(target: Int): Boolean = step >= target

    @Composable
    fun revealAlpha(target: Int, shown: Float = 1f): Float {
        val a by animateFloatAsState(if (reveal(target)) shown else 0f, tween(600), label = "slipReveal$target")
        return a
    }

    Dialog(
        onDismissRequest = callbacks.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2, Alignment.CenterVertically),
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                        Row(
                            Modifier.alpha(revealAlpha(1)),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Heading1(copy.emoji)
                            Heading2(copy.title, Modifier.weight(1f))
                        }
                        SmallText(copy.subtitle, Modifier.alpha(revealAlpha(2, 0.75f)))
                    }

                    Row(
                        Modifier.alpha(revealAlpha(3)).fillMaxWidth()
                            .cardStyle(color = Clear30Colors.opacityGray, shadowColor = Color.Transparent),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        SmallText("💡")
                        TinyText(copy.callout, color = Clear30Colors.text.copy(alpha = 0.75f))
                    }
                }

                // Hero activity (mounted at alpha 0 during the intro to keep layout stable).
                Box(Modifier.alpha(revealAlpha(4))) {
                    SlippedActivityView(activeActivityId, userInfo, program, callbacks)
                }

                // All options
                if (reveal(4)) {
                    Row(
                        Modifier.fillMaxWidth().alpha(0.5f),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            Modifier.pressScale { showOptions = true },
                            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TinyText("All options")
                            Icon(sfSymbol("chevron.right"), contentDescription = null, tint = Clear30Colors.text, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }

            // Close (Android affordance — the iOS sheet is swipe-dismissable).
            IconButton(
                icon = "xmark",
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.cardSpacing),
            ) { callbacks.onDismiss() }
        }
    }

    if (showOptions) {
        SlippedOptionsSheet(
            currentId = activeActivityId,
            onDismiss = { showOptions = false },
            onSelect = { newId ->
                showOptions = false
                if (newId != activeActivityId) {
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.slippedActivitySwapped,
                        mapOf(LogEventExtraDataType.TYPE to newId.rawValue),
                    )
                    activeActivityId = newId
                }
            },
        )
    }
}

// MARK: - All options sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlippedOptionsSheet(
    currentId: SlipActivityID,
    onDismiss: () -> Unit,
    onSelect: (SlipActivityID) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clear30Colors.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.cardSpacing * 2),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("All options")

            SlipActivity.all.forEach { activity ->
                val selected = activity.id == currentId
                Row(
                    Modifier.fillMaxWidth()
                        .pressScale {
                            Haptics.mediumImpact()
                            onSelect(activity.id)
                        }
                        .cardStyle(
                            color = Clear30Colors.button,
                            gradient = if (selected) activity.gradient else null,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(
                            if (selected) androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.25f))
                            else activity.gradient,
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Heading3(activity.icon) }

                    Column(Modifier.weight(1f)) {
                        SmallText(activity.title, color = if (selected) Color.White else Color.Unspecified)
                        TinyText(
                            activity.blurb,
                            color = (if (selected) Color.White else Clear30Colors.text).copy(alpha = 0.5f),
                        )
                    }

                    if (!selected) {
                        Icon(
                            sfSymbol("chevron.right"), contentDescription = null,
                            tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
