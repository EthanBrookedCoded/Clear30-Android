package org.clear30.views.existinguser.support

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getMeditationResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.scrollShadowBleed
import org.clear30.views.components.PagerDots
import org.clear30.views.components.LoadingIcon
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.IconButton
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Craving / sleep "resources" libraries (Swift MeditationResourceKind). */
enum class MeditationResourceKind(
    val table: String,
    val titleBar: String,
    val introHeading: String,
    val introBody: String,
    val libraryTitle: String,
    val event: LogEventType,
) {
    CRAVING("craving_resources", "Cravings", "Take a deep breath", "Your craving will pass", "Your Craving Library", LogEventType.openedCravingResources),
    SLEEP("sleep_resources", "Sleep", "Take a deep breath", "It's time to rest", "Your Sleep Library", LogEventType.openedSleepResources),
}

/**
 * CravingResources — ported from CravingResources.swift (`MeditationResources`).
 * A breathing intro ("Take a deep breath" + a pulsing circle) over the guided
 * craving/sleep audio library fetched from the `library` schema, each playable via
 * the shared meditation mini-player.
 */
@Composable
fun CravingResources(program: Program, userInfo: UserInfo, kind: MeditationResourceKind, onBack: () -> Unit) {
    var resources by remember { mutableStateOf<List<ProgramMeditation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // iOS intro sequence (CravingResources.swift:124-150): centered heading at
    // +1s, dim body at +3s, auto-dismiss to the library at +7s (W14).
    var introStep by remember { mutableStateOf(0) }

    LaunchedEffect(kind) {
        Logger.logEvent(userInfo.loggingID, kind.event)
        SupabaseController.getMeditationResources(kind.table).onSuccess { resources = it }
        loading = false
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000); introStep = 1
        kotlinx.coroutines.delay(2000); introStep = 2
        kotlinx.coroutines.delay(4000); introStep = 3
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(kind.titleBar)
        }

        if (introStep < 3) {
            // Full-screen centered intro text, revealed in steps.
            Column(
                Modifier.fillMaxSize().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
            ) {
                if (introStep >= 1) Heading2(kind.introHeading)
                if (introStep >= 2) SmallText(kind.introBody, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        } else {
            Spacer(Modifier.height(Dimens.cardSpacing))
            // Library header: title + (sleep only) sleep-timer menu (iOS
            // sleepTimerMenu, CravingResources.swift:207-227).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Heading3(kind.libraryTitle)
                Spacer(Modifier.weight(1f))
                if (kind == MeditationResourceKind.SLEEP) SleepTimerMenu()
            }
            Spacer(Modifier.height(Dimens.cardSpacing))

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIcon() }
                resources.isEmpty() -> Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    SmallText("Guided audio will appear here soon.", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
                else -> {
                    // iOS: a HORIZONTAL pager of meditation cards (≈50% height),
                    // each embedding the player (MeditationFeedView), with page
                    // dots centered underneath (FeedView pageDots) and no
                    // visited checkbox in the card corner.
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { resources.size })
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                // scrollShadowBleed + contentPadding: the pager clips
                                // to its bounds, which cut the meditation cards' soft
                                // shadows at the sides (iOS scrollShadowFix pattern).
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                                    .scrollShadowBleed(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = Dimens.scrollShadowFix,
                                ),
                                pageSpacing = Dimens.cardSpacing,
                            ) { page ->
                                org.clear30.views.existinguser.today.MeditationFeedCard(
                                    resources[page], program,
                                    showVisited = false,
                                )
                            }
                        }
                        if (resources.size > 1) {
                            PagerDots(
                                count = resources.size,
                                current = pagerState.currentPage,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = Dimens.cardSpacing / 2),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Sleep-timer dropdown (iOS `sleepTimerMenu`) — arms the app-lifetime timer on
 *  [org.clear30.data.MeditationAudioController], which pauses the shared player
 *  on expiry even with the app backgrounded (the media service keeps it alive). */
@Composable
private fun SleepTimerMenu() {
    var open by remember { mutableStateOf(false) }
    val minutes by org.clear30.data.MeditationAudioController.sleepTimerMinutes
        .collectAsStateWithLifecycle()
    Box {
        Row(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                .background(Clear30Colors.opacityGray)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                org.clear30.views.components.sfSymbol("timer"),
                contentDescription = null,
                tint = Clear30Colors.text.copy(alpha = 0.5f),
                modifier = Modifier.size(13.dp),
            )
            TinyText(minutes?.let { "$it min" } ?: "Off", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<Int?>(null) + listOf(5, 10, 15, 30, 45, 60)).forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { TinyText(option?.let { "$it min" } ?: "Off") },
                    onClick = {
                        org.clear30.data.MeditationAudioController.setSleepTimer(option)
                        open = false
                    },
                )
            }
        }
    }
}
