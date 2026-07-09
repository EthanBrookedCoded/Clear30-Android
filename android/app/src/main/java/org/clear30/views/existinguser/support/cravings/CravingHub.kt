package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getMeditationResources
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * CravingHub — port of `CravingHubView` / `CravingFlow`. The cravings landing
 * surfaces three techniques: breathwork (a cadence panel), a rail of the craving
 * meditation library, and the games (Push & Pull / Pattern / Slice). Breathwork
 * opens [BreathingView]; each game opens a [CravingGameFlow] (intro → game →
 * post-game); meditations play through the shared MeditationPlayer.
 */
@Composable
fun CravingHub(program: Program, userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var meditations by remember { mutableStateOf<List<ProgramMeditation>>(emptyList()) }
    var loadingMeditations by remember { mutableStateOf(true) }

    var activeGame by remember { mutableStateOf<CravingGameType?>(null) }
    var breathing by remember { mutableStateOf<BreathingCadence?>(null) }
    var playing by remember { mutableStateOf<ProgramMeditation?>(null) }

    LaunchedEffect(Unit) {
        CravingStore.bind(userInfo) { scope.launch { Clear30Store.save(userInfo) } }
        Logger.logEvent(userInfo.loggingID, LogEventType.openedCravingResources)
        meditations = SupabaseController.getMeditationResources("craving_resources").getOrNull().orEmpty()
        loadingMeditations = false
    }

    // Full-screen sub-routes (games + breathing) replace the hub like stacked sheets.
    activeGame?.let { game ->
        BackHandler { activeGame = null }
        Box(Modifier.fillMaxSize().background(Clear30Colors.background).statusBarsPadding()) {
            CravingGameFlow(game) { activeGame = null }
        }
        return
    }
    breathing?.let { cadence ->
        BackHandler { breathing = null }
        Box(Modifier.fillMaxSize().background(Clear30Colors.background).statusBarsPadding()) {
            BreathingView(cadence) { breathing = null }
        }
        return
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing * 2),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                IconButton("chevron.backward") { onBack() }
                Heading3("Craving Support")
            }

            // Breathwork panel
            BreathworkPanel(
                onPanel = { Haptics.lightImpact(); breathing = BreathingCadence.CALM },
                onCadence = { Haptics.lightImpact(); breathing = it },
            )

            // Meditations rail
            if (meditations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Row(
                        Modifier.padding(horizontal = Dimens.horizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        Icon(sfSymbol("sun.horizon.fill"), null, tint = Clear30Colors.meditation1, modifier = Modifier.size(18.dp))
                        SmallText("Meditations")
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
                    ) {
                        meditations.forEach { med -> MeditationCard(med) { playing = med } }
                    }
                }
            }

            // Games
            Column(
                Modifier.padding(horizontal = Dimens.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Icon(sfSymbol("gamecontroller.fill"), null, tint = Clear30Colors.symptom1, modifier = Modifier.size(18.dp))
                    SmallText("Games")
                }
                CravingGameType.hubOrder.forEach { game -> GameRow(game) { Haptics.mediumImpact(); activeGame = game } }
            }

            Spacer(Modifier.height(Dimens.cardSpacing * 3))
        }
    }

    playing?.let { med ->
        org.clear30.views.existinguser.support.MeditationPlayer(meditation = med, program = program, onClose = { playing = null })
    }
}

@Composable
private fun BreathworkPanel(onPanel: () -> Unit, onCadence: (BreathingCadence) -> Unit) {
    Box(Modifier.padding(horizontal = Dimens.horizontalPadding)) {
        Box(
            Modifier.fillMaxWidth().cardStyle(gradient = Clear30Gradients.sleep).pressScale { onPanel() },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Icon(sfSymbol("wind"), null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Heading3("Breathwork", color = Color.White)
                }
                SmallText("Guided breathing", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = Dimens.cardSpacing))
                // 2x2 cadence chips.
                BreathingCadence.entries.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                        rowItems.forEach { c ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(99.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .pressScale { onCadence(c) }
                                    .padding(vertical = Dimens.cardSpacing / 2.5f),
                                contentAlignment = Alignment.Center,
                            ) {
                                MiniText("${c.title} ${c.pattern}", color = Color.White)
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeditationCard(med: ProgramMeditation, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.width(160.dp).height(120.dp).pressScale { onClick() },
        gradient = Clear30Gradients.meditation,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(sfSymbol("play.fill"), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallText(med.name, color = Color.White, maxLines = 2)
        }
    }
}

@Composable
private fun GameRow(game: CravingGameType, onClick: () -> Unit) {
    val unlocked = CravingStore.maxUnlockedLevel(game.gameName)
    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(Dimens.cornerRadius / 1.5f)).background(game.gradient), contentAlignment = Alignment.Center) {
                Icon(sfSymbol(game.tileSymbol), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                SmallText(game.gameTitle)
                MiniText("${game.gameSubtitle} · Level $unlocked/${CravingStore.MAX_LEVEL}", color = Clear30Colors.text.copy(alpha = 0.5f))
                // Level progress capsule.
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)) {
                    Box(
                        Modifier.fillMaxWidth(unlocked.toFloat() / CravingStore.MAX_LEVEL).height(5.dp)
                            .clip(RoundedCornerShape(99.dp)).background(game.gradient),
                    )
                }
            }
            Icon(sfSymbol("chevron.right"), null, tint = Clear30Colors.text.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
        }
    }
}

// MARK: - Game flow coordinator (intro → game → post-game)

private sealed interface GameStep {
    data class Playing(val mode: GameMode, val showIntro: Boolean) : GameStep
    data class Post(val result: GameResult) : GameStep
}

@Composable
private fun CravingGameFlow(intensity: CravingGameType, onClose: () -> Unit) {
    var step by remember {
        val initial: GameStep = if (CravingStore.isInfiniteUnlocked(intensity.gameName)) {
            GameStep.Playing(GameMode.Infinite, true)
        } else {
            GameStep.Playing(GameMode.Level(CravingStore.maxUnlockedLevel(intensity.gameName)), true)
        }
        mutableStateOf(initial)
    }

    when (val s = step) {
        is GameStep.Playing -> CravingGameView(
            intensity = intensity, mode = s.mode, showIntro = s.showIntro,
            onLevelComplete = { step = GameStep.Post(it) },
            onExit = { onClose() },
        )
        is GameStep.Post -> PostGameView(
            result = s.result,
            onPlay = { mode -> step = GameStep.Playing(mode, false) },
            onDone = onClose,
        )
    }
}

@Composable
private fun CravingGameView(
    intensity: CravingGameType, mode: GameMode, showIntro: Boolean,
    onLevelComplete: (GameResult) -> Unit, onExit: (GameResult) -> Unit,
) {
    when (intensity) {
        CravingGameType.PUSH_PULL -> PushPullGameView(intensity, mode, showIntro, onLevelComplete, onExit)
        CravingGameType.PATTERN -> PatternRepeatGameView(intensity, mode, showIntro, onLevelComplete, onExit)
        CravingGameType.SLICE -> SliceGameView(intensity, mode, showIntro, onLevelComplete, onExit)
    }
}
