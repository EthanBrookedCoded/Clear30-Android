package org.clear30.views.components

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * StatusBarStyle — controls the brightness of the system status-bar icons. On
 * brand-gradient screens (intro, splash, celebration) the gradient is dark
 * enough that the default dark icons disappear; flipping `light` to true makes
 * the icons white. Light backgrounds (the main app) want dark icons.
 *
 * Call from any composable that fills the top of the screen. The setting is
 * applied via [WindowCompat.getInsetsController] which is the modern
 * replacement for `window.statusBarColor`.
 *
 * @param bgColor only used to derive the default icon tint (light vs dark) if
 *                you don't pass an explicit override. Pass [Color.Unspecified]
 *                to keep whatever the current activity has set.
 */
@Composable
fun StatusBarStyle(
    bgColor: Color = Color.Unspecified,
    forceLightIcons: Boolean? = null,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val light = forceLightIcons
            ?: (bgColor != Color.Unspecified && bgColor.luminance() < 0.5f)
        // appearanceLightStatusBars == true => DARK icons (Material spec inverts)
        controller.isAppearanceLightStatusBars = !light
        controller.isAppearanceLightNavigationBars = !light
        // Keep the system bars transparent so the brand gradient extends behind
        // them — without this Android paints a solid scrim that breaks the
        // gradient continuity at the top of every screen.
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.Transparent.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.Transparent.toArgb()
    }
}

/**
 * SafeAreaTop — equivalent to SwiftUI's automatic top safe-area inset.
 * Apply to the OUTERMOST container of any screen that doesn't already use a
 * Scaffold (which handles insets automatically). Without this, edge-to-edge
 * causes the first heading to render behind the status bar.
 */
@Composable
fun SafeAreaTop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) { content() }
}
