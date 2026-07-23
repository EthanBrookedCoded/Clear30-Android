package org.clear30

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.clear30.data.getMessages
import org.clear30.data.supabase.checkCode
import org.clear30.data.supabase.getSchoolData
import org.clear30.views.newuser.AllNewUser
import org.clear30.views.existinguser.AllTabs
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients

/**
 * Root composable — ported from `ContentView` (ContentView.swift).
 *
 * Loads persisted state via [AppRootViewModel] then routes:
 *   Loading      -> SplashScreen
 *   NewUser      -> onboarding (AllNewUser / slideshows) — onboarding segment
 *   ExistingUser -> main tabs (AllTabs) — tabs segment
 *
 * Routing, foreground sync, deep-link dispatch, and the global overlay layer are
 * all live; per-screen depth grows batch by batch.
 */
@Composable
fun AppRoot(viewModel: AppRootViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // iOS `scenePhase == .active` → Android Lifecycle.Event.ON_START. Push the
    // local program state up to Supabase on every foreground so a cross-device
    // reinstall recovers the user's check-in history instead of resetting.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.syncOnForeground() }

    // Drain pendingUrl into a URLManager dispatch. Tab routes flip the selected
    // tab via AppState.requestedTab (which AllTabs observes); richer routes
    // currently fire a deep_link_received analytic so funnel work can attribute
    // them — the screen-level pickup grows as each screen needs it.
    val pendingUrl by AppState.pendingUrl.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingUrl) {
        val uri = pendingUrl ?: return@LaunchedEffect
        val route = org.clear30.data.URLManager.parse(uri)
        // Attribution: log every inbound deep link with the raw URI so funnel
        // work can attribute installs/sessions to the source.
        val userId = (state as? AppRootState.ExistingUser)?.userInfo?.loggingID
            ?: (state as? AppRootState.NewUser)?.userInfo?.loggingID
        if (!userId.isNullOrEmpty()) {
            org.clear30.data.Logger.logEvent(
                userId,
                org.clear30.data.LogEventType.deepLinkReceived,
                mapOf(org.clear30.data.LogEventExtraDataType.URL to uri.toString()),
            )
        }
        // Flip the tab the route belongs to.
        when (route) {
            is org.clear30.data.DeepLinkRoute.Today -> AppState.requestTab("TODAY")
            is org.clear30.data.DeepLinkRoute.Profile -> AppState.requestTab("PROFILE")
            is org.clear30.data.DeepLinkRoute.Community,
            is org.clear30.data.DeepLinkRoute.Post -> AppState.requestTab("COMMUNITY")
            is org.clear30.data.DeepLinkRoute.Groups,
            is org.clear30.data.DeepLinkRoute.Group -> AppState.requestTab("GROUPS")
            is org.clear30.data.DeepLinkRoute.Support,
            is org.clear30.data.DeepLinkRoute.Meditation,
            is org.clear30.data.DeepLinkRoute.Messages,
            is org.clear30.data.DeepLinkRoute.Claire,
            is org.clear30.data.DeepLinkRoute.DrFred -> AppState.requestTab("SUPPORT")
            is org.clear30.data.DeepLinkRoute.Settings -> AppState.requestTab("PROFILE")
            is org.clear30.data.DeepLinkRoute.School -> {
                // School unlock (iOS URLManager.swift:52-72): fetch the bundle,
                // gift the app (freeCode), hydrate school mode, and regenerate
                // the feed's school messages so no relaunch is needed. Setting
                // schoolId immediately is a deliberate (safe) deviation — iOS
                // leaves it to the next-launch back-fill.
                val userInfo = (state as? AppRootState.ExistingUser)?.userInfo
                    ?: (state as? AppRootState.NewUser)?.userInfo
                if (userInfo != null) {
                    val schoolData = org.clear30.data.supabase.SupabaseController.getSchoolData(route.schoolID)
                    if (schoolData != null) {
                        userInfo.freeCode = route.schoolID
                        userInfo.schoolData = schoolData
                        userInfo.schoolId = schoolData.school_id
                        viewModel.program.schoolMessages =
                            schoolData.getMessages(viewModel.program, userInfo).toMutableList()
                        org.clear30.data.AlertHandler.info(
                            "${schoolData.short_name} has unlocked Clear30 for you!",
                            "Enjoy full access along with content from ${schoolData.long_name}.",
                        )
                        org.clear30.data.Clear30Store.save(userInfo)
                        if (!userId.isNullOrEmpty()) {
                            org.clear30.data.Logger.logEvent(
                                userId,
                                org.clear30.data.LogEventType.openedFromLink,
                                mapOf(
                                    org.clear30.data.LogEventExtraDataType.TITLE to "school",
                                    org.clear30.data.LogEventExtraDataType.TYPE to route.schoolID,
                                ),
                            )
                        }
                    }
                }
            }
            is org.clear30.data.DeepLinkRoute.Referral -> {
                // Free-unlock promo link (iOS URLManager.swift:108-117 →
                // ReferralCodeHandler.handleURL): only for users not already
                // free, validate against payment.promo_codes via
                // `payment_check_code` and gift the app on success. The
                // referral_codes lookup + group join belongs to the manual
                // onboarding entry (ReferralSlide), not this link.
                val userInfo = (state as? AppRootState.ExistingUser)?.userInfo
                    ?: (state as? AppRootState.NewUser)?.userInfo
                if (userInfo != null && userInfo.freeCode == null) {
                    if (org.clear30.data.supabase.SupabaseController.checkCode(route.code)) {
                        userInfo.freeCode = route.code
                        org.clear30.data.Clear30Store.save(userInfo)
                        org.clear30.data.AlertHandler.info(
                            "Success",
                            "You've unlocked Clear30 for free!\nNote: this does not cancel your subscription automatically.",
                        )
                    } else {
                        org.clear30.data.AlertHandler.info("Error", "Referral code is not active.")
                    }
                    if (!userId.isNullOrEmpty()) {
                        org.clear30.data.Logger.logEvent(
                            userId,
                            org.clear30.data.LogEventType.openedFromLink,
                            mapOf(org.clear30.data.LogEventExtraDataType.TITLE to "referral"),
                        )
                    }
                }
            }
            is org.clear30.data.DeepLinkRoute.Breakdown -> AppState.requestTab("TODAY")
            is org.clear30.data.DeepLinkRoute.Unrecognized -> Unit
        }
        // Stash the payload-bearing routes so the destination tab can drive
        // its local nav (open the post, join the group, start the chat). Tab
        // routes with no payload don't need a sub-route hand-off.
        when (route) {
            is org.clear30.data.DeepLinkRoute.Post,
            is org.clear30.data.DeepLinkRoute.Group,
            is org.clear30.data.DeepLinkRoute.Meditation,
            is org.clear30.data.DeepLinkRoute.Messages,
            is org.clear30.data.DeepLinkRoute.Claire,
            is org.clear30.data.DeepLinkRoute.DrFred,
            is org.clear30.data.DeepLinkRoute.Breakdown -> AppState.requestSubRoute(route)
            else -> Unit
        }
        // Ack so we don't re-dispatch on every recomposition.
        AppState.setPendingUrl(null)
    }

    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Crossfade(targetState = state, label = "appRoot") { s ->
            when (s) {
                is AppRootState.Loading -> org.clear30.views.newuser.SplashScreen()
                is AppRootState.NewUser -> AllNewUser(
                    userInfo = s.userInfo,
                    program = viewModel.program,
                    onboardingSetup = viewModel.onboardingSetup,
                    experimentController = viewModel.experimentController,
                    scope = scope,
                    onCompletedOnboarding = { viewModel.onboardingCompleted(s.userInfo) },
                )
                is AppRootState.ExistingUser -> {
                    // Main app — light backgrounds, so flip status bar icons
                    // back to dark (they were white during the onboarding
                    // gradient screens).
                    org.clear30.views.components.StatusBarStyle(forceLightIcons = false)
                    AllTabs(
                        userInfo = s.userInfo,
                        program = viewModel.program,
                        journalEntries = viewModel.journalEntries,
                        experimentController = viewModel.experimentController,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
        }
        // Single-source overlay layer above the routed content: master alert
        // (AlertHandler), the popup queue (PopupManager), and the global
        // loading scrim (LoadingCoordinator). Each is opt-in — when its flow
        // head is null it draws nothing and the routed screen keeps input.
        org.clear30.views.components.AppOverlays()
    }
}

