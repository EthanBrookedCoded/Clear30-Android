package org.clear30.messaging

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.clear30.AppState
import org.clear30.Clear30Application
import org.clear30.MainActivity
import org.clear30.R

/**
 * Firebase Cloud Messaging service — ported from the `MessagingDelegate` /
 * remote-notification methods on `AppDelegate` (Clear30App.swift).
 *
 * - onNewToken            <- messaging(_:didReceiveRegistrationToken:)
 * - data-only messages    <- application(_:didReceiveRemoteNotification:) (silent push)
 * - notification messages <- userNotificationCenter(_:willPresent:) banner/sound
 *
 * Why we override `onMessageReceived` instead of letting FCM auto-display: the
 * default FCM display path posts everything on the channel referenced in
 * AndroidManifest's `default_notification_channel_id` meta-data, ignoring the
 * payload's category. We build the notification ourselves so it lands on the
 * channel matching `data["category"]` (or `data["type"]`), which is what makes
 * the per-type toggles in system Settings actually work — mirroring how the
 * iOS app categorised push via [ToggleSettingsOption].
 */
class Clear30MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppState.setFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        AppState.setNotification(message.data)
        // Log inbound pushes when the payload carries a user_id (the backend
        // populates this from its push-send call). We don't log unsigned
        // pushes — the loggingID isn't reachable from this background process
        // and an empty user_id row is wasted on the events table.
        val pushUser = message.data["user_id"].orEmpty()
        val category = message.data["category"] ?: message.data["type"]
        if (pushUser.isNotEmpty() && category != null) {
            org.clear30.data.Logger.logEvent(
                loggingID = pushUser,
                event = org.clear30.data.LogEventType.pushReceived,
                extraData = mapOf(org.clear30.data.LogEventExtraDataType.TYPE to category),
            )
        }

        val notification = message.notification ?: return  // silent / data-only push
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val channelId = channelForCategory(message.data["category"] ?: message.data["type"])
        val title = notification.title ?: getString(R.string.app_name)
        val body = notification.body.orEmpty()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Surface the payload to AppRoot for in-app routing on tap.
            message.data["url"]?.let { putExtra("deep_link", it) }
        }
        val pending = PendingIntent.getActivity(
            this, message.messageId?.hashCode() ?: 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)

        try {
            NotificationManagerCompat.from(this)
                .notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted on API 33+. Nothing to do —
            // we already short-circuited via areNotificationsEnabled() above for
            // most cases; this is the catch for the racing-permission edge case.
        }
    }

    /** Map an FCM `category`/`type` data field to one of the per-type channels. */
    private fun channelForCategory(category: String?): String = when (category?.lowercase()) {
        "content", "messages", "daily_content" -> Clear30Application.CHANNEL_CONTENT
        "check_in", "checkin", "check-in" -> Clear30Application.CHANNEL_CHECK_IN
        "pop_in", "popin", "progress" -> Clear30Application.CHANNEL_POP_IN
        "community" -> Clear30Application.CHANNEL_COMMUNITY
        "group", "groups" -> Clear30Application.CHANNEL_GROUP
        "health" -> Clear30Application.CHANNEL_HEALTH
        "achievement", "achievements" -> Clear30Application.CHANNEL_ACHIEVEMENT
        "accountability", "sms" -> Clear30Application.CHANNEL_ACCOUNTABILITY
        else -> getString(R.string.default_notification_channel_id)
    }
}
