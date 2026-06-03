package org.clear30.views.newuser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

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
 * IntroScreenHandler — ported from IntroScreenHandler.swift. The experiment-
 * driven variant selection is simplified to the basic intro for now (new-style /
 * remote-config variants are a TODO); logo/partner imagery awaits asset migration.
 */
@Composable
fun IntroScreenHandler(onGetStarted: () -> Unit, onSignIn: (() -> Unit)? = null) {
    IntroScreen(
        info = IntroScreenInfo(
            type = "basic",
            intro = "Find",
            title = "Intention & Clarity",
            subtitle = "Beyond Weed",
        ),
        onGetStarted = onGetStarted,
        onSignIn = onSignIn,
    )
}

@Composable
fun IntroScreen(info: IntroScreenInfo, onGetStarted: () -> Unit, onSignIn: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Clear30Gradients.clear30)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TODO(assets): clear30_logo is SVG — import via Studio "SVG -> Vector Asset"
        Heading2("Clear30", color = Color.White)
        Spacer(Modifier.weight(1f))

        // Migrated raster hero asset (Intro Calendar Flat -> intro_calendar_flat)
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(org.clear30.R.drawable.intro_calendar_flat),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.8f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Spacer(Modifier.weight(1f))

        info.intro?.let { Heading3(it, color = Color.White.copy(alpha = 0.5f)) }
        Heading1(info.title, color = Color.White)
        info.subtitle?.let { Heading2(it, color = Color.White) }
        if (info.showStars == true) Laurels(Modifier.padding(top = Dimens.cardSpacing * 2))

        Spacer(Modifier.weight(1f))

        // TextIconButton — white pill with leaf icon + label
        Row(
            Modifier
                .pressScale(onClick = onGetStarted)
                .clip(RoundedCornerShape(Dimens.cornerRadius))
                .background(Color.White)
                .padding(horizontal = 25.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(sfSymbol(info.buttonSFSymbol ?: "leaf.fill"), contentDescription = null, tint = Clear30Colors.green, modifier = Modifier.size(18.dp))
            SmallText(info.buttonText ?: "Get Started", color = Clear30Colors.text)
        }

        if (onSignIn != null) {
            TinyText(
                "Already have an account? Sign in!",
                Modifier
                    .padding(top = Dimens.cardSpacing)
                    .pressScale(onClick = onSignIn)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
            )
        }

        MiniText(
            "By clicking 'Get Started' you are agreeing to the Privacy Policy and the Terms and Conditions",
            Modifier.padding(top = Dimens.cardSpacing),
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun Laurels(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
        repeat(5) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = Clear30Colors.journal1, modifier = Modifier.size(20.dp))
        }
    }
}
