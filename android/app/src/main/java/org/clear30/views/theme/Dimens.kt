package org.clear30.views.theme

import androidx.compose.ui.unit.dp

/**
 * Layout tokens ported 1:1 from GlobalData (Clear30App.swift).
 *
 * iOS CGFloat point values map directly to Compose [dp]. Per the project
 * style rules, screens must reference these tokens (and their /2, /3, *2
 * variants) instead of hard-coding spacing.
 */
object Dimens {
    val horizontalPadding = 25.dp
    val headingTopPadding = 10.dp
    val scrollShadowFix = 20.dp
    val cardSpacing = 14.dp
    val buttonHorizontalPadding = (25f / 1.5f).dp   // ~16.67
    val buttonVerticalPadding = 25.dp
    val cornerRadius = 21.dp

    /** Bottom-sheet top corners — iOS `presentationCornerRadius(20)` (sheetDefaults). */
    val sheetCornerRadius = 20.dp

    // Badge/pill chip insets (iOS hardcodes 10/5 on every badge chip — the
    // "Day N ✓" health badges, feed date chips, achievement tags, etc.).
    val chipHorizontalPadding = 10.dp
    val chipVerticalPadding = 5.dp
}
