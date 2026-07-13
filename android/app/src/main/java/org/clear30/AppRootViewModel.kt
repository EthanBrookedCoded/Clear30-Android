package org.clear30

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.Logger
import org.clear30.data.LogEventType
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.signOut
import org.clear30.data.supabase.syncProgramState
import org.clear30.data.supabase.updateFCMToken
import org.clear30.util.now
import java.util.UUID

/**
 * Root routing state — ported from `ContentView` (ContentView.swift). Decides
 * splash vs onboarding vs main tabs, and runs the `loadStorage` / `patchUserInfo`
 * startup work.
 *
 * Program / OnboardingSetup loading is added with the assessment-engine port;
 * routing currently keys off `UserInfo.completedOnboarding`, as in iOS.
 */
sealed interface AppRootState {
    data object Loading : AppRootState
    data class NewUser(val userInfo: UserInfo) : AppRootState
    data class ExistingUser(val userInfo: UserInfo) : AppRootState
}

class AppRootViewModel : ViewModel() {

    private val _state = MutableStateFlow<AppRootState>(AppRootState.Loading)
    val state: StateFlow<AppRootState> = _state.asStateFlow()

    lateinit var experimentController: ExperimentController
        private set
    lateinit var journalEntries: JournalEntries
        private set
    lateinit var program: Program
        private set
    lateinit var onboardingSetup: OnboardingSetup
        private set

    init {
        loadStorage()
        observeFcmToken()
    }

    /**
     * ContentView.updateFCM — keep UserInfo + the users row in sync with the FCM
     * registration token. Combined with the routing state so a token that arrives
     * before loadStorage finishes still lands once a UserInfo exists. Without
     * this, `create_user` pushes a null fcm_token at signup and token rotations
     * never reach the backend (pushes silently die for existing users).
     */
    private fun observeFcmToken() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_state, AppState.fcmToken) { s, t -> s to t }
                .collect { (s, token) ->
                    if (token == null) return@collect
                    val userInfo = (s as? AppRootState.ExistingUser)?.userInfo
                        ?: (s as? AppRootState.NewUser)?.userInfo
                        ?: return@collect
                    if (userInfo.fcmToken != token) {
                        userInfo.fcmToken = token
                        Clear30Store.save(userInfo)
                        // Pre-signup there's no users row/auth session yet — the token
                        // reaches the backend inside the create_user payload instead.
                        if (userInfo.userID.isNotEmpty()) {
                            SupabaseController.updateFCMToken(token)
                        }
                    }
                }
        }
    }

    /** ContentView.loadStorage — load persisted models, patch, route. */
    private fun loadStorage() {
        viewModelScope.launch {
            val userInfo = Clear30Store.loadUserInfo()
            experimentController = Clear30Store.loadExperimentController()
            journalEntries = Clear30Store.loadJournalEntries()
            program = Clear30Store.loadProgram()
            onboardingSetup = Clear30Store.loadOnboardingSetup()

            patchUserInfo(userInfo)

            val newUser = userInfo.completedOnboarding != true
            _state.value = if (newUser) AppRootState.NewUser(userInfo) else AppRootState.ExistingUser(userInfo)

            // Pull the user's experiment assignments + log exposures. Runs
            // *after* state routing so the splash doesn't wait on the network;
            // showFeature(...) calls before the response complete fall back to
            // their declared default, matching iOS `LoadingCoordinator`.
            if (userInfo.loggingID.isNotEmpty()) {
                org.clear30.data.supabase.refreshExperiments(experimentController, userInfo)
            }
        }
    }

    /** Subset of ContentView.patchUserInfo: ensure a logging id, record the session. */
    private suspend fun patchUserInfo(userInfo: UserInfo) {
        if (userInfo.loggingID.isEmpty()) {
            userInfo._loggingID = userInfo.userID.ifEmpty { UUID.randomUUID().toString() }
            Logger.logEvent(userInfo.loggingID, LogEventType.openedApp)
            // First launch: nudge the user to come back if they bail mid-onboarding.
            // Cancelled in AllNewUserViewModel.handlePayment once they finish.
            org.clear30.data.NotificationHandler.scheduleAbandonedOnboarding(userInfo, program)
        } else {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedApp)
        }
        userInfo.sessions.add(now())

        // Sync the paid entitlement from RevenueCat on load — restores paid state on
        // a reinstall / new device. Only sets when RevenueCat reports an active
        // entitlement; never clears a local freeCode/bypass on a transient miss.
        // Free-code users skip RevenueCat entirely (iOS checkEntitlementChanged).
        if (userInfo.freeCode == null) {
            org.clear30.data.PaywallController.activeEntitlement(userInfo)?.let { userInfo.currentEntitlementType = it }
        }

        Clear30Store.save(userInfo)
    }

    /** Re-route to the main app once onboarding finishes (handlePayment). */
    fun onboardingCompleted(userInfo: UserInfo) {
        _state.value = AppRootState.ExistingUser(userInfo)
        // Onboarding just produced the initial Program/UserInfo state — push it
        // up so a fresh install on a second device can recover.
        syncOnForeground()
    }

    /**
     * Foreground sync — invoked from MainActivity.onStart (mirrors iOS
     * `scenePhase == .active`). No-ops while the splash is still loading;
     * otherwise fires the column writes so a reinstall on another device
     * recovers day_info / content_info / breaks / last_smoked instead of
     * starting at zero. All writes are individually try/caught inside the
     * Supabase helpers; a network blip won't crash the foreground path.
     */
    fun syncOnForeground() {
        if (!::program.isInitialized) return
        viewModelScope.launch {
            SupabaseController.syncProgramState(program)
            // Re-pull experiment assignments so backend-side variant flips take
            // effect on the next foreground without waiting for a cold start.
            val currentUser = (_state.value as? AppRootState.ExistingUser)?.userInfo
                ?: (_state.value as? AppRootState.NewUser)?.userInfo
            if (currentUser != null && currentUser.loggingID.isNotEmpty()) {
                org.clear30.data.supabase.refreshExperiments(experimentController, currentUser)
                // Cache achievement definitions + earned so the engine
                // (CheckInLogger after every check-in) has criteria — even for
                // users who never visit the Profile tab where the lazy fetch
                // would otherwise be the only trigger.
                org.clear30.data.supabase.refreshAchievementsCache(currentUser.userID)
            }
        }
    }

    /** ContentView.signOut — wipe persisted models and reload. */
    fun signOut() {
        // Log before wiping — once the userInfo is gone, we lose the loggingID.
        val loggingId = (_state.value as? AppRootState.ExistingUser)?.userInfo?.loggingID
            ?: (_state.value as? AppRootState.NewUser)?.userInfo?.loggingID
        if (!loggingId.isNullOrEmpty()) {
            Logger.logEvent(loggingId, LogEventType.signedOut)
        }
        viewModelScope.launch {
            // Clear the Supabase Auth session FIRST — otherwise the persisted token
            // survives the wipe and the "new" account stays signed in as the old one
            // (and a stale token from a different env keeps every RLS read empty).
            org.clear30.data.supabase.SupabaseController.signOut()
            // Reset RevenueCat to an anonymous user so the next account doesn't
            // inherit this one's entitlements (iOS PaywallController.signOut).
            org.clear30.data.PaywallController.signOut()
            // iOS: NotificationHandler.removePending() — the old account's
            // schedules must not keep firing on the new session.
            org.clear30.data.NotificationHandler.removePending()
            // Drop process-scoped caches/queues that referenced the old user.
            org.clear30.data.UserDirectory.clear()
            org.clear30.data.PopupManager.clear()
            org.clear30.data.AlertHandler.dismiss()
            org.clear30.data.LoadingCoordinator.reset()
            Clear30Store.wipeAll()
            _state.value = AppRootState.Loading
            loadStorage()
        }
    }
}
