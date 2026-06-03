package org.clear30.views.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    AlertDialog(
        onDismissRequest = { AlertHandler.dismiss() },
        title = { Text(a.title) },
        text = { Text(a.message) },
        confirmButton = {
            TextButton(onClick = {
                a.onPrimary?.invoke()
                AlertHandler.dismiss()
            }) { Text(a.primaryLabel) }
        },
        dismissButton = a.secondaryLabel?.let {
            {
                TextButton(onClick = {
                    a.onSecondary?.invoke()
                    AlertHandler.dismiss()
                }) { Text(it) }
            }
        },
    )
}

@Composable
private fun PopupQueueHost() {
    val payload by PopupManager.current.collectAsStateWithLifecycle()
    val p = payload ?: return
    when (p) {
        is PopupManager.Payload.Alert -> AlertDialog(
            onDismissRequest = { PopupManager.dismissCurrent() },
            title = { Text(p.title) },
            text = { Text(p.message) },
            confirmButton = {
                TextButton(onClick = {
                    p.onConfirm()
                    PopupManager.dismissCurrent()
                }) { Text(p.confirmLabel) }
            },
        )
        is PopupManager.Payload.Confetti -> {
            // Auto-dismiss the celebration after ~2s so the user sees it
            // briefly without an explicit close button — matches the iOS
            // confetti overlay behavior.
            LaunchedEffect(p) { delay(2000); PopupManager.dismissCurrent() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Heading1("${p.emoji}  ${p.message}")
            }
        }
        is PopupManager.Payload.Achievement -> AlertDialog(
            onDismissRequest = { PopupManager.dismissCurrent() },
            title = { Text("Achievement earned") },
            text = { Text(p.achievementKey) },
            confirmButton = {
                TextButton(onClick = { PopupManager.dismissCurrent() }) { Text("Nice") }
            },
        )
        is PopupManager.Payload.TutorialStep -> AlertDialog(
            onDismissRequest = { PopupManager.dismissCurrent() },
            title = { Text(p.screen) },
            text = { Text(p.captionKey) },
            confirmButton = {
                TextButton(onClick = { PopupManager.dismissCurrent() }) { Text("Got it") }
            },
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
