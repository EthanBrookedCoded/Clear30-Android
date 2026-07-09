package org.clear30.views.newuser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import org.clear30.views.components.StatusBarStyle
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Lexend

/**
 * SplashScreen — the brand-gradient cold-start screen. Full-bleed brand
 * gradient (blue → green diagonal), centered "Clear30" wordmark with a subtle
 * breathing scale, three pulsing dots underneath suggesting that we're loading
 * something. Status bar icons flip to white so they stay legible over the
 * dark gradient.
 *
 * The iOS app uses a SwiftUI Splash with the same gradient + the brand SVG
 * logo. Until the SVG is imported as a vector asset, we render the wordmark
 * as Lexend Bold so the visual weight matches.
 */
@Composable
fun SplashScreen() {
    StatusBarStyle(forceLightIcons = true)

    Box(
        Modifier.fillMaxSize().background(Clear30Gradients.clear30),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BreathingIcon()
            Spacer(Modifier.height(40.dp))
            PulsingDots()
        }
    }
}

/**
 * The Clear30 app icon (green "30" mark) in a white rounded tile, with a subtle
 * breathing scale (1.0 → 1.04 → 1.0 over 2.4s). The white tile lets the green
 * mark read clearly over the blue→green gradient and matches the launcher icon.
 * Sized a touch under a typical splash logo so the proportions sit right.
 */
@Composable
private fun BreathingIcon() {
    val transition = rememberInfiniteTransition(label = "splash.breath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash.phase",
    )
    // 0..1 phase → soft sine-ish bell curve via |sin(πx)|
    val s = kotlin.math.sin(phase * kotlin.math.PI).toFloat()
    val scale = 1.0f + 0.04f * s
    Box(
        Modifier
            .scale(scale)
            .size(104.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = Color.Black.copy(alpha = 0.25f),
                ambientColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(org.clear30.R.drawable.clear30_icon),
            contentDescription = "Clear30",
            contentScale = ContentScale.Fit,
            // The PNG already has its own padding; a little more keeps the mark
            // from crowding the tile edges so the proportions read right.
            modifier = Modifier.fillMaxSize().padding(6.dp),
        )
    }
}

/**
 * Three small pills under the wordmark that cycle their opacity in sequence,
 * mirroring the iOS "thinking" indicator: each dot brightens 200ms after the
 * one to its left so a clean wave reads across the row.
 */
@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "splash.dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "splash.dotsPhase",
    )
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            // Each dot lit when phase ∈ [i, i+0.5), faded otherwise.
            val lit = phase % 3f >= i && phase % 3f < i + 0.5f
            val alpha by animateFloatAsState(
                if (lit) 1f else 0.25f,
                animationSpec = tween(220),
                label = "splash.dot.$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * A subtle "tagline" placeholder for use under the wordmark on a longer-running
 * splash. The standard splash doesn't show it; the loading variant does.
 */
@Composable
@Suppress("unused")
fun SplashTagline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Beyond Weed",
            color = Color.White.copy(alpha = 0.75f),
            fontFamily = Lexend,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
    }
}

@Suppress("unused") private val unusedHints = Clear30Colors.text
