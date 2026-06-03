package org.clear30.views.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.clear30.R

/** Lexend / Lexend Mono — the app's only bundled fonts (App/Clear30/Fonts). */
val Lexend = FontFamily(
    Font(R.font.lexend, FontWeight.Light),
    Font(R.font.lexend, FontWeight.Normal),
    Font(R.font.lexend, FontWeight.Medium),
    Font(R.font.lexend, FontWeight.SemiBold),
    Font(R.font.lexend, FontWeight.Bold),
)

val LexendMono = FontFamily(Font(R.font.lexend_mono))

/** Material3 typography scale rendered in Lexend. */
val Clear30Typography = Typography().run {
    val f = Lexend
    copy(
        displayLarge = displayLarge.copy(fontFamily = f),
        displayMedium = displayMedium.copy(fontFamily = f),
        displaySmall = displaySmall.copy(fontFamily = f),
        headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = f),
        titleSmall = titleSmall.copy(fontFamily = f),
        bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f),
        bodySmall = bodySmall.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f),
        labelMedium = labelMedium.copy(fontFamily = f),
        labelSmall = labelSmall.copy(fontFamily = f),
    )
}

/** Common Clear30 text styles used across screens. */
object Clear30Text {
    val largeTitle = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Bold, fontSize = 34.sp)
    val title = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    val heading = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val body = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 17.sp)
    val caption = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 13.sp)
}
