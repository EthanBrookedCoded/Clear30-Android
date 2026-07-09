package org.clear30.views.existinguser.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.roundToInt
import kotlin.random.Random

private val patternPadGradients: List<Brush> =
    listOf(Clear30Gradients.clear30, Clear30Gradients.meditation, Clear30Gradients.claire, Clear30Gradients.symptomCard)
private val patternPadGlows: List<Color> =
    listOf(Clear30Colors.green, Clear30Colors.meditation1, Clear30Colors.claire2, Clear30Colors.symptom1)
private val patternIconPool = listOf(
    "leaf.fill", "drop.fill", "moon.fill", "sun.max.fill", "sparkles", "flame.fill",
    "snowflake", "cloud.fill", "bolt.fill", "star.fill", "heart.fill", "wind",
)

/** Simon-style memory engine (port of iOS `PatternEngine`). */
private class PatternEngine(private val scope: CoroutineScope) {
    enum class Phase { IDLE, WATCHING, AWAITING_INPUT, LEVEL_CLEARED, FAILURE }

    var phase by mutableStateOf(Phase.IDLE)
    val sequence = mutableStateListOf<Int>()
    var inputIndex by mutableIntStateOf(0)
    var activeCell by mutableStateOf<Int?>(null)
    var score by mutableIntStateOf(0)
    var maxLength by mutableIntStateOf(0)
    var padSymbols by mutableStateOf(listOf("leaf.fill", "drop.fill", "moon.fill", "sun.max.fill"))
    var padColorOrder by mutableStateOf(listOf(0, 1, 2, 3))
    var mode: GameMode = GameMode.Level(1)

    private var startedAtMs = System.currentTimeMillis()
    private var playbackJob: Job? = null
    val cellCount = 4
    private val level get() = mode.levelValue

    private val clearLength get() = 3 + (GameLevels.fraction(level) * 7).roundToInt()
    private val difficulty get() = if (mode.isInfinite) minOf(1.0, sequence.size / 14.0) else GameLevels.fraction(level)
    private val speed get() = if (mode.isInfinite) maxOf(0.12, 0.6 - difficulty * 0.48) else maxOf(0.15, 0.6 - difficulty * 0.45)
    private val gap get() = speed * 0.4

    fun start(mode: GameMode) {
        this.mode = mode
        sequence.clear(); inputIndex = 0; score = 0; maxLength = 0
        startedAtMs = System.currentTimeMillis()
        val r = Random(System.nanoTime())
        padSymbols = patternIconPool.shuffled(r).take(cellCount)
        padColorOrder = (0 until cellCount).shuffled(r)
        nextRound()
    }

    fun restart() = start(mode)

    private fun nextRound() {
        sequence.add(nextIndex())
        inputIndex = 0
        phase = Phase.WATCHING
        playbackJob?.cancel()
        playbackJob = scope.launch { playSequence() }
    }

    private fun nextIndex(): Int {
        val last = sequence.lastOrNull() ?: return Random.nextInt(cellCount)
        return (last + Random.nextInt(1, cellCount)) % cellCount
    }

    fun handleTap(index: Int) {
        if (phase != Phase.AWAITING_INPUT || inputIndex >= sequence.size) return
        if (index == sequence[inputIndex]) {
            Haptics.lightImpact()
            score += 10 * maxOf(1, level)
            inputIndex++
            if (inputIndex >= sequence.size) roundCompleted()
        } else {
            Haptics.successLight()
            phase = Phase.FAILURE
        }
    }

    private fun roundCompleted() {
        maxLength = maxOf(maxLength, sequence.size)
        if (!mode.isInfinite && sequence.size >= clearLength) {
            phase = Phase.LEVEL_CLEARED
        } else {
            scope.launch { delay(500); nextRound() }
        }
    }

    fun makeResult(intensity: CravingGameType, completed: Boolean) = GameResult(
        game = "pattern_repeat", intensity = intensity, score = score,
        durationSeconds = (System.currentTimeMillis() - startedAtMs) / 1000.0,
        maxStreak = maxLength, levelReached = mode.levelValue, completed = completed, wasInfinite = mode.isInfinite,
    )

    private suspend fun playSequence() {
        val s = speed; val g = gap
        delay(400)
        for (index in sequence.toList()) {
            activeCell = index
            Haptics.lightImpact()
            delay((s * 1000).toLong())
            activeCell = null
            delay((g * 1000).toLong())
        }
        delay(100)
        phase = Phase.AWAITING_INPUT
    }

    fun stop() { playbackJob?.cancel() }
}

@Composable
fun PatternRepeatGameView(
    intensity: CravingGameType,
    mode: GameMode,
    showIntro: Boolean,
    onLevelComplete: (GameResult) -> Unit,
    onExit: (GameResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val engine = remember { PatternEngine(scope) }
    var started by remember { mutableStateOf(!showIntro) }
    var currentMode by remember { mutableStateOf(mode) }
    var showLevelPicker by remember { mutableStateOf(false) }
    var pulse by remember { mutableStateOf(false) }
    val padScale by animateFloatAsState(if (pulse) 0.95f else 1f, label = "padPulse")

    fun startGame() { started = true; engine.start(currentMode) }

    LaunchedEffect(showIntro) { if (!showIntro) startGame() }

    LaunchedEffect(engine.phase) {
        if (engine.phase == PatternEngine.Phase.AWAITING_INPUT) {
            pulse = true; delay(150); pulse = false
        }
        if (engine.phase == PatternEngine.Phase.LEVEL_CLEARED) {
            onLevelComplete(engine.makeResult(intensity, completed = true))
        }
    }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    Box(Modifier.fillMaxSize()) {
        if (!started) {
            GameIntroView(
                title = "Pattern", symbol = "square.grid.2x2.fill",
                blurb = "A memory game to occupy your mind and pull it away from the craving.",
                lines = listOf(
                    GameIntroLine("eye", "Watch the tiles light up in order"),
                    GameIntroLine("hand.tap", "Repeat the sequence from memory"),
                    GameIntroLine("chart.line.uptrend.xyaxis", "It grows each round, clear the level to win"),
                ),
                gradient = intensity.gradient, glow = intensity.glow,
                onClose = { onExit(engine.makeResult(intensity, completed = false)) },
                onStart = { startGame() },
            )
        } else {
            Column(Modifier.fillMaxSize().padding(top = Dimens.headingTopPadding)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    ScoreBadge(engine.score, intensity.gradient)
                    Spacer(Modifier.weight(1f))
                    LevelChip(currentMode) { showLevelPicker = true }
                    GameQuitButton { engine.stop(); onExit(engine.makeResult(intensity, completed = false)) }
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.horizontalPadding), contentAlignment = Alignment.Center) {
                    PatternPadGrid(engine, padScale)
                }

                PatternFooter(engine, intensity) { engine.restart() }
            }
        }

        if (showLevelPicker) {
            GameSheet(onDismiss = { showLevelPicker = false }) {
                LevelPicker("Pattern", "pattern_repeat", currentMode, intensity.gradient) { picked ->
                    currentMode = picked; showLevelPicker = false; engine.start(currentMode)
                }
            }
        }
    }
}

@Composable
private fun PatternPadGrid(engine: PatternEngine, padScale: Float) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1f).scale(padScale),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            PatternPad(engine, 0, Modifier.weight(1f).fillMaxSize())
            PatternPad(engine, 1, Modifier.weight(1f).fillMaxSize())
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            PatternPad(engine, 2, Modifier.weight(1f).fillMaxSize())
            PatternPad(engine, 3, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun PatternPad(engine: PatternEngine, index: Int, modifier: Modifier) {
    val colorIndex = engine.padColorOrder.getOrElse(index) { index }
    val gradient = patternPadGradients[colorIndex]
    val glow = patternPadGlows[colorIndex]
    val symbol = engine.padSymbols.getOrElse(index) { "circle.fill" }
    val active = engine.activeCell == index
    val awake = engine.phase == PatternEngine.Phase.AWAITING_INPUT
    val cellAlpha = if (active || awake) 1f else 0.4f
    val scale by animateFloatAsState(if (active) 1.05f else 1f, label = "padActive")

    Box(
        modifier
            .scale(scale).alpha(cellAlpha)
            .glossyDome(gradient, glow, cornerRadius = 34.dp, glowOpacity = if (active) 0.5f else 0.25f, glowRadius = if (active) 20.dp else 12.dp)
            .clickable(enabled = awake) { engine.handleTap(index) },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(Modifier.fillMaxSize().alpha(0.25f).background(Color.White, RoundedCornerShape(34.dp)))
        }
        Icon(
            sfSymbol(symbol),
            null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.3f).scale(if (active) 1.12f else 0.96f),
        )
    }
}

@Composable
private fun PatternFooter(engine: PatternEngine, intensity: CravingGameType, onRetry: () -> Unit) {
    AnimatedVisibility(
        visible = engine.phase == PatternEngine.Phase.FAILURE,
        enter = fadeIn(), exit = fadeOut(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                SmallText(
                    if (engine.score > 0) "Good run, that takes real focus." else "No worries, give it another go.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (engine.score > 0) {
                    SmallText("Streak ${engine.maxLength} · scored ${engine.score}", color = intensity.tint)
                }
            }
            GamePrimaryButton("Try again", "arrow.counterclockwise", intensity.gradient) { Haptics.lightImpact(); onRetry() }
        }
    }
}
