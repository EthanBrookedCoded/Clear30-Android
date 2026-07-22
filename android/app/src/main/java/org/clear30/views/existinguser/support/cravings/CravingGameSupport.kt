package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.data.Clear30Store
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30CardDialog
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.softShadow
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics
import kotlin.random.Random

/**
 * Shared scaffolding for the craving games — a faithful port of
 * `CravingGameSupport.swift`: the game key/level model, the per-user progress
 * store (backed by [UserInfo]'s synced cache), the encouragement copy pools, and
 * the in-game chrome reused by all three games (level chip, quit button, score
 * badge, intro, level picker, glossy-dome surface).
 */

// MARK: - Game key (one per game)

enum class CravingGameType {
    PUSH_PULL, PATTERN, SLICE;

    val gradient: Brush
        get() = when (this) {
            PUSH_PULL -> Clear30Gradients.clear30
            PATTERN -> Clear30Gradients.claire
            SLICE -> Clear30Gradients.red
        }

    /** Solid tint used where a single accent colour is needed (post-game cells). */
    val tint: Color
        get() = when (this) {
            PUSH_PULL -> Clear30Colors.blue
            PATTERN -> Clear30Colors.claire1
            SLICE -> Clear30Colors.red1
        }

    val glow: Color
        get() = when (this) {
            PUSH_PULL -> Clear30Colors.green
            PATTERN -> Clear30Colors.claire2
            SLICE -> Clear30Colors.red2
        }

    val gameName: String
        get() = when (this) {
            PUSH_PULL -> "push_pull"
            PATTERN -> "pattern_repeat"
            SLICE -> "slice"
        }

    val gameTitle: String
        get() = when (this) {
            PUSH_PULL -> "Push & Pull"
            PATTERN -> "Pattern"
            SLICE -> "Slice"
        }

    val gameSubtitle: String
        get() = when (this) {
            PUSH_PULL -> "Sort the urge"
            PATTERN -> "Memory game"
            SLICE -> "Cut the cravings"
        }

    val tileSymbol: String
        get() = when (this) {
            PUSH_PULL -> "arrow.left.and.right"
            PATTERN -> "square.grid.2x2.fill"
            SLICE -> "scissors"
        }

    companion object {
        val hubOrder = listOf(PUSH_PULL, PATTERN, SLICE)
    }
}

// MARK: - Game mode + level helpers

sealed class GameMode {
    data class Level(val n: Int) : GameMode()
    data object Infinite : GameMode()

    val levelValue: Int get() = (this as? Level)?.n ?: 0
    val isInfinite: Boolean get() = this is Infinite
}

object GameLevels {
    const val COUNT = 12

    fun band(level: Int): String = when (level) {
        in 1..4 -> "Easy"
        in 5..8 -> "Normal"
        else -> "Hard"
    }

    /** 0..1 difficulty fraction across the 12 levels. */
    fun fraction(level: Int): Double {
        val clamped = level.coerceIn(1, COUNT)
        return (clamped - 1).toDouble() / (COUNT - 1).toDouble()
    }
}

// MARK: - Game result

data class GameResult(
    val game: String,
    val intensity: CravingGameType,
    val score: Int,
    val durationSeconds: Double,
    val maxStreak: Int,
    val levelReached: Int,
    val completed: Boolean,
    val wasInfinite: Boolean = false,
)

// MARK: - Progress store (backed by UserInfo's synced cache)

/**
 * Small persistence layer for the cravings flow — port of the iOS `CravingStore`.
 * [bind] wires the live [UserInfo] (so the static call sites in the games stay
 * unchanged) plus a persist hook that saves the user after each write, since the
 * progress lives in the synced `UserInfo.cache`.
 */
object CravingStore {
    const val MAX_LEVEL = 12

    private var userInfo: UserInfo? = null
    private var persist: (() -> Unit)? = null

    private const val BEST_PREFIX = "craving_best_score_"
    private const val UNLOCKED_PREFIX = "craving_max_unlocked_"
    private const val INFINITE_PREFIX = "craving_infinite_unlocked_"

    fun bind(userInfo: UserInfo, persist: () -> Unit) {
        this.userInfo = userInfo
        this.persist = persist
    }

    fun bestScore(game: String): Int = userInfo?.getCachedInt(BEST_PREFIX + game) ?: 0

    /** Records the score if it beats the stored best. */
    fun recordScore(score: Int, game: String) {
        val ui = userInfo ?: return
        val key = BEST_PREFIX + game
        if (score > ui.getCachedInt(key)) {
            ui.setCacheInt(key, score)
            persist?.invoke()
        }
    }

    /** Highest level unlocked for [game]. L1 is always unlocked. */
    fun maxUnlockedLevel(game: String): Int = maxOf(1, userInfo?.getCachedInt(UNLOCKED_PREFIX + game) ?: 1)

    /** Records a level completion; bumps maxUnlocked to clearedLevel + 1 (capped). */
    fun markLevelComplete(clearedLevel: Int, game: String): Int {
        val ui = userInfo ?: return minOf(MAX_LEVEL, maxOf(1, clearedLevel + 1))
        val key = UNLOCKED_PREFIX + game
        val previous = maxOf(1, ui.getCachedInt(key))
        val next = minOf(MAX_LEVEL, maxOf(previous, clearedLevel + 1))
        ui.setCacheInt(key, next)
        if (clearedLevel >= MAX_LEVEL) ui.setCacheBool(INFINITE_PREFIX + game, true)
        persist?.invoke()
        return next
    }

    /** True once the user has cleared L12 for this game (Endless unlocked). */
    fun isInfiniteUnlocked(game: String): Boolean = userInfo?.getCachedBool(INFINITE_PREFIX + game) ?: false
}

// MARK: - Encouragement copy pools

object GameQuotes {
    private val rewards = listOf(
        "That's real growth 🌳", "You're building momentum ⚡️", "Every rep counts 💪",
        "Crushing it 🛠️", "Keep shining 🌟", "Effort adds up 🧩", "Look at you go 🚀",
        "Strong move 💚", "You showed up, that matters", "One step closer to clear",
    )
    private val encouragement = listOf(
        "The urge fades. You don't.", "You're choosing the life you want.",
        "Future you is grateful right now.", "This feeling is temporary.",
        "You're stronger than the craving.", "Every 'no' rewires your brain.",
        "Clarity is worth the discomfort.", "You've got nothing to prove to a craving.",
        "Riding the wave, it always passes.", "Your goals are bigger than this moment.",
    )
    private val breathingRewards = listOf(
        "Slower breath, clearer mind.", "That's your nervous system, reset.",
        "Calm is a skill, you just practiced it.", "You gave yourself this moment. It counts.",
        "The craving got quieter, didn't it?", "Stillness looks good on you.",
        "One steady breath at a time.", "You showed up for yourself today.",
    )

    fun randomReward(): String = rewards.random()
    fun randomEncouragement(): String = encouragement.random()
    fun randomBreathingReward(): String = breathingRewards.random()
}

// MARK: - Glossy dome surface (shared by Push & Pull cards, Pattern pads, Slice orbs)

/**
 * A static 3D treatment: content on a gradient surface lit by a top-left radial
 * "dome" highlight, wrapped in a thick white outline, lifted by a colored glow +
 * a soft dark shadow (iOS `GlossyDomeStyle`).
 */
fun Modifier.glossyDome(
    gradient: Brush,
    glow: Color,
    cornerRadius: Dp,
    glowOpacity: Float = 0.5f,
    glowRadius: Dp = 18.dp,
    darkRadius: Dp = 8.dp,
): Modifier = this
    .softShadow(glow.copy(alpha = glowOpacity), cornerRadius = cornerRadius, blurRadius = glowRadius)
    .softShadow(Clear30Colors.shadow.copy(alpha = Clear30Colors.shadow.alpha * 0.5f), cornerRadius = cornerRadius, blurRadius = darkRadius)
    .clip(RoundedCornerShape(cornerRadius))
    .background(gradient)
    // Top-left dome highlight, drawn over the gradient but behind the content.
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.maxDimension * 0.85f,
            ),
        )
    }
    .border(3.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(cornerRadius))

// MARK: - In-game chrome

/** Star + score capsule (iOS `ScoreBadge`). */
@Composable
fun ScoreBadge(score: Int, gradient: Brush) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.button)
            .border(1.dp, gradient, RoundedCornerShape(99.dp))
            .padding(start = 6.dp, end = Dimens.cardSpacing, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(gradient), contentAlignment = Alignment.Center) {
            Icon(sfSymbol("star.fill"), null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        SmallText("$score")
    }
}

/** "Lvl N" / "Endless" chip; tappable when [onSelect] is set (opens the picker). */
@Composable
fun LevelChip(mode: GameMode, onSelect: (() -> Unit)? = null) {
    val label = if (mode.isInfinite) "Endless" else "Lvl ${mode.levelValue}"
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)
            .let { if (onSelect != null) it.clickable { Haptics.lightImpact(); onSelect() } else it }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TinyText(label, color = Clear30Colors.text)
        Icon(
            sfSymbol(if (onSelect != null) "chevron.down" else if (mode.isInfinite) "infinity" else "chevron.down"),
            null,
            tint = Clear30Colors.text.copy(alpha = 0.5f),
            modifier = Modifier.size(10.dp),
        )
    }
}

/** Circular quit X (iOS `GameQuitButton` → CircleIconButton). */
@Composable
fun GameQuitButton(onQuit: () -> Unit) {
    Box(
        Modifier.size(37.5.dp).clip(CircleShape).background(Clear30Colors.opacityGray)
            .clickable { Haptics.lightImpact(); onQuit() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol("xmark"), "Quit", tint = Clear30Colors.text, modifier = Modifier.size(15.dp))
    }
}

// MARK: - Pre-game intro

data class GameIntroLine(val icon: String, val text: String)

/** "Get ready" intro screen shown before a game (iOS `GameIntroView`). */
@Composable
fun GameIntroView(
    title: String,
    symbol: String,
    blurb: String,
    lines: List<GameIntroLine>,
    gradient: Brush,
    glow: Color,
    onClose: () -> Unit,
    onStart: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(top = Dimens.headingTopPadding * 3, bottom = Dimens.headingTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 1.5f),
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                Box(
                    Modifier.size(84.dp).softShadow(glow.copy(alpha = 0.5f), cornerRadius = 42.dp, blurRadius = 16.dp)
                        .clip(CircleShape).background(gradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol(symbol), null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Heading2(title)
                SmallText(blurb, color = Clear30Colors.text.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Clear30Colors.opacityGray), contentAlignment = Alignment.Center) {
                            Icon(sfSymbol(line.icon), null, tint = glow, modifier = Modifier.size(16.dp))
                        }
                        SmallText(line.text, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            StretchedButton("Start", gradient = gradient) { Haptics.mediumImpact(); onStart() }
        }
        // Always-available escape.
        Box(Modifier.align(Alignment.TopStart).padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
            GameQuitButton(onQuit = onClose)
        }
    }
}

// MARK: - Level picker

/** Grid of selectable levels + an Endless option (iOS `LevelPickerSheet`). */
@Composable
fun LevelPicker(
    gameTitle: String,
    gameName: String,
    current: GameMode,
    gradient: Brush,
    onPick: (GameMode) -> Unit,
) {
    val unlocked = CravingStore.maxUnlockedLevel(gameName)
    val infiniteUnlocked = CravingStore.isInfiniteUnlocked(gameName)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallText("$gameTitle levels")
            Spacer(Modifier.weight(1f))
            TinyText("$unlocked / ${CravingStore.MAX_LEVEL}", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(((CravingStore.MAX_LEVEL + 3) / 4 * 66).dp),
            userScrollEnabled = false,
        ) {
            items((1..CravingStore.MAX_LEVEL).toList()) { lvl ->
                LevelCell(lvl, lvl <= unlocked, current == GameMode.Level(lvl), gradient) {
                    if (lvl <= unlocked) { Haptics.lightImpact(); onPick(GameMode.Level(lvl)) }
                }
            }
        }
        EndlessCell(infiniteUnlocked, current == GameMode.Infinite, gradient) {
            if (infiniteUnlocked) { Haptics.lightImpact(); onPick(GameMode.Infinite) }
        }
    }
}

@Composable
private fun LevelCell(level: Int, unlocked: Boolean, current: Boolean, gradient: Brush, onClick: () -> Unit) {
    Box(
        Modifier.height(56.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(if (current) gradient else Clear30Gradients.button)
            .let { if (!current) it.border(1.dp, Clear30Colors.opacityGray, RoundedCornerShape(Dimens.cornerRadius)) else it }
            .clickable(enabled = unlocked, onClick = onClick)
            .let { if (!unlocked) it.then(Modifier) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (unlocked) {
            SmallText("$level", color = if (current) Color.White else Clear30Colors.text)
        } else {
            Icon(sfSymbol("lock.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
        }
    }
    // Locked cells render at half opacity via the icon tint; the box stays subtle.
}

@Composable
private fun EndlessCell(unlocked: Boolean, current: Boolean, gradient: Brush, onClick: () -> Unit) {
    Box(
        Modifier.height(56.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(if (current) gradient else Clear30Gradients.button)
            .let { if (!current) it.border(1.dp, Clear30Colors.opacityGray, RoundedCornerShape(Dimens.cornerRadius)) else it }
            .clickable(enabled = unlocked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Icon(
                sfSymbol(if (unlocked) "infinity" else "lock.fill"),
                null,
                tint = if (current) Color.White else Clear30Colors.text.copy(alpha = if (unlocked) 1f else 0.5f),
                modifier = Modifier.size(if (unlocked) 16.dp else 14.dp),
            )
            SmallText("Endless", color = if (current) Color.White else Clear30Colors.text)
        }
    }
}

/** Tiny deterministic-per-call RNG helper to vary visuals (kept out of Compose state). */
internal fun rng(): Random = Random(System.nanoTime())

/** Centered card dialog that hosts a game's level picker over the scrim. */
@Composable
fun GameSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Clear30CardDialog(onDismiss = onDismiss) {
        content()
    }
}
