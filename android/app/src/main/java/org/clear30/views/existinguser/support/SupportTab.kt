package org.clear30.views.existinguser.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * SupportTab — ported from Support.swift (the support/library hub). Surfaces the
 * AI coaches (Claire / Dr Fred) and the content library categories as gradient
 * section cards. Library sections (Meditations / Reddit / YouTube / Messages /
 * Journal prompts) pull their items from `program.contentInfo` — same source
 * the iOS Library views consumed.
 */
private sealed interface SupportRoute {
    data object Hub : SupportRoute
    data object Claire : SupportRoute
    data object DrFred : SupportRoute
    data object Meditations : SupportRoute
    data object Reddits : SupportRoute
    data object YouTubes : SupportRoute
}

private data class SupportSection(val title: String, val icon: String, val gradient: Brush, val route: SupportRoute)

@Composable
fun SupportTab(program: Program, userInfo: UserInfo) {
    var route by remember { mutableStateOf<SupportRoute>(SupportRoute.Hub) }
    val back = { route = SupportRoute.Hub }

    // Deep link → drive the local nav to the right sub-screen, then ack so we
    // don't snap back here on every recomposition. The Meditation payload
    // carries a URL but the player still loads from program.contentInfo, so
    // we open the meditations grid and let the user pick.
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(sub) {
        when (sub) {
            is org.clear30.data.DeepLinkRoute.Claire -> route = SupportRoute.Claire
            is org.clear30.data.DeepLinkRoute.DrFred -> route = SupportRoute.DrFred
            is org.clear30.data.DeepLinkRoute.Meditation -> route = SupportRoute.Meditations
            else -> return@LaunchedEffect
        }
        org.clear30.AppState.requestSubRoute(null)
    }
    // Fire screen-entry analytics for each library section when the user
    // navigates in. Wrapping the LaunchedEffect in `key(route)` so it re-fires
    // every time the route changes, not just on first composition.
    androidx.compose.runtime.key(route) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val event = when (route) {
                is SupportRoute.Meditations -> LogEventType.openedMeditations
                is SupportRoute.Reddits -> LogEventType.openedReddit
                is SupportRoute.YouTubes -> LogEventType.openedYouTube
                else -> null
            }
            event?.let { Logger.logEvent(userInfo.loggingID, it) }
        }
    }
    // System back returns to the hub from any sub-screen instead of leaving the
    // app — without this, pressing back on Claire/Dr Fred / Meditations etc.
    // would kill the activity, since the local-state nav is invisible to the
    // platform back stack.
    androidx.activity.compose.BackHandler(enabled = route !is SupportRoute.Hub) { back() }
    when (val r = route) {
        is SupportRoute.Claire -> { ClaireChat(onBack = back); return }
        is SupportRoute.DrFred -> { DrFredChat(onBack = back); return }
        is SupportRoute.Meditations -> { MeditationsScreen(program, onBack = back); return }
        is SupportRoute.Reddits -> { ResourcesScreen(program, kind = ResourceKind.REDDIT, onBack = back); return }
        is SupportRoute.YouTubes -> { ResourcesScreen(program, kind = ResourceKind.YOUTUBE, onBack = back); return }
        is SupportRoute.Hub -> Unit
    }

    val sections = listOf(
        SupportSection("Talk to Claire", "bolt.fill", Clear30Gradients.claire, SupportRoute.Claire),
        SupportSection("Talk to Dr. Fred", "person.fill", Clear30Gradients.clear30, SupportRoute.DrFred),
        SupportSection("Meditations", "leaf.fill", Clear30Gradients.meditation, SupportRoute.Meditations),
        SupportSection("Reddit stories", "person.3", Clear30Gradients.reddit, SupportRoute.Reddits),
        SupportSection("YouTube", "play.fill", Clear30Gradients.youtube, SupportRoute.YouTubes),
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Heading1("Support")
        sections.forEach { s ->
            GradientActionButton(iconName = s.icon, gradient = s.gradient, title = s.title) {
                route = s.route
            }
        }
    }
}
