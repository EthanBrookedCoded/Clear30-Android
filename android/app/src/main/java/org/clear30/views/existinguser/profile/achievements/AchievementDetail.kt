package org.clear30.views.existinguser.profile.achievements

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.data.model.AchievementDefinition
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * AchievementReveal — full-screen reward popup (port of AchievementDetail.swift +
 * ParallaxCard.swift). A blurred rarity-color glow behind a gyroscope-driven 3D
 * parallax card (device tilt → rotationX/rotationY + a moving shine), then the
 * achievement icon, name, description, and stats. First-view achievements play
 * the unlock sequence: scale-in → shake (glow builds) → slam + confetti →
 * staggered text reveal. Already-seen ones show everything immediately. Left/right
 * chevrons page through the earned carousel.
 */
@Composable
fun AchievementReveal(
    achievements: List<AchievementDefinition>,
    startKey: String,
    isVisited: (String) -> Boolean,
    onVisited: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (achievements.isEmpty()) return
    // Track the shown achievement by KEY, not by a fixed index. Marking an
    // achievement visited re-sorts `achievements` (new-first), so a remembered
    // index would resolve to a DIFFERENT item after the visit write (I44). The
    // key is stable; the index is derived from it each recomposition.
    var currentKey by remember { mutableStateOf(startKey) }
    val index = achievements.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    val current = achievements[index]
    val rarity = current.computedRarity

    // Animation state, reset whenever the carousel index changes.
    val cardScale = remember { Animatable(0.3f) }
    val slam = remember { Animatable(1f) }
    val shakeRot = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    var showCard by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(0) }

    val tilt by rememberDeviceTilt()

    LaunchedEffect(currentKey) {
        // Reset everything for this card.
        showCard = false; showIcon = false; showName = false; showDesc = false; showBadges = false
        shakeRot.snapTo(0f); slam.snapTo(1f); glow.snapTo(0f)

        val firstView = !isVisited(current.key)
        onVisited(current.key)

        if (!firstView) {
            cardScale.snapTo(1f)
            showCard = true; showIcon = true; showName = true; showDesc = true; showBadges = true
            return@LaunchedEffect
        }

        // Unlock sequence.
        cardScale.snapTo(0.3f)
        delay(250)
        showCard = true
        cardScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 220f))
        delay(500)

        // Shake while the glow + scale build.
        launch { glow.animateTo(1f, tween(1250)) }
        launch { cardScale.animateTo(1.125f, tween(1250)) }
        shakeRot.animateTo(0f, keyframes {
            durationMillis = 1250
            val n = 12
            for (i in 0..n) {
                val frac = i.toFloat() / n
                val amp = 5f + frac * 5f
                val v = if (i == n) 0f else if (i % 2 == 0) amp else -amp
                v at (1250 * frac).toInt()
            }
        })

        // Slam + confetti.
        launch { cardScale.animateTo(1f, tween(150)) }
        confetti++
        slam.animateTo(1.25f, spring(dampingRatio = 0.5f, stiffness = 900f))
        launch { slam.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }

        // Staggered reveal.
        showIcon = true
        delay(500); showName = true
        delay(300); showDesc = true
        delay(300); showBadges = true
    }

    val density = LocalDensity.current.density

    BoxWithConstraints(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        // Responsive card metrics (AchievementDetail.swift: card = 60% width ×
        // 40% height, inset = 40% × 25%, icon = 17.5% width — of the screen).
        val cardWidth = maxWidth * 0.6f
        val cardHeight = maxHeight * 0.4f
        val insetWidth = maxWidth * 0.4f
        val insetHeight = maxHeight * 0.25f
        val iconSize = maxWidth * 0.175f

        // Blurred rarity glow.
        Box(Modifier.fillMaxSize().blur(60.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(rarity.color1.copy(alpha = 0.5f), radius = size.width, center = Offset(-size.width * 0.25f, -size.height * 0.1f))
                drawCircle(rarity.color2.copy(alpha = 0.5f), radius = size.width, center = Offset(size.width * 0.75f, size.height * 0.6f))
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Parallax card.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(cardWidth).height(cardHeight)
                        .graphicsLayerCard(tilt, cardScale, slam, shakeRot, showCard, density),
                    contentAlignment = Alignment.Center,
                ) {
                    ParallaxCardContent(current, tilt, showIcon, insetWidth, insetHeight, iconSize)
                }
                ConfettiBurst(confetti, Modifier.size(360.dp))
            }

            Spacer(Modifier.height(Dimens.cardSpacing * 4))

            // Name.
            val nameAlpha by animateFloatAsState(if (showName) 1f else 0f, label = "name")
            Heading3(current.name, color = Color.White, modifier = Modifier.graphicsAlpha(nameAlpha))

            Spacer(Modifier.height(Dimens.cardSpacing / 2))

            // Description.
            val descAlpha by animateFloatAsState(if (showDesc) 0.75f else 0f, label = "desc")
            SmallText(current.description, color = Color.White, modifier = Modifier.graphicsAlpha(descAlpha))

            Spacer(Modifier.height(Dimens.cardSpacing * 2))

            // Stats badge.
            val badgeAlpha by animateFloatAsState(if (showBadges) 1f else 0f, label = "badge")
            statsText(current)?.let { stats ->
                Box(Modifier.graphicsAlpha(badgeAlpha)) {
                    StatsBadge(rarity, stats)
                }
            }
        }

        // Carousel chevrons. A glowing white dot above a chevron flags unvisited
        // achievements in that direction (AchievementDetail.swift navButtons).
        if (achievements.size > 1) {
            val hasUnvisitedLeft = (0 until index).any { !isVisited(achievements[it].key) }
            val hasUnvisitedRight = (index + 1 until achievements.size).any { !isVisited(achievements[it].key) }
            Box(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding), contentAlignment = Alignment.BottomStart) {
                if (index > 0) {
                    Box {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton("chevron.left", tint = Color.White) { currentKey = achievements[index - 1].key }
                        }
                        if (hasUnvisitedLeft) UnvisitedDot(Modifier.align(Alignment.TopStart).padding(1.dp))
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding), contentAlignment = Alignment.BottomEnd) {
                if (index < achievements.size - 1) {
                    Box {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton("chevron.right", tint = Color.White) { currentKey = achievements[index + 1].key }
                        }
                        if (hasUnvisitedRight) UnvisitedDot(Modifier.align(Alignment.TopEnd).padding(1.dp))
                    }
                }
            }
        }

        // Close.
        Box(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Clear30Colors.background), contentAlignment = Alignment.Center) {
                IconButton("xmark", tint = Clear30Colors.text, onClick = onClose)
            }
        }
    }
}

/** The card face: rarity gradient + translucent inset + white icon + rarity badge. */
@Composable
private fun ParallaxCardContent(
    def: AchievementDefinition,
    tilt: Offset,
    showIcon: Boolean,
    insetWidth: Dp,
    insetHeight: Dp,
    iconSize: Dp,
) {
    val rarity = def.computedRarity
    val corner = Dimens.cornerRadius * 3
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(corner)).background(rarity.gradient),
        contentAlignment = Alignment.Center,
    ) {
        // Translucent inset (parallax middle layer).
        Box(
            Modifier
                .width(insetWidth).height(insetHeight)
                .parallaxOffset(tilt, 1.2f)
                .clip(RoundedCornerShape(corner / 1.5f))
                .background(Color.White.copy(alpha = 0.25f)),
        )

        // Foreground: icon + badge.
        val contentAlpha by animateFloatAsState(if (showIcon) 1f else 0f, label = "icon")
        Column(
            Modifier.parallaxOffset(tilt, 1.6f).graphicsAlpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Icon(sfSymbol(def.sfSymbol), contentDescription = def.name, tint = Color.White, modifier = Modifier.size(iconSize))
            AchievementBadge(rarity = rarity, gradientBackground = false)
        }

        // Moving shine.
        ShineOverlay(tilt, corner)
    }
}

/** White dot + soft glow marking unvisited achievements in a carousel direction. */
@Composable
private fun UnvisitedDot(modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.5.dp)) {
        val r = size.minDimension / 2f
        // Soft white glow (iOS `.shadow(color: .white, radius: 7)`), drawn past
        // the dot's layout bounds.
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0f)),
                center = center,
                radius = r * 2f,
            ),
            radius = r * 2f,
            center = center,
        )
        drawCircle(Color.White, radius = r, center = center)
    }
}

/** Diagonal white sheen whose angle + intensity track the tilt. */
@Composable
private fun ShineOverlay(tilt: Offset, corner: androidx.compose.ui.unit.Dp) {
    val mag = kotlin.math.sqrt(tilt.x * tilt.x + tilt.y * tilt.y)
    val intensity = (mag / 25f).coerceIn(0f, 0.6f)
    val angle = atan2(tilt.x, tilt.y)
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(corner)).graphicsRotate(angle * 180f / Math.PI.toFloat()).background(
            Brush.linearGradient(
                0.35f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = intensity),
                0.65f to Color.White.copy(alpha = 0f),
            ),
        ),
    )
}

/** Confetti burst — particles fly outward + fade when [trigger] increments. */
@Composable
private fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900))
    }
    val p = progress.value
    if (p >= 1f) return
    // Precompute a stable (angle, distance-factor) per particle so they fly
    // along fixed rays instead of re-randomizing each frame.
    val particles = remember {
        List(40) { i ->
            val rnd = Random(i * 9973)
            (i.toFloat() / 40f) * 2f * Math.PI.toFloat() + rnd.nextFloat() to (0.6f + rnd.nextFloat() * 0.4f)
        }
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        particles.forEach { (angle, distFactor) ->
            val dist = size.minDimension * 0.5f * p * distFactor
            val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist)
            drawCircle(Color.White.copy(alpha = (1f - p)), radius = (1f - p) * 6f + 1f, center = pos)
        }
    }
}

@Composable
private fun StatsBadge(rarity: org.clear30.data.model.AchievementRarity, text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(Dimens.cornerRadius / 1.75f)).background(rarity.gradient).padding(horizontal = 10.dp, vertical = 5.dp),
    ) { SmallText(text, color = Color.White) }
}

/** "X% earned this" / "you and N others" — a light port of generateStatsText(). */
private fun statsText(def: AchievementDefinition): String? {
    val s = def.stats ?: return null
    if (s.totalEarned <= 0 || s.percentageEarned <= 0.0) return null
    val pct = if (s.percentageEarned < 1) "%.1f%%".format(s.percentageEarned) else "${s.percentageEarned.toInt()}%"
    return when {
        s.percentageEarned >= 50 -> "$pct earned this"
        s.percentageEarned >= 10 -> "Only $pct earned this"
        else -> "Top $pct!"
    }
}

// ── Modifier helpers ─────────────────────────────────────────────────────────

private fun Modifier.graphicsAlpha(a: Float): Modifier = this.graphicsLayer { alpha = a }

private fun Modifier.graphicsRotate(deg: Float): Modifier = this.graphicsLayer { rotationZ = deg }

private fun Modifier.parallaxOffset(tilt: Offset, factor: Float): Modifier = this.graphicsLayer {
    translationX = tilt.y * factor * density
    translationY = -tilt.x * factor * density
}

private fun Modifier.graphicsLayerCard(
    tilt: Offset,
    cardScale: Animatable<Float, *>,
    slam: Animatable<Float, *>,
    shakeRot: Animatable<Float, *>,
    showCard: Boolean,
    deviceDensity: Float,
): Modifier = this.graphicsLayer {
    rotationX = tilt.x
    rotationY = tilt.y
    rotationZ = shakeRot.value
    cameraDistance = 12f * deviceDensity
    val s = cardScale.value * slam.value
    scaleX = s
    scaleY = s
    alpha = if (showCard) 1f else 0f
}

/**
 * Device tilt (pitch, roll) in degrees, baseline-relative + low-pass smoothed,
 * clamped to ±[maxRotation]. Mirrors ParallaxCard's CMDeviceMotion attitude via
 * the accelerometer gravity vector.
 */
@Composable
private fun rememberDeviceTilt(maxRotation: Float = 35f, sensitivity: Float = 0.5f): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseline: Offset? = null
        var smoothed = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                val pitch = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
                val roll = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
                val raw = Offset(pitch, roll)
                val base = baseline ?: raw.also { baseline = it }
                val rel = Offset((raw.x - base.x) * sensitivity, (raw.y - base.y) * sensitivity)
                val clamped = Offset(rel.x.coerceIn(-maxRotation, maxRotation), rel.y.coerceIn(-maxRotation, maxRotation))
                smoothed = lerp(smoothed, clamped, 0.15f)
                state.value = smoothed
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return state
}
