package org.clear30

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide transient state, ported from `AppDelegate` (Clear30App.swift).
 *
 * The iOS AppDelegate published these via `@Published`; on Android we expose
 * them as [StateFlow]s on a process-scoped singleton that the root composable
 * collects. FCM token, pending deep links, sign-out / loading triggers and the
 * last received notification all flow through here.
 */
object AppState : ViewModel() {

    private val _loadingTrigger = MutableStateFlow(false)
    val loadingTrigger: StateFlow<Boolean> = _loadingTrigger.asStateFlow()

    private val _signOutTrigger = MutableStateFlow(false)
    val signOutTrigger: StateFlow<Boolean> = _signOutTrigger.asStateFlow()

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _pendingUrl = MutableStateFlow<Uri?>(null)
    val pendingUrl: StateFlow<Uri?> = _pendingUrl.asStateFlow()

    private val _notification = MutableStateFlow<Map<String, String>?>(null)
    val notification: StateFlow<Map<String, String>?> = _notification.asStateFlow()

    /**
     * Deep-link tab request — a one-shot signal that AllTabs observes and acks
     * by setting back to null. Set by URLManager-dispatched routes that need to
     * change the selected tab without prop-drilling through AppRoot.
     *
     * The string is the [org.clear30.views.existinguser.CustomTabBarItem.name]
     * (uppercase enum name) so we don't have to import the enum here.
     */
    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab.asStateFlow()

    /**
     * Pending sub-route for the destination tab — e.g. a deep link to a post
     * detail, a group join code, or a Claire/Dr-Fred chat. The tab that owns
     * the route observes [pendingSubRoute], drives its local navigation to
     * the target screen, and acks by setting it back to null. Kept as a
     * shared signal (rather than a per-tab one) because at most one deep
     * link is in flight at a time.
     */
    private val _pendingSubRoute = MutableStateFlow<org.clear30.data.DeepLinkRoute?>(null)
    val pendingSubRoute: StateFlow<org.clear30.data.DeepLinkRoute?> = _pendingSubRoute.asStateFlow()

    /**
     * Popup-paywall request (iOS `viewModel.activeSheet = .payment(hard:)`).
     * AllTabs observes it, presents the paywall, and acks with null. `true`
     * forces the hard (undismissable) variant; `false` presents dismissable.
     * Set by deep links / locked-content upsells that can't reach AllTabs state.
     */
    private val _paywallRequest = MutableStateFlow<Boolean?>(null)
    val paywallRequest: StateFlow<Boolean?> = _paywallRequest.asStateFlow()

    /**
     * Foreground/background transitions (iOS `scenePhase`), driven by
     * MainActivity's lifecycle. Starts true so the launch transition doesn't
     * double-fire the foreground work `patchUserInfo` already does.
     */
    private val _foregrounded = MutableStateFlow(true)
    val foregrounded: StateFlow<Boolean> = _foregrounded.asStateFlow()

    fun setLoading(value: Boolean) { _loadingTrigger.value = value }
    fun setForegrounded(value: Boolean) { _foregrounded.value = value }
    fun triggerSignOut() { _signOutTrigger.value = true }
    fun clearSignOut() { _signOutTrigger.value = false }
    fun setFcmToken(token: String?) { _fcmToken.value = token }
    fun setPendingUrl(uri: Uri?) { _pendingUrl.value = uri }
    fun setNotification(data: Map<String, String>?) { _notification.value = data }
    fun requestTab(tabName: String?) { _requestedTab.value = tabName }
    fun requestSubRoute(route: org.clear30.data.DeepLinkRoute?) { _pendingSubRoute.value = route }
    fun requestPaywall(hard: Boolean?) { _paywallRequest.value = hard }
}
