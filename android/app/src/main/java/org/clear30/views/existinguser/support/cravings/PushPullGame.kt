package org.clear30.views.existinguser.support

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.views.components.Heading2
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** A Push & Pull card: a craving cue (swipe up) or a resilience cue (swipe down).
 *  Cannabis cues use the imported iOS cannabis art ([drawable]); resilience cues
 *  use an SF symbol. */
private data class PPCard(val id: Long, val cannabis: Boolean, val label: String, val drawable: Int?, val symbol: String?)

private val ppCannabis = listOf(
    "Dispensary" to org.clear30.R.drawable.cannabis_dispensary,
    "Bong" to org.clear30.R.drawable.cannabis_bong,
    "Home grow" to org.clear30.R.drawable.cannabis_plant,
    "The stash" to org.clear30.R.drawable.cannabis_jar,
    "CBD" to org.clear30.R.drawable.cannabis_cbd,
    "Getting high" to org.clear30.R.drawable.cannabis_person,
    "Chasing it" to org.clear30.R.drawable.cannabis_search,
    "The brand" to org.clear30.R.drawable.cannabis_badge,
)
private val ppResilience = listOf(
    "Career" to "briefcase.fill", "Family" to "person.2.fill",
    "Mental clarity" to "brain.head.profile", "Health" to "heart.fill",
    "Money saved" to "dollarsign.circle.fill", "Better sleep" to "moon.stars.fill",
    "Sharper focus" to "scope", "Connection" to "hands.sparkles.fill",
)

private class PushPullEngine(private val scope: CoroutineScope) {
    val deck = mutableStateListOf<PPCard>()
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var topScale by mutableFloatStateOf(1f)
    var topOpacity by mutableFloatStateOf(1f)
    var topRotation by mutableFloatStateOf(0f)
    var isCommitting by mutableStateOf(false)
    var feedbackText by mutableStateOf<String?>(null)
    var correctCount by mutableIntStateOf(0)
    var streak by mutableIntStateOf(0)
    var maxStreak by mutableIntStateOf(0)
    var cleared by mutableStateOf(false)
    var quoteText by mutableStateOf<String?>(null)

    var mode: GameMode = GameMode.Level(1)
    var cardsToClear by mutableIntStateOf(10)
    var threshold = 110f
    private var quoteMilestones = listOf<Int>()
    private var startedAtMs = System.currentTimeMillis()
    private var nextId = 0L

    private fun makeDeck(count: Int): List<PPCard> {
        val templates: List<PPCard> =
            ppCannabis.map { PPCard(0, true, it.first, it.second, null) } +
            ppResilience.map { PPCard(0, false, it.first, null, it.second) }
        val out = mutableListOf<PPCard>()
        val r = Random(System.nanoTime())
        while (out.size < count) {
            for (t in templates.shuffled(r)) {
                out.add(t.copy(id = nextId++))
                if (out.size >= count) break
            }
        }
        return out
    }

    fun start(mode: GameMode) {
        this.mode = mode
        val level = if (mode.isInfinite) 6 else mode.levelValue
        cardsToClear = if (mode.isInfinite) 999 else 5 + (GameLevels.fraction(level) * 9).roundToInt()
        threshold = if (mode.isInfinite) 90f else (110 - GameLevels.fraction(level) * 30).toFloat()
        val deckSize = if (mode.isInfinite) 40 else cardsToClear
        deck.clear(); deck.addAll(makeDeck(deckSize))
        correctCount = 0; streak = 0; maxStreak = 0
        startedAtMs = System.currentTimeMillis(); cleared = false
        offsetX = 0f; offsetY = 0f; topScale = 1f; topOpacity = 1f; topRotation = 0f; isCommitting = false
        val target = if (mode.isInfinite) 12 else cardsToClear
        quoteMilestones = listOf(target / 4, target / 2, target * 3 / 4).filter { it > 0 }
    }

    fun restart() = start(mode)

    fun onDragChanged(dy: Float) {
        if (isCommitting) return
        offsetX = 0f; offsetY = dy
        val progress = minOf(1f, abs(dy) / threshold)
        topScale = if (dy < 0) 1 - 0.08f * progress else 1 + 0.08f * progress
        topRotation = dy / 40f
        val fade = minOf(1f, abs(dy) / (threshold * 2.5f))
        topOpacity = 1 - 0.4f * fade
    }

    fun onDragEnded(dy: Float, screenH: Float, screenW: Float) {
        if (isCommitting) { return }
        val top = deck.firstOrNull() ?: return
        when {
            top.cannabis && dy < -threshold -> commit(up = true, screenH, screenW)
            !top.cannabis && dy >= threshold -> commit(up = false, screenH, screenW)
            else -> snapBack(top)
        }
    }

    private fun commit(up: Boolean, screenH: Float, screenW: Float) {
        isCommitting = true
        Haptics.lightImpact()
        feedbackText = null
        correctCount++; streak++; maxStreak = maxOf(maxStreak, streak)
        if (mode.isInfinite) threshold = minOf(135f, 90f + correctCount * 0.75f)
        if (quoteMilestones.contains(correctCount)) {
            quoteText = GameQuotes.randomEncouragement()
            scope.launch { delay(2200); quoteText = null }
        }
        val side = if (Random.nextBoolean()) 1f else -1f
        val drift = screenW * 0.45f
        val targetX = side * drift
        val targetY = if (up) -screenH * 0.9f else screenH * 0.5f
        val targetScale = if (up) 0.45f else 1.45f
        val targetRot = side * (14f + Random.nextFloat() * 10f)
        val startX = offsetX; val startY = offsetY; val startScale = topScale; val startRot = topRotation
        scope.launch {
            animate(0f, 1f, animationSpec = tween(280, easing = FastOutLinearInEasing)) { t, _ ->
                offsetX = lerp(startX, targetX, t); offsetY = lerp(startY, targetY, t)
                topScale = lerp(startScale, targetScale, t); topRotation = lerp(startRot, targetRot, t)
                topOpacity = lerp(1f, 0f, t)
            }
            topRotation = 0f
            if (deck.isNotEmpty()) deck.removeAt(0)
            offsetX = 0f; offsetY = 0f; topScale = 1f; topOpacity = 1f
            if (mode.isInfinite && deck.size < 5) deck.addAll(makeDeck(20))
            if (!mode.isInfinite && (correctCount >= cardsToClear || deck.isEmpty())) cleared = true
            isCommitting = false
        }
    }

    private fun snapBack(card: PPCard) {
        Haptics.mediumImpact()
        streak = 0
        feedbackText = if (card.cannabis) "Swipe up to push it away" else "Swipe down to pull it closer"
        val startX = offsetX; val startY = offsetY; val startScale = topScale; val startRot = topRotation
        scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { t, _ ->
                offsetX = lerp(startX, 0f, t); offsetY = lerp(startY, 0f, t)
                topScale = lerp(startScale, 1f, t); topRotation = lerp(startRot, 0f, t); topOpacity = lerp(topOpacity, 1f, t)
            }
        }
        scope.launch { delay(2000); feedbackText = null }
    }

    fun makeResult(intensity: CravingGameType, completed: Boolean) = GameResult(
        game = "push_pull", intensity = intensity, score = correctCount,
        durationSeconds = (System.currentTimeMillis() - startedAtMs) / 1000.0,
        maxStreak = maxStreak, levelReached = mode.levelValue, completed = completed, wasInfinite = mode.isInfinite,
    )
}

@Composable
fun PushPullGameView(
    intensity: CravingGameType,
    mode: GameMode,
    showIntro: Boolean,
    onLevelComplete: (GameResult) -> Unit,
    onExit: (GameResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val engine = remember { PushPullEngine(scope) }
    var started by remember { mutableStateOf(!showIntro) }
    var currentMode by remember { mutableStateOf(mode) }
    var showLevelPicker by remember { mutableStateOf(false) }

    fun startGame() { started = true; engine.start(currentMode) }
    LaunchedEffect(showIntro) { if (!showIntro) startGame() }
    LaunchedEffect(engine.cleared) { if (engine.cleared) onLevelComplete(engine.makeResult(intensity, completed = true)) }

    Box(Modifier.fillMaxSize()) {
        if (!started) {
            GameIntroView(
                title = "Push & Pull", symbol = "arrow.up.arrow.down",
                blurb = "A clinically proven exercise that can reinforce your intentions.",
                lines = listOf(
                    GameIntroLine("arrow.up", "Swipe cravings UP to push them away"),
                    GameIntroLine("arrow.down", "Swipe your goals DOWN to pull them close"),
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
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                ) {
                    ProgressDots(engine, intensity)
                    Spacer(Modifier.weight(1f))
                    LevelChip(currentMode) { showLevelPicker = true }
                    GameQuitButton { onExit(engine.makeResult(intensity, completed = false)) }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.horizontalPadding), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val hPx = with(density) { maxHeight.toPx() }
                    val wPx = with(density) { maxWidth.toPx() }
                    var totalDrag by remember(currentMode) { mutableFloatStateOf(0f) }

                    Box(
                        Modifier.fillMaxWidth().pointerInput(currentMode) {
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = { engine.onDragEnded(totalDrag, hPx, wPx); totalDrag = 0f },
                                onDragCancel = { engine.onDragEnded(totalDrag, hPx, wPx); totalDrag = 0f },
                            ) { _, delta -> totalDrag += delta; engine.onDragChanged(totalDrag) }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Up to three cards deep; the front card carries the live transform.
                        CardStack(engine)
                    }

                    engine.feedbackText?.let { txt ->
                        Box(Modifier.align(Alignment.TopCenter).padding(top = Dimens.cardSpacing)) {
                            FeedbackPill(txt)
                        }
                    }
                }
            }
        }

        // Encouragement quote.
        engine.quoteText?.let { quote ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.cardSpacing * 6)) {
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(intensity.gradient)
                        .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
                ) { TinyText(quote, color = Color.White) }
            }
        }

        if (showLevelPicker) {
            GameSheet(onDismiss = { showLevelPicker = false }) {
                LevelPicker("Push & Pull", "push_pull", currentMode, intensity.gradient) { picked ->
                    currentMode = picked; showLevelPicker = false; engine.start(currentMode)
                }
            }
        }
    }
}

@Composable
private fun CardStack(engine: PushPullEngine) {
    val cards = engine.deck.take(3)
    // Draw deepest first.
    cards.asReversed().forEach { card ->
        val index = cards.indexOf(card)
        val isTop = index == 0
        Box(
            Modifier
                .offset {
                    if (isTop) IntOffset(engine.offsetX.roundToInt(), engine.offsetY.roundToInt())
                    else IntOffset(0, (14 * index).dp.roundToPx())
                }
                .scale(if (isTop) engine.topScale else 1 - 0.06f * index)
                .alpha(if (isTop) engine.topOpacity else if (index == 1) 0.5f else 0.25f)
                .rotate(if (isTop) engine.topRotation else 0f),
        ) {
            PushPullCardView(card)
        }
    }
}

@Composable
private fun PushPullCardView(card: PPCard) {
    val gradient = if (card.cannabis) Clear30Gradients.symptomCard else Clear30Gradients.clear30
    val glow = if (card.cannabis) Clear30Colors.symptom1 else Clear30Colors.green
    Column(
        Modifier.fillMaxWidth().glossyDome(gradient, glow, cornerRadius = Dimens.cornerRadius * 1.5f)
            .padding(vertical = Dimens.cardSpacing * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        // Ringed glossy disc with the icon.
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.size(116.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                if (card.drawable != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(card.drawable),
                        contentDescription = card.label,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Clear30Colors.symptom1),
                        modifier = Modifier.size(54.dp),
                    )
                } else if (card.symbol != null) {
                    Icon(sfSymbol(card.symbol), null, tint = Clear30Colors.blue, modifier = Modifier.size(50.dp))
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.25f))
                .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
        ) { TinyText(card.label, color = Color.White) }
    }
}

@Composable
private fun ProgressDots(engine: PushPullEngine, intensity: CravingGameType) {
    if (!engine.mode.isInfinite) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(engine.cardsToClear.coerceAtMost(16)) { i ->
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (i < engine.correctCount) intensity.gradient else Clear30Gradients.gray),
                )
            }
        }
    } else {
        TinyText("Cleared: ${engine.correctCount}", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

@Composable
private fun FeedbackPill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGrayFlattened)
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
    ) { TinyText(text) }
}
