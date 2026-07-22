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
import kotlinx.coroutines.launch
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
    // Popup-paywall presentation state: null = hidden, else the hard flag
    // (iOS viewModel.activeSheet = .payment(hard:)).
    var paywallSheet by remember { mutableStateOf<Boolean?>(null) }

    // iOS AllTabs.checkSubscription() (run once per load from loadStorage):
    // force-show the hard paywall for unpaid users, then ask RevenueCat whether
    // the entitlement drifted and flip the app state accordingly.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!userInfo.isPaid && userInfo.paywallHard) paywallSheet = true

        val (didChange, newEntitlement) =
            org.clear30.data.PaywallController.checkEntitlementChanged(userInfo)
        if (didChange) {
            if (newEntitlement != null) {
                handleUserPaid(userInfo, program, newEntitlement)
                selected = CustomTabBarItem.TODAY // iOS goToRoot()
                paywallSheet = null
            } else {
                handleUserUnsubscribed(userInfo)
                if (userInfo.paywallHard) paywallSheet = true // iOS checkForceShowPaywall()
            }
            org.clear30.data.Clear30Store.save(userInfo)
        }
    }

    // Externally requested popup paywall (deep links / upsell points — iOS
    // viewModel.activeSheet = .payment(...) call sites outside AllTabs).
    val paywallRequest by org.clear30.AppState.paywallRequest.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(paywallRequest) {
        paywallRequest?.let { hard ->
            paywallSheet = hard
            org.clear30.AppState.requestPaywall(null) // ack
        }
    }

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
        // Evaluate achievements once per app load too — check-ins and the
        // Profile section also evaluate, but both can be skipped for days
        // (e.g. the Day-0 layout hides the section entirely).
        runCatching { org.clear30.data.AchievementEngine.evaluateNow(userInfo, program) }
        // Referral-code group join deferred from onboarding (iOS
        // AllTabs.swift:554-557): the sales-slide referral stashed a group id;
        // consume it once the user is in the app and not already grouped.
        runCatching {
            val setup = org.clear30.data.Clear30Store.loadOnboardingSetup()
            val groupToJoin = setup.groupToJoin
            if (groupToJoin != null && userInfo.groupID.isNullOrEmpty()) {
                val err = org.clear30.data.GroupController(userInfo, program).join(groupToJoin)
                if (err == null) {
                    setup.groupToJoin = null
                    org.clear30.data.Clear30Store.save(setup)
                }
            }
        }
    }

    // The day-30 "breakdown 📦" deep link opens the post-assessment when one is
    // pending (iOS URLManager.swift:184-193).
    val breakdownSub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(breakdownSub) {
        if (breakdownSub is org.clear30.data.DeepLinkRoute.Breakdown) {
            if (program.postAssessmentCardText != null) showPostAssessment = true
            org.clear30.AppState.requestSubRoute(null)
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
            // W21: instant tab switch (no Crossfade) — the 280ms crossfade drew
            // BOTH tabs stacked mid-transition (overlapping cards) and composited
            // a fading alpha layer over the feed's blur shadows, corrupting them.
            when (selected) {
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

    // Popup paywall (iOS AllTabs .fullScreenCover(viewModel.paymentSheet) →
    // Paywall(popup: true, hard:)). Hard paywalls can't be dismissed
    // (interactiveDismissDisabled); soft ones close via back / the X button.
    paywallSheet?.let { hard ->
        org.clear30.views.components.Clear30FullScreenCover(
            onDismiss = { paywallSheet = null },
            canDismiss = !hard,
        ) {
            org.clear30.views.newuser.Paywall(
                userInfo = userInfo,
                popup = true,
                hard = hard,
            ) { entitlement ->
                if (entitlement != null) {
                    handleUserPaid(userInfo, program, entitlement)
                    selected = CustomTabBarItem.TODAY // iOS goToRoot()
                    // Persist off the dialog's composition (it unmounts next
                    // frame — a rememberCoroutineScope launch would be axed).
                    org.clear30.Clear30Application.appScope.launch {
                        org.clear30.data.Clear30Store.save(userInfo)
                    }
                }
                paywallSheet = null
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

/**
 * iOS AllTabs.handleUserPaid — flip the app into the paid state: store the
 * entitlement, re-enable + reschedule content notifications. (The iOS
 * OpeningAnimation "you're upgraded" popup and onboardingSetup.setup replay are
 * not ported — the former's view doesn't exist yet, the latter's halves are
 * consumed elsewhere/dormant, see AllNewUserViewModel.handlePayment.)
 * Caller persists userInfo.
 */
private fun handleUserPaid(
    userInfo: UserInfo,
    program: Program,
    entitlement: org.clear30.data.model.EntitlementType,
) {
    userInfo.currentEntitlementType = entitlement
    userInfo.notificationSettings?.options?.set(org.clear30.data.model.ToggleSettingsOption.CONTENT, true)
    val allMessages = program.contentInfo.values.flatMap { it.messages } + program.schoolMessages
    org.clear30.data.NotificationHandler.scheduleContent(userInfo, allMessages, program)
}

/**
 * iOS AllTabs.handleUserUnsubscribed — revoke paid-only state: clear the
 * entitlement (+ deprecated `paid` flag), drop scheduled content pushes, turn
 * every notification toggle off, and log it. Caller persists userInfo and
 * re-runs the hard-paywall check.
 */
private fun handleUserUnsubscribed(userInfo: UserInfo) {
    userInfo.currentEntitlementType = null
    userInfo.paid = false
    org.clear30.data.NotificationHandler.removePendingContent()
    org.clear30.data.model.ToggleSettingsOption.entries
        .filter { it.type == org.clear30.data.model.ToggleSettingsOptionType.NOTIFICATIONS }
        .forEach { userInfo.notificationSettings?.options?.set(it, false) }
    Logger.logEvent(userInfo.loggingID, LogEventType.unsubscribed)
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
