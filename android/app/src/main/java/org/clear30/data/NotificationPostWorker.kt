package org.clear30.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.clear30.MainActivity
import org.clear30.R

/**
 * Fires the actual notification at the scheduled moment — `NotificationHandler`
 * enqueues an instance of this with a fire time and the per-type channel id;
 * WorkManager invokes [doWork] then.
 *
 * Channels are created by [org.clear30.Clear30Application.createNotificationChannels];
 * we pick the channel by id from the input data so the user's per-type mute in
 * system Settings actually takes effect (mirrors iOS `UNNotificationCategory`).
 */
class NotificationPostWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val channelId = inputData.getString(KEY_CHANNEL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: applicationContext.getString(R.string.app_name)
        val body = inputData.getString(KEY_BODY).orEmpty()
        val notifId = inputData.getInt(KEY_NOTIF_ID, System.currentTimeMillis().toInt())
        val deepLink = inputData.getString(KEY_DEEP_LINK)

        // Gate on user/system notification permission. Returning success (not
        // retry) — there's nothing to retry; the user denied access.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            deepLink?.let { putExtra("deep_link", it) }
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            notifId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(notifId, notification)
            Result.success()
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the gate check and post.
            Result.success()
        }
    }

    companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_NOTIF_ID = "notif_id"
        const val KEY_DEEP_LINK = "deep_link"

        // Tags so we can cancel by category — mirrors iOS `removePendingNotificationRequests(withIdentifiers:)`.
        const val TAG_ABANDONED_ONBOARDING = "tag_abandoned_onboarding"
        const val TAG_CONTENT = "tag_content"
        const val TAG_CHECK_IN = "tag_check_in"
        const val TAG_POP_IN = "tag_pop_in"
        const val TAG_ACHIEVEMENT = "tag_achievement"
    }
}
