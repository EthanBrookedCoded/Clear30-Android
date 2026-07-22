package org.clear30.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/**
 * Popups — the ONE standard set of presentation containers, mirroring iOS
 * `sheetDefaults()` (ExtensionsAppOnly.swift) + the system alert. Every sheet,
 * full-screen cover, and alert in the app goes through one of these so corner
 * radius, background, padding, and typography stay consistent:
 *
 *  - [Clear30Sheet] — bottom sheet (iOS `.sheet` + `sheetDefaults`).
 *  - [Clear30FullScreenCover] — full-screen modal (iOS `.fullScreenCover`).
 *  - [Clear30CardDialog] — centered card over the scrim (emoji picker, game
 *    level picker, send-a-note).
 *  - [Clear30Alert] — alert with brand typography and [pressScale] actions
 *    (replaces Material `AlertDialog` + default `TextButton`s).
 */

/**
 * Clear30Sheet — the standard bottom sheet: brand background, 20dp top corners
 * (iOS `presentationCornerRadius(20)`), default drag handle, and the app-wide
 * content padding (horizontal [Dimens.horizontalPadding], vertical
 * [Dimens.cardSpacing]). Pass `contentPadding = false` for edge-to-edge content
 * (e.g. a horizontal rail that must bleed to the sheet edge).
 *
 * [canDismiss] = false pins the sheet open (iOS `interactiveDismissDisabled`):
 * swipe, back press, and scrim taps are all ignored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Clear30Sheet(
    onDismiss: () -> Unit,
    canDismiss: Boolean = true,
    skipPartiallyExpanded: Boolean = true,
    contentPadding: Boolean = true,
    dragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = { target -> canDismiss || target != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = state,
        shape = RoundedCornerShape(topStart = Dimens.sheetCornerRadius, topEnd = Dimens.sheetCornerRadius),
        containerColor = Clear30Colors.background,
        dragHandle = if (dragHandle) {
            { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Clear30Colors.opacityGray) }
        } else null,
    ) {
        Column(
            if (contentPadding) {
                Modifier.padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing)
            } else Modifier,
        ) { content() }
    }
}

/**
 * Clear30FullScreenCover — the standard full-screen modal (iOS
 * `.fullScreenCover`): an edge-to-edge Dialog over an opaque brand background
 * (or [background], e.g. black for media viewers). The dialog window can draw
 * under the status bar — pass [statusBarPadding] when the content should be
 * inset below it (screens with their own header at the top).
 *
 * [canDismiss] = false makes it non-dismissible (hard paywall, assessments);
 * scrim taps never dismiss (full-screen — there is no visible scrim).
 * [entrance] plays the shared [popEntrance] spring scale/fade-in.
 */
@Composable
fun Clear30FullScreenCover(
    onDismiss: () -> Unit,
    canDismiss: Boolean = true,
    background: Color = Clear30Colors.background,
    statusBarPadding: Boolean = false,
    entrance: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = false,
        ),
    ) {
        var m = Modifier.fillMaxSize()
        if (entrance) m = m.popEntrance()
        m = m.background(background)
        if (statusBarPadding) m = m.statusBarsPadding()
        Box(m) { content() }
    }
}

/**
 * Clear30CardDialog — a centered card over the default scrim, in the app card
 * style ([cardStyle] corners + brand background). Replaces the ad-hoc
 * `Dialog { Box.clip(...).background(...) }` wrappers (GameSheet,
 * UserEmojiPicker, SendNotePopup, ProfileBreakOptions date picker).
 */
@Composable
fun Clear30CardDialog(
    onDismiss: () -> Unit,
    canDismiss: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
        ),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .padding(Dimens.horizontalPadding)
                .cardStyle(padding = false),
        ) {
            CompositionLocalProvider(LocalContentColor provides Clear30Colors.text) {
                content()
            }
        }
    }
}

/**
 * Clear30Alert — the standard alert: brand card, Lexend typography, and
 * [pressScale] text actions instead of Material `TextButton`s. Drop-in for the
 * Material `AlertDialog(title/text/confirm/dismiss)` sites.
 *
 * [content] renders below the message for alerts that embed inputs (the
 * "Your Why" editor, prompt responses); most callers only pass [message].
 * [destructive] tints the confirm action red.
 */
@Composable
fun Clear30Alert(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Clear30CardDialog(onDismiss = onDismissRequest) {
        Column(
            Modifier.fillMaxWidth().padding(Dimens.cardSpacing * 1.5f),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3(title)
            if (message != null) SmallText(message, color = Clear30Colors.text.copy(alpha = 0.75f))
            content?.invoke()
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissLabel != null) {
                    AlertActionText(dismissLabel, color = Clear30Colors.text.copy(alpha = 0.5f)) {
                        (onDismissAction ?: onDismissRequest)()
                    }
                }
                AlertActionText(
                    confirmLabel,
                    color = if (destructive) Clear30Colors.red1 else Clear30Colors.text,
                    enabled = confirmEnabled,
                    onClick = onConfirm,
                )
            }
        }
    }
}

/** An alert action — Lexend SemiBold text with the app-wide press-shrink+haptic. */
@Composable
private fun AlertActionText(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Text(
        text = label,
        modifier = (if (enabled) Modifier.pressScale(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 2),
        color = if (enabled) color else color.copy(alpha = 0.25f),
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
    )
}
