package org.clear30.views.theme

import androidx.compose.ui.graphics.Color

/**
 * Clear30 brand palette.
 *
 * Ported 1:1 from the iOS asset catalog (Assets.xcassets *.colorset) and the
 * hard-coded hex gradients in GlobalData (Clear30App.swift). Values are sRGB.
 */
object Clear30Colors {
    // --- Core brand (asset catalog) ---
    val blue = Color(0xFF5BB4A9)                 // "Clear 30 blue"  91,180,169
    val green = Color(0xFF80C97A)                // "Clear 30 green" 128,201,122
    val yellow = Color(0xFFFFF87E)               // "Clear 30 yellow" 255,248,126
    val accent = Color(0xFF6EBF92)               // AccentColor 110,191,146

    val background = Color(0xFFFDFFFF)           // clear30BackgroundColor 253,255,255
    val button = Color(0xFFFDFFFF)               // clear30ButtonColor 253,255,255
    val text = Color(0xFF000000)                 // clear30TextColor
    val widgetBackground = Color(0xFFFFFFFF)     // WidgetBackground

    val shadow = Color(0x33000000)               // clear30ShadowColor  black @ 0.2
    val gray = Color(0xFFB6B6B6)                 // clear30Gray  0.713 grayscale
    val opacityGray = Color(0x1A000000)          // clear30OpacityGray  black @ 0.1
    val opacityGrayFlattened = Color(0xFFE5E5E5) // clear30OpacityGrayFlattened 229,229,229
    val opacityGrayFlattenedButton = Color(0xFFE5E5E5)

    // --- Program stage gradient endpoints (Stage1..6, -1/-2) ---
    val stage1a = Color(0xFFFF9359); val stage1b = Color(0xFFFA8957)
    val stage2a = Color(0xFFDE3745); val stage2b = Color(0xFFF24957)
    val stage3a = Color(0xFF944BE7); val stage3b = Color(0xFFA051FA)
    val stage4a = Color(0xFF5B9CF0); val stage4b = Color(0xFF5BAEE6)
    val stage5a = Color(0xFFF0D042); val stage5b = Color(0xFFEFCC34)
    val stage6a = Color(0xFF80C97A); val stage6b = Color(0xFF5BB4A9)

    // --- Social / feature accents (asset catalog) ---
    val reddit1 = Color(0xFFEB6D28); val reddit2 = Color(0xFFEF8739)
    val youTube1 = Color(0xFFFF0000); val youTube2 = Color(0xFFFF3700)
    val meditation1 = Color(0xFF5B9CF0); val meditation2 = Color(0xFF5BAEE6)

    // --- Hard-coded gradient endpoints from GlobalData ---
    val symptom1 = Color(0xFFFF8C59); val symptom2 = Color(0xFFFFA372)
    val slipped1 = Color(0xFFF97A3D); val slipped2 = Color(0xFFFFAE5C)
    val journal1 = Color(0xFFF0D042); val journal2 = Color(0xFFEFCC34)
    // Community / Groups accent — iOS communityGradient (#A32EB8→#B93FCF).
    val community1 = Color(0xFFA32EB8); val community2 = Color(0xFFB93FCF)
    val instagram1 = Color(0xFFDD2A7B); val instagram2 = Color(0xFF8134AF)
    val brightGreen1 = Color(0xFF26CD6A); val brightGreen2 = Color(0xFF00BCA5)
    val supplements1 = Color(0xFFF4BA66); val supplements2 = Color(0xFFFFC685)
    val sleep1 = Color(0xFF14435E); val sleep2 = Color(0xFF1C5E80)
    val red1 = Color(0xFFF65555); val red2 = Color(0xFFFB5151)
    val claire1 = Color(0xFF5C70EF); val claire2 = Color(0xFF8969FF)
}

/** Color(hex: "..") equivalent — accepts "RRGGBB", "#RRGGBB", "AARRGGBB". */
fun colorFromHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLong(16)
    return when (clean.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color.Black
    }
}
