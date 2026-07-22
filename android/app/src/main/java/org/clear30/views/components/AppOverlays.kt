package org.clear30.views.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.clear30.data.AlertHandler
import org.clear30.data.LoadingCoordinator
import org.clear30.data.PopupManager

/**
 * AppOverlays — single-source overlay layer. Rendered in [AppRoot] above the
 * routed content so the master alert, popup queue, and loading scrim sit above
 * every screen.
 *
 * The iOS app composes the same three layers via `.alert` modifier +
 * [PopupManager]'s sheet + a `LoadingView`; here they're three sibling Composables
 * stacked in a Box. Each is opt-in: when its StateFlow head is null, it doesn't
 * render anything and the underlying content keeps full input focus.
 */
@Composable
fun AppOverlays() {
    Box(Modifier.fillMaxSize()) {
        MasterAlert()
        PopupQueueHost()
        GlobalLoadingScrim()
    }
}

@Composable
private fun MasterAlert() {
    val alert by AlertHandler.current.collectAsStateWithLifecycle()
    val a = alert ?: return
    Clear30Alert(
        onDismissRequest = { AlertHandler.dismiss() },
        title = a.title,
        message = a.message,
        confirmLabel = a.primaryLabel,
        onConfirm = {
            a.onPrimary?.invoke()
            AlertHandler.dismiss()
        },
        dismissLabel = a.secondaryLabel,
        onDismissAction = {
            a.onSecondary?.invoke()
            AlertHandler.dismiss()
        },
    )
}

@Composable
private fun PopupQueueHost() {
    val payload by PopupManager.current.collectAsStateWithLifecycle()
    val p = payload ?: return
    when (p) {
        is PopupManager.Payload.Alert -> Clear30Alert(
            onDismissRequest = { PopupManager.dismissCurrent() },
            title = p.title,
            message = p.message,
            confirmLabel = p.confirmLabel,
            onConfirm = {
                p.onConfirm()
                PopupManager.dismissCurrent()
            },
        )
        is PopupManager.Payload.Confetti -> {
            // Particle confetti burst + the celebration label, auto-dismissed once
            // the burst has rained out — matches the iOS `ConfettiCheckIn` overlay.
            LaunchedEffect(p) { delay(2600); PopupManager.dismissCurrent() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Heading1("${p.emoji}  ${p.message}")
                ConfettiOverlay()
            }
        }
        is PopupManager.Payload.Achievement -> Clear30Alert(
            onDismissRequest = { PopupManager.dismissCurrent() },
            title = "Achievement earned",
            message = p.achievementKey,
            confirmLabel = "Nice",
            onConfirm = { PopupManager.dismissCurrent() },
        )
    }
}

@Composable
private fun GlobalLoadingScrim() {
    val loading by LoadingCoordinator.isLoading.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}
