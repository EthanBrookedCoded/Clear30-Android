package org.clear30.views.existinguser.support

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.components.softShadow
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * Shown after a game level ends (iOS `PostGameView`). A "Level N complete" hero,
 * a horizontal level carousel (cleared levels carry a check, locked show a
 * padlock, the next level fades up and becomes the selection), and the actions:
 * a primary "Next level" / "Play again" / "Endless mode" plus "I'm all good".
 * Marks the cleared level complete in [CravingStore] on appear.
 */
@Composable
fun PostGameView(result: GameResult, onPlay: (GameMode) -> Unit, onDone: () -> Unit) {
    val level = result.levelReached
    val nextLevel: Int? = if (level < CravingStore.MAX_LEVEL) level + 1 else null
    val infiniteSlot = CravingStore.MAX_LEVEL + 1
    val infiniteUnlocked = CravingStore.isInfiniteUnlocked(result.game)

    var unlocked by remember { mutableIntStateOf(1) }
    var selected by remember { mutableIntStateOf(level) }
    var showCheck by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val previous = CravingStore.maxUnlockedLevel(result.game)
        if (result.completed && !result.wasInfinite) CravingStore.markLevelComplete(level, result.game)
        CravingStore.recordScore(result.score, result.game)
        unlocked = maxOf(previous, CravingStore.maxUnlockedLevel(result.game))
        selected = level
        if (result.completed) { showConfetti = true; Haptics.successHeavy() }
        delay(400); showCheck = true
        delay(550)
        revealed = true
        selected = nextLevel ?: if (infiniteUnlocked) infiniteSlot else level
    }

    fun primaryLabel(): String = when {
        selected == infiniteSlot -> "Endless mode"
        nextLevel != null && selected == nextLevel -> "Next level"
        selected == level -> "Play again"
        else -> "Play level $selected"
    }

    fun primaryIcon(): String? = when {
        selected == infiniteSlot -> "infinity"
        nextLevel != null && selected == nextLevel -> "arrow.right"
        selected == level -> "arrow.counterclockwise"
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(top = Dimens.headingTopPadding * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Hero
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                Box(
                    Modifier.size(64.dp)
                        .softShadow(Clear30Colors.shadow, cornerRadius = 32.dp, blurRadius = 8.dp)
                        .clip(CircleShape).background(result.intensity.gradient)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol("checkmark"), null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Heading2("Level $level complete 🎉")
                    SmallText("Scored ${result.score}", color = result.intensity.tint)
                }
            }

            // Level carousel
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { listState.scrollToItem((level - 1).coerceAtLeast(0)) }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing * 1.5f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
            ) {
                items(CravingStore.MAX_LEVEL) { idx ->
                    val lvl = idx + 1
                    LevelCarouselCell(
                        lvl = lvl, level = level, selected = selected, unlocked = unlocked, nextLevel = nextLevel,
                        revealed = revealed, showCheck = showCheck, intensity = result.intensity,
                    ) {
                        Haptics.lightImpact(); selected = lvl
                    }
                }
                item {
                    EndlessCarouselCell(
                        selected = selected == infiniteSlot, locked = !infiniteUnlocked,
                        dimReveal = level == CravingStore.MAX_LEVEL && infiniteUnlocked && !revealed,
                        intensity = result.intensity,
                    ) {
                        Haptics.lightImpact(); selected = infiniteSlot
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Actions
            Column(
                Modifier.fillMaxWidth().padding(start = Dimens.horizontalPadding, end = Dimens.horizontalPadding, bottom = Dimens.cardSpacing * 2.5f),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                GamePrimaryButton(primaryLabel(), primaryIcon(), result.intensity.gradient) {
                    onPlay(if (selected == infiniteSlot) GameMode.Infinite else GameMode.Level(selected))
                }
                GamePrimaryButton("I'm all good", null, null, onClick = onDone)
            }
        }

        if (showConfetti) ConfettiOverlay()
    }
}

@Composable
private fun LevelCarouselCell(
    lvl: Int, level: Int, selected: Int, unlocked: Int, nextLevel: Int?,
    revealed: Boolean, showCheck: Boolean, intensity: CravingGameType, onClick: () -> Unit,
) {
    val isSelected = lvl == selected
    val isCleared = lvl <= level
    val isNext = lvl == nextLevel
    val locked = lvl > unlocked
    val dimNext = isNext && !revealed
    val showBadge = if (lvl == level) showCheck else (isCleared && !isSelected)
    val cellAlpha = if (dimNext) 0.35f else if (locked) 0.5f else 1f
    val scale by animateFloatAsState(if (isSelected) 1.12f else 1f, label = "lvlScale")

    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier.size(52.dp).scale(scale).alpha(cellAlpha)
                .let { if (isSelected) it.softShadow(Clear30Colors.shadow, cornerRadius = 15.dp, blurRadius = 8.dp) else it }
                .clip(RoundedCornerShape(15.dp))
                .background(if (isSelected) intensity.gradient else Clear30Gradients.button)
                .let {
                    if (!isSelected) {
                        it.border(
                            1.dp,
                            if (isCleared) intensity.tint.copy(alpha = 0.4f) else Clear30Colors.opacityGray,
                            RoundedCornerShape(15.dp),
                        )
                    } else it
                }
                .clickable(enabled = !locked && !dimNext, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) {
                Icon(sfSymbol("lock.fill"), null, tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
            } else {
                SmallText("$lvl", color = if (isSelected) Color.White else Clear30Colors.text)
            }
        }
        if (showBadge) {
            Box(
                Modifier.size(17.dp).clip(CircleShape).background(intensity.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sfSymbol("checkmark"), null, tint = Color.White, modifier = Modifier.size(9.dp))
            }
        }
    }
}

@Composable
private fun EndlessCarouselCell(selected: Boolean, locked: Boolean, dimReveal: Boolean, intensity: CravingGameType, onClick: () -> Unit) {
    val cellAlpha = if (dimReveal) 0.35f else if (locked) 0.5f else 1f
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "endlessScale")
    Box(
        Modifier.size(52.dp).scale(scale).alpha(cellAlpha)
            .let { if (selected) it.softShadow(Clear30Colors.shadow, cornerRadius = 15.dp, blurRadius = 8.dp) else it }
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) intensity.gradient else Clear30Gradients.button)
            .let { if (!selected) it.border(1.dp, Clear30Colors.opacityGray, RoundedCornerShape(15.dp)) else it }
            .clickable(enabled = !locked && !dimReveal, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sfSymbol(if (locked) "lock.fill" else "infinity"),
            null,
            tint = if (selected) Color.White else Clear30Colors.text.copy(alpha = if (locked) 0.3f else 1f),
            modifier = Modifier.size(if (locked) 14.dp else 18.dp),
        )
    }
}

/** Gradient (or gray) pill with an optional leading icon — the games' CTA button. */
@Composable
internal fun GamePrimaryButton(text: String, icon: String?, gradient: Brush?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(gradient ?: Clear30Gradients.button)
            .clickable { Haptics.lightImpact(); onClick() }
            .padding(horizontal = 25.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(sfSymbol(icon), null, tint = if (gradient != null) Color.White else Clear30Colors.text, modifier = Modifier.size(16.dp))
        }
        SmallText(text, color = if (gradient != null) Color.White else Clear30Colors.text)
    }
}
