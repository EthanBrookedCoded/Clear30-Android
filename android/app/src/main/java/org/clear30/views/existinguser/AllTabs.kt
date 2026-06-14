package org.clear30.views.existinguser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.existinguser.community.CommunityTab
import org.clear30.views.existinguser.groups.GroupsTab
import org.clear30.views.existinguser.profile.ProfileTab
import org.clear30.views.existinguser.support.SupportTab
import org.clear30.views.existinguser.today.TodayTab

/**
 * AllTabs — ported from AllTabs.swift (the 5-tab backbone). Hosts the bottom
 * [CustomTabBar] and switches the content per selected tab, logging the
 * corresponding opened-tab analytics event.
 *
 * Each tab hosts its real screen (Today/Community/Groups/Profile/Support); the
 * navigation stack, sheets, toasts, and badge handlers from the iOS view model
 * are layered in as those land.
 */
@Composable
fun AllTabs(
    userInfo: UserInfo,
    program: Program,
    journalEntries: org.clear30.data.model.JournalEntries,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(CustomTabBarItem.TODAY) }

    // React to deep-link / push-notification tab requests from AppState.
    val requested by org.clear30.AppState.requestedTab.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(requested) {
        val name = requested ?: return@LaunchedEffect
        val target = runCatching { CustomTabBarItem.valueOf(name) }.getOrNull()
        if (target != null && target != selected) {
            selected = target
            Logger.logEvent(userInfo.loggingID, target.openedEvent)
        }
        org.clear30.AppState.requestTab(null)  // ack
    }

    // First-visit tutorial per tab — enqueues into PopupManager which renders
    // above every screen via AppOverlays. Idempotent: maybeShow short-circuits
    // once the screen is in the user's cached completion set.
    androidx.compose.runtime.LaunchedEffect(selected) {
        val screen = when (selected) {
            CustomTabBarItem.TODAY -> "today"
            CustomTabBarItem.PROFILE -> "profile"
            CustomTabBarItem.COMMUNITY -> "community"
            CustomTabBarItem.GROUPS -> "groups"
            CustomTabBarItem.SUPPORT -> null
        }
        screen?.let { org.clear30.data.TutorialController.maybeShow(userInfo, it) }
    }

    Scaffold(
        bottomBar = {
            CustomTabBar(selected = selected, onSelect = { tab ->
                selected = tab
                Logger.logEvent(userInfo.loggingID, tab.openedEvent)
            })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (selected) {
                CustomTabBarItem.TODAY -> TodayTab(program, userInfo)
                CustomTabBarItem.COMMUNITY -> CommunityTab(userInfo)
                CustomTabBarItem.GROUPS -> GroupsTab(userInfo)
                CustomTabBarItem.PROFILE -> ProfileTab(userInfo, program, journalEntries, onSignOut)
                CustomTabBarItem.SUPPORT -> SupportTab(program, userInfo, journalEntries)
            }
        }
    }
}

/** opened-tab analytics events (AllTabs.swift logScreen). */
private val CustomTabBarItem.openedEvent: LogEventType
    get() = when (this) {
        CustomTabBarItem.TODAY -> LogEventType.openedHome
        CustomTabBarItem.PROFILE -> LogEventType.openedProfile
        CustomTabBarItem.SUPPORT -> LogEventType.openedContent
        CustomTabBarItem.COMMUNITY -> LogEventType.openedCommunity
        CustomTabBarItem.GROUPS -> LogEventType.openedCommunity
    }
