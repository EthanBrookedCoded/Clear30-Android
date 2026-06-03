package org.clear30.views.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Clear30 is a light, single-mode brand (the iOS app does not ship a dark
 * variant — backgrounds are near-white, text near-black). We map the brand
 * palette onto a Material3 light scheme so stock Compose components inherit it.
 */
private val Clear30ColorScheme = lightColorScheme(
    primary = Clear30Colors.green,
    onPrimary = Clear30Colors.background,
    secondary = Clear30Colors.blue,
    onSecondary = Clear30Colors.background,
    tertiary = Clear30Colors.accent,
    background = Clear30Colors.background,
    onBackground = Clear30Colors.text,
    surface = Clear30Colors.button,
    onSurface = Clear30Colors.text,
    error = Clear30Colors.red2,
)

@Composable
fun Clear30Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Clear30ColorScheme,
        typography = Clear30Typography,
        content = content,
    )
}
