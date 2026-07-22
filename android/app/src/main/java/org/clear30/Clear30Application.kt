package org.clear30

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.clear30.data.LocalStore
import org.clear30.data.PaywallController
import org.clear30.views.theme.Haptics

/**
 * Application entry point — ported from `AppDelegate.application(_:didFinishLaunchingWithOptions:)`
 * in Clear30App.swift.
 *
 * iOS did this work in the AppDelegate; Android does SDK bootstrapping in
 * Application.onCreate (runs before any Activity/Composable).
 */
class Clear30Application : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()
        Haptics.init(this)
        LocalStore.init(this)

        // FirebaseApp auto-initialises via the google-services plugin + manifest.
        // FirebaseApp.configure() is implicit on Android once google-services.json is present.

        // RevenueCat (Swift AppDelegate.didFinishLaunching -> PaywallController.initRevenueCat)
        PaywallController.initRevenueCat(this)

        // Helium paywall SDK — after RevenueCat (its RC bridge relies on the
        // configured Purchases instance). No-op until HELIUM_API_KEY is set.
        PaywallController.initHeliumSDK(this)

        // Audio focus / attributes baseline (iOS AVAudioSession setCategory:.playback
        // .mixWithOthers). ExoPlayer uses these as defaults when constructed via
        // [AudioBaseline.attributes]; the Meditations player honors them.
        org.clear30.data.AudioBaseline.init(this)

        // Crashlytics: disable collection in DEBUG so test crashes don't pollute the
        // dashboard, on by default in release. Wrapped in runCatching so the app
        // still launches before google-services.json is supplied.
        runCatching {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                .isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }

        // FCM registration token: Clear30MessagingService.onNewToken only fires
        // when the token is (re)created, so fetch the current one every launch —
        // mirrors iOS's MessagingDelegate delivering the token on each start.
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    AppState.setFcmToken(token)
                    if (BuildConfig.DEBUG) android.util.Log.i("Clear30FCM", "FCM token: $token")
                }
        }
    }

    /**
     * Notification channels per type — mirrors the iOS `ToggleSettingsOption`
     * notification categories so users can mute/categorize each kind from system
     * settings. The default channel stays as the fallback target for FCM.
     *
     * IMPORTANT: a channel's importance can only be **lowered** by the user
     * after first creation — the app cannot raise it. So we register each
     * channel at the importance we'd want long-term, and if a future change
     * needs a different importance we bump the channel id (e.g. `..._v2`) so a
     * fresh channel is created. Don't simply edit IMPORTANCE_* below on a
     * shipped channel id; upgraded users would keep the original importance.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            // DEFAULT (not HIGH) for the fallback channel: most pushes are
            // informational, and importance is sticky on upgrades.
            NotificationChannel(getString(R.string.default_notification_channel_id), "Clear30", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_CONTENT, "Daily content", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your daily program message"
            },
            NotificationChannel(CHANNEL_CHECK_IN, "Check-ins", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminder to log your daily check-in"
            },
            NotificationChannel(CHANNEL_POP_IN, "Progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Streak, money saved, and other progress nudges"
            },
            NotificationChannel(CHANNEL_COMMUNITY, "Community", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Replies, reactions and prompts from the community"
            },
            NotificationChannel(CHANNEL_GROUP, "Groups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Activity from your accountability group"
            },
            NotificationChannel(CHANNEL_HEALTH, "Health milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Health timeline updates"
            },
            NotificationChannel(CHANNEL_ACHIEVEMENT, "Achievements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Earned achievements"
            },
            NotificationChannel(CHANNEL_ACCOUNTABILITY, "Accountability", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Accountability texts you've enabled"
            },
        )
        channels.forEach(manager::createNotificationChannel)
    }

    companion object {
        lateinit var instance: Clear30Application
            private set

        /**
         * App-lifetime scope for one-shot model mutations whose UI trigger can
         * leave composition before the work completes (e.g. ending a break
         * removes the break-options section mid-flight). Main-dispatched with a
         * SupervisorJob so one failed mutation doesn't kill the scope.
         */
        val appScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main,
        )

        const val CHANNEL_CONTENT = "content"
        const val CHANNEL_CHECK_IN = "check_in"
        const val CHANNEL_POP_IN = "pop_in"
        const val CHANNEL_COMMUNITY = "community"
        const val CHANNEL_GROUP = "group"
        const val CHANNEL_HEALTH = "health"
        const val CHANNEL_ACHIEVEMENT = "achievement"
        const val CHANNEL_ACCOUNTABILITY = "accountability"
    }
}
