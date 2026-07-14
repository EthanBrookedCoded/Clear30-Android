package org.clear30.views.existinguser

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import org.clear30.data.getMessages
import org.clear30.util.daysTo
import org.clear30.util.now
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
    experimentController: org.clear30.data.model.ExperimentController,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(CustomTabBarItem.TODAY) }
    var showPostAssessment by remember { mutableStateOf(false) }

    // Post-assessment auto-open (iOS AllTabs.swift:560-566): the break's N days
    // elapsed without completing it → present the flow, unless the welcome-back
    // window (5+ days away) owns the popup slot.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val awayDays = userInfo.previousSessionDate?.daysTo(now()) ?: 0
        if (program.postAssessmentCardText != null && awayDays < 5 && program.lastBreak != null) {
            showPostAssessment = true
        }
    }

    // Once per app-load (iOS AllTabs.swift:455-485): regenerate the transient
    // school messages, pull the health timeline content, and arm the milestone
    // notifications.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        userInfo.schoolData?.let {
            program.schoolMessages = it.getMessages(program, userInfo).toMutableList()
        }
        runCatching {
            org.clear30.data.HealthDataHandler.ensureHealthData(userInfo, program)
            org.clear30.data.NotificationHandler.scheduleHealthNotifications(userInfo, program)
        }
    }

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

    Scaffold(
        bottomBar = {
            CustomTabBar(selected = selected, onSelect = { tab ->
                selected = tab
                Logger.logEvent(userInfo.loggingID, tab.openedEvent)
            })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            // Crossfade every tab switch so navigation feels smooth instead of
            // snapping. Keyed on the selected tab; the outgoing screen fades out
            // as the incoming fades in.
            Crossfade(targetState = selected, animationSpec = tween(durationMillis = 280), label = "tab") { tab ->
                when (tab) {
                    CustomTabBarItem.TODAY -> TodayTab(
                        program, userInfo, journalEntries,
                        onOpenPostAssessment = { showPostAssessment = true },
                    )
                    CustomTabBarItem.COMMUNITY -> CommunityTab(program, userInfo)
                    CustomTabBarItem.GROUPS -> GroupsTab(program, userInfo)
                    CustomTabBarItem.PROFILE -> ProfileTab(
                        userInfo, program, journalEntries, onSignOut,
                        onOpenPostAssessment = { showPostAssessment = true },
                    )
                    CustomTabBarItem.SUPPORT -> SupportTab(program, userInfo, journalEntries)
                }
            }
        }
    }

    if (showPostAssessment) {
        program.lastBreak?.let { lastBreak ->
            org.clear30.views.existinguser.postassessment.PostAssessment(
                userInfo = userInfo,
                program = program,
                currentBreak = lastBreak,
                experimentController = experimentController,
                completion = { showPostAssessment = false },
            )
        } ?: run { showPostAssessment = false }
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
