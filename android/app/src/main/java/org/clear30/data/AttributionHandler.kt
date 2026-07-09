package org.clear30.data

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.clear30.Clear30Application

/**
 * AttributionHandler — ported from AttributionHandler.swift.
 *
 * iOS used AppStack + Facebook SDK + App Tracking Transparency. Neither the
 * Facebook nor an AppStack SDK is a dependency of this Android project, so the
 * attribution sink here is **Firebase Analytics** (installed via
 * `libs.firebase.analytics`), which auto-initializes through google-services.
 *  - Facebook / AppStack -> not wired (no SDK); add the dependency + a branch
 *    here if those networks are needed.
 *  - requestTrackingAuthorization -> iOS ATT has no Android analog; no-op.
 *
 * [Logger.logEvent] forwards every product event through [trackEvent].
 */
object AttributionHandler {

    @Volatile
    private var initialized: Boolean = false

    private val analytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(Clear30Application.instance)
    }

    fun initSDK() {
        // Firebase Analytics auto-initializes via google-services; just open the gate.
        initialized = true
    }

    /** Forward an analytics event to Firebase (was trackAppStackEvent + trackFacebookEvent). */
    fun trackEvent(event: String, extraData: Map<String, String> = emptyMap()) {
        if (!initialized) return
        runCatching {
            val bundle = Bundle()
            extraData.forEach { (k, v) -> bundle.putString(sanitize(k), v) }
            analytics.logEvent(sanitize(event), bundle)
        }
    }

    fun getAppStackID(): String? = null // no AppStack SDK on Android

    fun getAttributionInfo(): Map<String, Any>? = null

    /** iOS App Tracking Transparency has no Android equivalent — no-op. */
    fun requestTrackingAuthorization() { /* no-op on Android */ }

    /**
     * Firebase event/param names must start with a letter and contain only
     * letters, digits and underscores (≤40 chars). Sanitize so arbitrary iOS
     * event names don't get silently dropped.
     */
    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_]"), "_")
        val prefixed = if (cleaned.firstOrNull()?.isLetter() == true) cleaned else "e_$cleaned"
        return prefixed.take(40)
    }
}
