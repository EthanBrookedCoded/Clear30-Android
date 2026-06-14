package org.clear30.views.newuser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.R
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.StatusBarStyle
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/** IntroScreenInfo — ported from IntroScreenType.swift (snake_case Supabase keys). */
@Serializable
data class IntroScreenInfo(
    val type: String,
    val intro: String? = null,
    val title: String,
    val subtitle: String? = null,
    @SerialName("image_name") val imageName: String? = null,
    @SerialName("show_stars") val showStars: Boolean? = null,
    @SerialName("show_partners") val showPartners: Boolean? = null,
    @SerialName("button_text") val buttonText: String? = null,
    @SerialName("button_sf_symbol") val buttonSFSymbol: String? = null,
    @SerialName("icon_size") val iconSize: Int? = null,
    @SerialName("title_size") val titleSize: String? = null,
    @SerialName("title_opacity") val titleOpacity: Double? = null,
    @SerialName("show_emoji_bubbles") val showEmojiBubbles: Boolean? = null,
)

/**
 * IntroScreenHandler — ported from IntroScreenHandler.swift (default branch).
 * The "newOnboarding" experiment variant + remote-config IntroScreenVariant are
 * a TODO; this is the basic intro every fresh-install gets on iOS.
 */
@Composable
fun IntroScreenHandler(onGetStarted: () -> Unit, onSignIn: (() -> Unit)? = null) {
    IntroScreen(
        info = IntroScreenInfo(
            type = "basic",
            intro = "Find",
            title = "Intention & Clarity",
            subtitle = "Beyond Weed",
            imageName = "intro_calendar_flat",
            // iOS basic intro: showPartners=false, showStars=nil — neither
            // surfaces by default. Don't force them on here.
            showPartners = false,
        ),
        onGetStarted = onGetStarted,
        onSignIn = onSignIn,
    )
}

/**
 * IntroScreen — port of IntroScreenBasic.swift. Faithful layout order:
 *
 *   1. [IntroScreenLogo] (the wordmark, padding-bottom cardSpacing)
 *   2. Optional hero image (resizable, aspect-fit)
 *   3. Spacer (flex)
 *   4. Headings group — `intro` (opacity 0.5) → `title` → `subtitle` → laurels
 *   5. Spacer (flex)
 *   6. Optional partners row
 *   7. "Get Started" white pill button with leaf icon
 *   8. Optional "Already have an account? Sign in!" pill
 *   9. Privacy / Terms microtext
 *
 * All text center-aligned in white. The clear30 gradient fills behind every
 * inset including the status bar — we apply systemBars insets to the inner
 * content padding so the layout still respects the notch.
 */
@Composable
fun IntroScreen(info: IntroScreenInfo, onGetStarted: () -> Unit, onSignIn: (() -> Unit)? = null) {
    StatusBarStyle(forceLightIcons = true)

    Box(
        Modifier.fillMaxSize().background(Clear30Gradients.clear30),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Wordmark — iOS uses a 100pt SVG. Until that's imported, render
            //    the brand name in Lexend Bold at a comparable size. Padding
            //    matches iOS cardSpacing.
            IntroLogo()
            Spacer(Modifier.size(Dimens.cardSpacing))

            // 2. Optional hero image (the Calendar Flat asset on the default intro).
            if (info.imageName != null) {
                val resId = remember(info.imageName) {
                    when (info.imageName) {
                        "Intro Calendar Flat", "intro_calendar_flat" -> R.drawable.intro_calendar_flat
                        else -> 0
                    }
                }
                if (resId != 0) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(0.85f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            // 3. Flex spacer pushing headings to center-ish.
            Spacer(Modifier.weight(1f))

            // 4. Headings group — centered, white. `intro` rendered at 0.5 opacity.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                info.intro?.let {
                    Heading3(it, color = Color.White.copy(alpha = 0.5f), modifier = Modifier)
                }
                Heading1(info.title, color = Color.White)
                info.subtitle?.let { Heading2(it, color = Color.White) }
                if (info.showStars == true) {
                    InfoScreenLaurels(big = false, modifier = Modifier.padding(top = Dimens.cardSpacing * 2))
                }
            }

            // 5. Flex spacer below headings.
            Spacer(Modifier.weight(1f))

            // 6. Partners row — only when info.showPartners.
            if (info.showPartners == true) {
                IntroScreenPartners()
                Spacer(Modifier.size(Dimens.cardSpacing))
            }

            // 7. Get Started — white pill with leading leaf icon.
            TextIconButton(
                text = info.buttonText ?: "Get Started",
                iconName = info.buttonSFSymbol ?: "leaf.fill",
                onClick = onGetStarted,
            )

            // 8. Optional Sign In tiny button — white-on-clear-glass.
            if (onSignIn != null) {
                Spacer(Modifier.size(Dimens.cardSpacing))
                TinyText(
                    "Already have an account? Sign in!",
                    Modifier
                        .pressScale(onClick = onSignIn)
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                )
            }

            // 9. Privacy / Terms microtext — centered, 0.5 opacity, single line wrapping.
            Spacer(Modifier.size(Dimens.cardSpacing))
            Text(
                text = "By clicking 'Get Started' you are agreeing\n" +
                    "to the Privacy Policy and the Terms and Conditions",
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = Lexend,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * IntroLogo — port of IntroScreenLogo. iOS uses a 100pt SVG of the brand mark.
 * Until that's imported as a vector asset, we render the wordmark as Lexend
 * Bold at a size that fills the same visual slot.
 */
@Composable
private fun IntroLogo() {
    Box(Modifier.width(180.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Clear30",
            color = Color.White,
            fontFamily = Lexend,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * InfoScreenLaurels — port of `InfoScreenLaurels`. Five gold stars between two
 * laurel branches; the SF symbol `laurel.leading` and `laurel.trailing` aren't
 * straightforward to substitute on Material, so we render Unicode laurel
 * emojis for visual parity.
 */
@Composable
private fun InfoScreenLaurels(big: Boolean = false, modifier: Modifier = Modifier) {
    val starHeight = if (big) 30.dp else 20.dp
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        Text("🌿", fontSize = if (big) 56.sp else 36.sp, color = Color.White)
        Spacer(Modifier.size(Dimens.cardSpacing / 4))
        repeat(5) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = Clear30Colors.journal1,
                modifier = Modifier.size(starHeight),
            )
        }
        Spacer(Modifier.size(Dimens.cardSpacing / 4))
        Text("🌿", fontSize = if (big) 56.sp else 36.sp, color = Color.White)
    }
}

/**
 * Partner-org row — port of IntroScreenPartners. iOS shows the NIH, U Michigan,
 * Harvard, MLB logos. Until those vectors are imported, fall back to labeled
 * text chips so the shape of the row is right; the asset import is queued.
 */
@Composable
private fun IntroScreenPartners() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        SmallText("Partnering with", color = Color.White.copy(alpha = 0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2)) {
            listOf("NIH", "U Michigan", "Harvard").forEach { name ->
                MiniText(name, color = Color.White)
            }
        }
    }
}

/**
 * TextIconButton — port of the iOS `TextIconButton`. White rounded pill,
 * leading SF symbol tinted brand-green, body text in Lexend Medium black.
 * Press feedback uses the existing [pressScale] modifier.
 */
@Composable
private fun TextIconButton(text: String, iconName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Color.White)
            .padding(horizontal = 25.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            sfSymbol(iconName),
            contentDescription = null,
            tint = Clear30Colors.green,
            modifier = Modifier.size(18.dp),
        )
        SmallText(text, color = Clear30Colors.text)
    }
}

