package org.clear30.views.existinguser.support

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// Craving targets use the imported iOS cannabis art; Clear30 targets use emoji.
private val sliceCravingDrawables = listOf(
    org.clear30.R.drawable.cannabis_symbol, org.clear30.R.drawable.cannabis_plant,
    org.clear30.R.drawable.cannabis_bong, org.clear30.R.drawable.cannabis_jar,
    org.clear30.R.drawable.cannabis_dispensary, org.clear30.R.drawable.cannabis_cbd,
    org.clear30.R.drawable.cannabis_badge, org.clear30.R.drawable.cannabis_person,
    org.clear30.R.drawable.cannabis_search,
)
private val sliceClear30Emojis = listOf("🏃", "🧘", "❤️", "☀️", "🛌", "📚", "🥗", "🧠", "🚴", "💪", "🥕", "🧗")

private data class SliceTarget(
    val id: Long, val craving: Boolean, val emoji: String?, val drawable: Int?,
    val x: Float, val y: Float, val radius: Float,
    val spawnedAtMs: Long, val lifeSeconds: Double,
)

private class SliceEngine(private val scope: CoroutineScope) {
    var mode: GameMode = GameMode.Level(1)
    val targets = mutableStateListOf<SliceTarget>()
    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var maxCombo by mutableIntStateOf(0)
    var elapsed by mutableFloatStateOf(0f)
    var remaining by mutableFloatStateOf(0f)
    var cleared by mutableStateOf(false)
    var penaltyFlash by mutableIntStateOf(0)
    var penaltyToast by mutableStateOf<String?>(null)

    private var spawnJob: Job? = null
    private var tickJob: Job? = null
    private var startMs = System.currentTimeMillis()
    private var arenaW = 0f
    private var arenaH = 0f
    private var nextId = 0L
    // px-per-dp so the orbs are sized in dp (iOS points) but collide in arena px.
    private var scale = 1f

    val duration: Double get() = if (mode.isInfinite) 0.0 else 30 + GameLevels.fraction(mode.levelValue) * 60

    private val fraction: Double get() = if (mode.isInfinite) waveIntensity else GameLevels.fraction(mode.levelValue)

    private val waveIntensity: Double
        get() {
            val period = 40.0
            val phase = (elapsed.toDouble() % period) / period
            val wave = (1 - cos(2 * Math.PI * phase)) / 2
            val floor = 0.3
            return floor + (1 - floor) * wave
        }

    fun start(mode: GameMode, wPx: Float, hPx: Float, scale: Float) {
        this.mode = mode; arenaW = wPx; arenaH = hPx; this.scale = scale
        startMs = System.currentTimeMillis()
        score = 0; combo = 0; maxCombo = 0; elapsed = 0f
        cleared = false
        remaining = duration.toFloat()
        targets.clear()
        spawnLoop(); tickLoop()
    }

    fun stop() { spawnJob?.cancel(); tickJob?.cancel(); spawnJob = null; tickJob = null }

    /** Returns the targets hit at [point] (arena px) so the view can spark bursts. */
    fun registerSlice(point: Offset): List<SliceTarget> {
        val grace = 10f * scale
        val hit = targets.filter { t ->
            val dx = t.x - point.x; val dy = t.y - point.y
            sqrt(dx * dx + dy * dy) < t.radius + grace
        }
        if (hit.isEmpty()) return emptyList()
        targets.removeAll(hit)
        for (t in hit) {
            if (t.craving) {
                score += 1 + maxOf(0, combo / 3); combo++; maxCombo = maxOf(maxCombo, combo)
            } else {
                combo = 0; score = maxOf(0, score - 2); penaltyFlash++
                penaltyToast = "Don't slice the good stuff!"
                scope.launch { delay(1400); penaltyToast = null }
            }
        }
        Haptics.mediumImpact()
        return hit
    }

    fun makeResult(intensity: CravingGameType, completed: Boolean) = GameResult(
        game = "slice", intensity = intensity, score = score,
        durationSeconds = (System.currentTimeMillis() - startMs) / 1000.0,
        maxStreak = maxCombo, levelReached = mode.levelValue, completed = completed, wasInfinite = mode.isInfinite,
    )

    private fun spawnLoop() {
        spawnJob?.cancel()
        spawnJob = scope.launch {
            while (true) {
                val interval = if (mode.isInfinite) maxOf(0.35, 1.4 - fraction * 1.05) else maxOf(0.6, 1.5 - fraction * 0.9)
                delay((interval * 1000).toLong())
                val wave = if (mode.isInfinite) {
                    val w = fraction
                    if (w > 0.8) Random.nextInt(2, 4) else if (w > 0.55) Random.nextInt(1, 3) else 1
                } else {
                    if (mode.levelValue >= 9) Random.nextInt(1, 3) else 1
                }
                repeat(wave) { spawn() }
            }
        }
    }

    private fun tickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(50)
                elapsed = (System.currentTimeMillis() - startMs) / 1000f
                val now = System.currentTimeMillis()
                val survivors = targets.filter { (now - it.spawnedAtMs) / 1000.0 <= it.lifeSeconds }
                if (survivors.size != targets.size) {
                    targets.clear(); targets.addAll(survivors); combo = 0
                }
                if (!mode.isInfinite) {
                    remaining = maxOf(0f, (duration - elapsed).toFloat())
                    if (remaining <= 0f && !cleared) { cleared = true; stop() }
                }
            }
        }
    }

    private fun spawn() {
        val f = fraction
        // iOS radius range 42..56 pt (shrinking with difficulty), scaled to arena px
        // so the orbs are the right physical size on every screen density.
        val radiusDp = (42 - f * 14) + Random.nextDouble() * ((56 - f * 16) - (42 - f * 14))
        val radius = (radiusDp * scale).toFloat()
        val inset = radius + 12 * scale
        if (arenaW <= inset * 2 || arenaH <= inset * 2) return
        var chosen: Offset? = null
        repeat(14) {
            val cand = Offset(
                (inset + Random.nextDouble() * (arenaW - inset * 2)).toFloat(),
                (inset + Random.nextDouble() * (arenaH - inset * 2)).toFloat(),
            )
            val clashes = targets.any { t ->
                val dx = t.x - cand.x; val dy = t.y - cand.y
                sqrt(dx * dx + dy * dy) < t.radius + radius + 18 * scale
            }
            if (!clashes && chosen == null) chosen = cand
        }
        val pos = chosen ?: return
        val life = if (mode.isInfinite) maxOf(1.0, 2.4 - fraction * 1.2) else 2.5 - GameLevels.fraction(mode.levelValue) * 1.3
        val greenChance = 0.2 + fraction * 0.4
        val isClear30 = Random.nextDouble() < greenChance
        val emoji = if (isClear30) sliceClear30Emojis.random() else null
        val drawable = if (isClear30) null else sliceCravingDrawables.random()
        targets.add(
            SliceTarget(nextId++, !isClear30, emoji, drawable, pos.x, pos.y, radius, System.currentTimeMillis(), life),
        )
    }
}

@Composable
fun SliceGameView(
    intensity: CravingGameType,
    mode: GameMode,
    showIntro: Boolean,
    onLevelComplete: (GameResult) -> Unit,
    onExit: (GameResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val engine = remember { SliceEngine(scope) }
    var started by remember { mutableStateOf(!showIntro) }
    var currentMode by remember { mutableStateOf(mode) }
    var showLevelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(engine.cleared) { if (engine.cleared) onLevelComplete(engine.makeResult(intensity, completed = true)) }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    Box(Modifier.fillMaxSize()) {
        if (!started) {
            GameIntroView(
                title = "Slice", symbol = "scissors",
                blurb = "The longer you play, the more time you put between yourself and the craving.",
                lines = listOf(
                    GameIntroLine("hand.draw", "Swipe through red craving targets to slice them"),
                    GameIntroLine("leaf.fill", "Avoid the green Clear30 ones"),
                    GameIntroLine("timer", "Score as much as you can before time runs out"),
                ),
                gradient = intensity.gradient, glow = intensity.glow,
                onClose = { onExit(engine.makeResult(intensity, completed = false)) },
                onStart = { started = true },  // engine starts once the arena is measured
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }
                LaunchedEffect(currentMode, wPx, hPx) {
                    if (wPx > 0 && hPx > 0) engine.start(currentMode, wPx, hPx, density.density)
                }

                SliceArena(engine, density)
                PenaltyFlash(engine.penaltyFlash)

                Column(Modifier.fillMaxSize().padding(top = Dimens.headingTopPadding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        ScoreBadge(engine.score, intensity.gradient)
                        if (!currentMode.isInfinite) TimerRing(engine, Modifier.size(24.dp))
                        Spacer(Modifier.weight(1f))
                        LevelChip(currentMode) { showLevelPicker = true }
                        GameQuitButton { engine.stop(); onExit(engine.makeResult(intensity, completed = false)) }
                    }
                    Spacer(Modifier.weight(1f))
                    engine.penaltyToast?.let { toast ->
                        Box(Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing * 2), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Gradients.red)
                                    .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                            ) { SmallText(toast, color = Color.White) }
                        }
                    }
                }
            }
        }

        if (showLevelPicker) {
            GameSheet(onDismiss = { showLevelPicker = false }) {
                LevelPicker("Slice", "slice", currentMode, intensity.gradient) { picked ->
                    currentMode = picked; showLevelPicker = false
                }
            }
        }
    }
}

@Composable
private fun SliceArena(engine: SliceEngine, density: androidx.compose.ui.unit.Density) {
    val trail = remember { mutableStateListOf<Offset>() }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = { trail.clear() },
                onDragCancel = { trail.clear() },
            ) { change, _ ->
                trail.add(change.position)
                if (trail.size > 14) trail.removeAt(0)
                engine.registerSlice(change.position)
            }
        },
    ) {
        // Targets. `key(t.id)` ties each orb's composition state to ITS target, so
        // the entrance below plays once per spawn and survivors keep their state
        // when the tick loop rebuilds the list after an expiry.
        engine.targets.forEach { t ->
            key(t.id) {
                val sizeDp = with(density) { (t.radius * 2f).toDp() }
                Box(
                    Modifier.offset { IntOffset((t.x - t.radius).roundToInt(), (t.y - t.radius).roundToInt()) }
                        .size(sizeDp).spawnEntrance().clip(CircleShape)
                        .background(if (t.craving) Clear30Gradients.red else Clear30Gradients.clear30)
                        .border(3.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (t.drawable != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(t.drawable),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                            modifier = Modifier.size(with(density) { t.radius.toDp() }),
                        )
                    } else if (t.emoji != null) {
                        Heading2(t.emoji)
                    }
                }
            }
        }
        // Slice trail.
        if (trail.size > 1) {
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(trail.first().x, trail.first().y)
                    trail.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    brush = Brush.linearGradient(
                        listOf(Clear30Colors.red1.copy(alpha = 0.5f), Clear30Colors.red2),
                        start = trail.first(), end = trail.last(),
                    ),
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * Entrance for a freshly spawned orb — spring scale-up from 0.5 + fade-in, so
 * targets grow onto the board instead of popping. Matches SliceGame.swift, which
 * appends inside `withAnimation(.spring(response: 0.26, dampingFraction: 0.7))`
 * with `.transition(.scale(scale: 0.5).combined(with: .opacity))` on the target.
 *
 * Presentation only: hit-testing runs in the engine against the target's stored
 * position/radius, and `graphicsLayer` doesn't touch layout — so an orb is
 * tappable, with its full hit area, from the frame it spawns.
 *
 * The layer is dropped once the spring settles: a board with nothing invalidating
 * it can otherwise leave an orb behind a stale graphics layer (bug E15).
 */
@Composable
private fun Modifier.spawnEntrance(): Modifier {
    val progress = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // SwiftUI response 0.26 → stiffness = (2π / 0.26)² ≈ 584.
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 584f))
        settled = true
    }
    if (settled) return this
    return this.graphicsLayer {
        val s = 0.5f + progress.value * 0.5f
        scaleX = s
        scaleY = s
        alpha = progress.value.coerceIn(0f, 1f)
    }
}

@Composable
private fun TimerRing(engine: SliceEngine, modifier: Modifier) {
    val ratio = if (engine.duration > 0) (engine.remaining / engine.duration.toFloat()).coerceIn(0f, 1f) else 0f
    Canvas(modifier) {
        val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
        drawArc(
            color = Clear30Colors.opacityGray, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            style = stroke,
        )
        drawArc(
            brush = Clear30Gradients.clear30, startAngle = -90f, sweepAngle = 360f * ratio, useCenter = false,
            style = stroke,
        )
    }
}

@Composable
private fun PenaltyFlash(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(0.5f)
        alpha.animateTo(0f, animationSpec = tween(400))
    }
    if (alpha.value > 0f) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.red1.copy(alpha = alpha.value * 0.5f)))
    }
}
