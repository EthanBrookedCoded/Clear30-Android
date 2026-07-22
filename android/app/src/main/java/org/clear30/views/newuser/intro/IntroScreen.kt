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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
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
import org.clear30.views.components.softShadow
import org.clear30.views.newuser.assessment.linkedText
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
 * IntroScreenHandler — port of IntroScreenHandler.swift.
 *
 * The live onboarding renders the NEW-STYLE variant via `newStyleDefault`
 * (title "Reset Your\nRelationship\nWith Weed"), so we mirror that as the
 * primary look. The remote-config payload + the older "basic" intro are
 * faithful ports kept available behind [IntroScreenBasic] for parity, but the
 * default fresh-install path renders [IntroScreenVariant].
 */
@Composable
fun IntroScreenHandler(onGetStarted: () -> Unit, onSignIn: (() -> Unit)? = null) {
    IntroScreenVariant(
        info = newStyleDefault,
        onGetStarted = onGetStarted,
        onSignIn = onSignIn,
    )
}

/** Default new-style config, ported from IntroScreenHandler.newStyleDefault. */
private val newStyleDefault = IntroScreenInfo(
    type = "new_style",
    title = "Reset Your\nRelationship\nWith Weed",
)

// MARK: - New style variant ------------------------------------------------

/**
 * IntroScreenVariant — port of IntroScreenVariants.swift. Faithful layout:
 *
 *   1. Soft blurred two-circle gradient background (green top-trailing, blue
 *      bottom-leading) over the flat background color — NOT the flat clear30
 *      gradient the basic intro uses.
 *   2. Spacer → centered ZStack (optional emoji bubbles + app icon + title).
 *   3. The app `icon` rendered at `iconSize` (default 120pt) with a 0.25-corner
 *      rounded clip and a soft shadow.
 *   4. Title (default Heading2) at `titleOpacity` (default 0.75) with a shadow.
 *   5. Spacer → bottom buttons (Continue CTA, "Log In" affordance, terms text).
 *
 * All text is centered. Default colors inherit white via the dark background.
 */
@Composable
fun IntroScreenVariant(
    info: IntroScreenInfo,
    onGetStarted: () -> Unit,
    onSignIn: (() -> Unit)? = null,
) {
    // Dark status-bar icons over the light intro field (iOS uses dark .primary).
    StatusBarStyle(forceLightIcons = false)

    val iconSize = (info.iconSize ?: 120).dp
    val titleOpacity = (info.titleOpacity ?: 0.75).toFloat()
    val showBubbles = info.showEmojiBubbles == true
    val titleText = info.title.ifEmpty { "Reset Your\nRelationship\nWith Weed" }

    Box(Modifier.fillMaxSize()) {
        // 1. Soft blurred gradient background.
        IntroGradientBackground()

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.headingTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // 2. Centered content (icon + title), with optional emoji bubbles.
            Box(contentAlignment = Alignment.Center) {
                if (showBubbles) {
                    EmojiBubble("🍃", 45.dp, -11f, Modifier.offset(x = (-100).dp, y = (-45).dp))
                    EmojiBubble("🌟", 55.dp, 12f, Modifier.offset(x = 110.dp, y = (-30).dp))
                    EmojiBubble("☺️", 50.dp, -11f, Modifier.offset(x = (-120).dp, y = 80.dp))
                    EmojiBubble("🗓️", 60.dp, 5f, Modifier.offset(x = 110.dp, y = 95.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        if (showBubbles) Dimens.cardSpacing else Dimens.cardSpacing * 2,
                    ),
                ) {
                    // 3. App icon — iOS `Image(.icon)` rounded to 0.25 of its
                    //    size, with a soft black @ 0.15 shadow (radius 25 when the
                    //    icon is > 80pt, else 15 — matches IntroScreenVariants L51).
                    Image(
                        painter = painterResource(R.drawable.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .softShadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                cornerRadius = iconSize * 0.25f,
                                blurRadius = if (iconSize.value > 80) 25.dp else 15.dp,
                            )
                            .size(iconSize)
                            .clip(RoundedCornerShape(iconSize * 0.25f)),
                        contentScale = ContentScale.Fit,
                    )

                    // 4. Title at the configured opacity. (No box shadow: drawing a
                    //    rounded-rect blur behind the text block read as a heavy gray
                    //    halo around the letters — dark title text on the light field
                    //    is legible on its own.)
                    IntroTitle(
                        titleText,
                        info.titleSize,
                        Modifier.alpha(titleOpacity),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 5. Bottom buttons.
            IntroBottomButtons(info = info, onGetStarted = onGetStarted, onSignIn = onSignIn)
        }
    }
}

@Composable
private fun IntroTitle(text: String, titleSize: String?, modifier: Modifier) {
    // iOS title is `.primary` (dark) over the light field, centered. The typed
    // text composables don't expose textAlign, so center each line via the column.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        text.split("\n").forEach { line ->
            when (titleSize ?: "heading2") {
                "heading1" -> Heading1(line, color = Clear30Colors.text)
                "heading3" -> Heading3(line, color = Clear30Colors.text)
                else -> Heading2(line, color = Clear30Colors.text)
            }
        }
    }
}

/**
 * Emoji bubble — circle of clear30Button @ 0.75 with a soft `clear30Shadow`
 * (radius 5) and the emoji centered at half the bubble size. Rotation is applied
 * to the whole bubble (circle + emoji) to match iOS `.rotationEffect` (L91-100).
 */
@Composable
private fun EmojiBubble(emoji: String, size: Dp, rotation: Float, modifier: Modifier) {
    Box(
        modifier
            .rotate(rotation)
            .softShadow(color = Clear30Colors.shadow, cornerRadius = size / 2, blurRadius = 5.dp)
            .size(size)
            .clip(CircleShape)
            .background(Clear30Colors.button.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}

// MARK: - Shared bottom buttons --------------------------------------------

/**
 * IntroBottomButtons — port of `introBottomButtons`. CTA gradient pill +
 * optional "Log In" affordance + terms/privacy microtext. Spacing = cardSpacing.
 */
@Composable
private fun IntroBottomButtons(
    info: IntroScreenInfo,
    onGetStarted: () -> Unit,
    onSignIn: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        TextIconButton(
            text = info.buttonText ?: "Continue",
            iconName = info.buttonSFSymbol ?: "arrow.right",
            gradient = true,
            onClick = onGetStarted,
        )

        if (onSignIn != null) {
            Row(
                Modifier.pressScale(onClick = onSignIn).alpha(0.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText("Already have an account? ", color = Clear30Colors.text)
                // Underline run for "Log In".
                Text(
                    text = "Log In",
                    color = Clear30Colors.text,
                    fontFamily = Lexend,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        // Terms / privacy — iOS MiniTextWithLinks @ 0.25 opacity, centered, dark
        // `.primary` over the light field (IntroScreenVariants L130-133). The
        // Terms and Conditions / Privacy Policy runs are tappable links.
        Text(
            text = linkedText(
                "By clicking \"Continue\" you agree to our\n" +
                    "[Terms and Conditions](https://clear30.org/terms-and-conditions/) and [Privacy Policy](https://clear30.org/privacy-policy/)",
            ),
            modifier = Modifier.fillMaxWidth().alpha(0.25f),
            color = Clear30Colors.text,
            fontFamily = Lexend,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Shared gradient background ---------------------------------------

/**
 * IntroGradientBackground — faithful port of iOS `introGradientBackground`: the
 * light `clear30Background` field with a soft green glow off the top-trailing
 * corner and a soft blue glow off the bottom-leading corner (iOS draws two huge
 * circles at 0.3 opacity + 75 blur). We render them as radial gradients so the
 * glow is soft on every API level (Modifier.blur is API 31+ only) — and the
 * title stays DARK over the near-white field, exactly like iOS `.primary` text.
 */
@Composable
private fun IntroGradientBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Clear30Colors.background)
            .drawBehind {
                // iOS draws two `width*2` circles (radius == width) at 0.3 opacity
                // with a 75pt blur. The green circle is centered top-trailing at
                // (width, height*0.1); the blue circle bottom-leading at
                // (0, height*0.8) — see introGradientBackground (L143-159). Radial
                // gradients reproduce the soft glow on every API level (Modifier
                // .blur is API 31+ only). The 0.5 inner alpha + transparent edge
                // visually approximates the iOS 0.3-opacity, 75-blur circle.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Clear30Colors.green.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.1f),
                        radius = size.width,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Clear30Colors.blue.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(0f, size.height * 0.8f),
                        radius = size.width,
                    ),
                )
            },
    )
}

// MARK: - Basic variant (faithful fallback) --------------------------------

/**
 * IntroScreen — port of IntroScreenBasic.swift. Kept for parity with the older
 * "basic" intro (wordmark + hero image + intro/title/subtitle + CTA). The live
 * onboarding renders [IntroScreenVariant] instead.
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
            // 1. Wordmark — iOS uses a 100pt brand image (clear30_logo). The
            //    white-only wordmark drawable IS imported, so render it directly.
            IntroLogo()
            Spacer(Modifier.size(Dimens.cardSpacing))

            // 2. Optional hero image (Calendar Flat asset on the default intro).
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
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 4. Headings group — centered, white. iOS wraps these in cardSpacing.
            Column(
                Modifier.padding(Dimens.cardSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                info.intro?.let {
                    Heading3(it, Modifier.alpha(0.5f), color = Color.White)
                }
                Heading1(info.title, color = Color.White)
                info.subtitle?.let { Heading2(it, color = Color.White) }
                if (info.showStars == true) {
                    InfoScreenLaurels(big = false, modifier = Modifier.padding(top = Dimens.cardSpacing * 2))
                }
            }

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
                gradient = false,
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
                        .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing / 2),
                    color = Color.White,
                )
            }

            // 9. Privacy / Terms microtext — iOS MiniTextWithLinks @ 0.5 opacity,
            //    centered, white tint; Privacy Policy / Terms are tappable links.
            Spacer(Modifier.size(Dimens.cardSpacing))
            Text(
                text = linkedText(
                    "By clicking 'Get Started' you are agreeing\n" +
                        "to the [Privacy Policy](https://clear30.org/privacy-policy/) and the [Terms and Conditions](https://clear30.org/terms-and-conditions/)",
                ),
                modifier = Modifier.fillMaxWidth().alpha(0.5f),
                color = Color.White,
                fontFamily = Lexend,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * IntroLogo — port of IntroScreenLogo. iOS renders the 100pt white `clear30_logo`
 * wordmark, fit to its aspect ratio.
 */
@Composable
private fun IntroLogo() {
    Image(
        painter = painterResource(R.drawable.clear30_logo),
        contentDescription = "Clear30",
        modifier = Modifier.width(100.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * InfoScreenLaurels — port of `InfoScreenLaurels`. Five gold stars between two
 * laurel branches. The SF symbols `laurel.leading`/`laurel.trailing` have no
 * Material equivalent, so Unicode laurel emojis stand in for visual parity.
 * TODO(port): asset laurel.leading / laurel.trailing (no Material icon).
 */
@Composable
private fun InfoScreenLaurels(big: Boolean = false, modifier: Modifier = Modifier) {
    val starHeight = if (big) 30.dp else 20.dp
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        Text("🌿", fontSize = (if (big) 56f else 36f).sp, color = Color.White)
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
        Text("🌿", fontSize = (if (big) 56f else 36f).sp, color = Color.White)
    }
}

/**
 * Partner-org row — port of IntroScreenPartners. iOS shows NIH, U Michigan,
 * Harvard logos.
 * TODO(port): assets nih / umich / harvard — not yet imported; labeled chips
 *   stand in so the row shape is right.
 */
@Composable
private fun IntroScreenPartners() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        SmallText("Partnering with", Modifier.alpha(0.5f), color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2)) {
            listOf("NIH", "U Michigan", "Harvard").forEach { name ->
                MiniText(name, color = Color.White)
            }
        }
    }
}

/**
 * TextIconButton — port of the iOS `TextIconButton`. Rounded pill with a leading
 * SF symbol. The new-style variant uses the clear30 gradient (white text/icon);
 * the basic variant uses a white pill (brand-green icon, black text).
 */
@Composable
private fun TextIconButton(text: String, iconName: String, gradient: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(if (gradient) Clear30Gradients.clear30 else Clear30Gradients.white)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
    ) {
        Icon(
            sfSymbol(iconName),
            contentDescription = null,
            tint = if (gradient) Color.White else Clear30Colors.green,
            modifier = Modifier.size(18.dp),
        )
        SmallText(text, color = if (gradient) Color.White else Clear30Colors.text)
    }
}
