package org.clear30.views.existinguser.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.clear30.R
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Each benefit: emoji + copy + the gradient for its emoji chip. */
private val THERAPY_BULLETS = listOf(
    Triple("🤩", "Up to 50% more affordable than traditional in-person therapy without insurance.", Clear30Gradients.clear30),
    Triple("🥹", "Nearly 3 million people have found help through online therapy on BetterHelp.", Clear30Gradients.meditation),
    Triple("💰", "20% off — exclusive to the Clear30 community.", Clear30Gradients.clear30Bright),
    Triple("💬", "Message your licensed therapist anytime.", Clear30Gradients.community),
    Triple("✅", "Switch therapists or cancel anytime.", Clear30Gradients.sleep),
)

/**
 * BetterHelp ("Is Therapy For Me?") — Clear30 × BetterHelp intro, benefit cards,
 * and a "Claim 20% Discount" affiliate CTA (BetterHelp.swift).
 *
 * Polished beyond the iOS baseline: a hero card + per-benefit chip cards that
 * stagger in (fade + slide) on appear, and a gently breathing CTA so the offer
 * draws the eye without nagging.
 */
@Composable
fun BetterHelp(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    // Entrance: flip to true on first frame so the AnimatedVisibility children
    // play their staggered enter transitions.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // Breathing CTA.
    val pulse = rememberInfiniteTransition(label = "cta")
    val ctaScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ctaScale",
    )

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Is Therapy For Me?")
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Reveal(shown, index = 0) { HeroCard() }

            THERAPY_BULLETS.forEachIndexed { i, (emoji, text, accent) ->
                Reveal(shown, index = i + 1) { BenefitCard(emoji, text, accent) }
            }
        }

        DefaultButton(
            "Claim 20% Discount",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = ctaScale; scaleY = ctaScale },
        ) { uriHandler.openUri("https://betterhelp.com/clear20") }

        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        TinyText(
            "Disclosure: As a BetterHelp Affiliate, we receive compensation from BetterHelp or other sources " +
                "if you purchase products or services through the links provided on this page.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Staggered fade + slide-up entrance, delayed by [index]. */
@Composable
private fun Reveal(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    val delay = index * 85
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 450, delayMillis = delay)) +
            slideInVertically(tween(durationMillis = 450, delayMillis = delay)) { it / 4 },
    ) { content() }
}

@Composable
private fun HeroCard() {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                Heading2("Clear30", color = Clear30Colors.green)
                SmallText("×", color = Clear30Colors.text.copy(alpha = 0.5f))
                Image(
                    painter = painterResource(R.drawable.better_help),
                    contentDescription = "BetterHelp",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(30.dp),
                )
            }
            SmallText(
                "Starting therapy can be scary 😬\nWe're here to help ☺️",
                color = Clear30Colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BenefitCard(emoji: String, text: String, accent: Brush) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Heading3(emoji)
            }
            SmallText(text, color = Clear30Colors.text, modifier = Modifier.weight(1f))
        }
    }
}
