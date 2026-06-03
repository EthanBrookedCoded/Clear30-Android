package org.clear30.data

/**
 * AttributionHandler — ported from AttributionHandler.swift.
 *
 * iOS used AppStack (no Android SDK) + Facebook SDK + App Tracking Transparency
 * (iOS-only). On Android:
 *  - Facebook events -> com.facebook.android:facebook-core (AppEventsLogger)
 *  - AppStack -> no Android SDK; substitute (AppsFlyer/Firebase) decided later
 *  - requestATT -> not applicable; Android uses the system ad-id + consent
 *
 * Structure is ported now; the actual SDK wiring is added when the analytics
 * dependencies are introduced (the Logger's attribution TODO points here).
 */
object AttributionHandler {

    @Volatile
    private var initialized: Boolean = false

    fun initSDK() {
        // TODO(port): initFacebookSDK() via AppEventsLogger.activateApp(app)
        //   + AppStack replacement init.
        initialized = true
    }

    /** Forward an analytics event (was trackAppStackEvent + trackFacebookEvent). */
    fun trackEvent(event: String, extraData: Map<String, String> = emptyMap()) {
        if (!initialized) return
        // TODO(port): AppEventsLogger(context).logEvent(event, bundleOf(extraData))
    }

    fun getAppStackID(): String? = null // TODO(port): AppStack replacement id

    fun getAttributionInfo(): Map<String, Any>? = null

    /** iOS App Tracking Transparency has no Android equivalent — no-op. */
    fun requestTrackingAuthorization() { /* no-op on Android */ }
}
