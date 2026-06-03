package org.clear30.data

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.clear30.Clear30Application
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ToggleSettingsOption
import org.clear30.data.model.UserInfo
import java.util.concurrent.TimeUnit

/**
 * Local notification scheduling — ported from iOS `NotificationHandler`.
 *
 * iOS scheduled `UNNotificationRequest`s via `UNUserNotificationCenter`; Android
 * uses WorkManager OneTimeWorkRequests (and a daily PeriodicWorkRequest for the
 * check-in reminder), each carrying the per-type channel id in input data so
 * `NotificationPostWorker` posts on the channel the user actually toggled.
 *
 * The settings gate matches iOS exactly: every public scheduler reads
 * `userInfo.notificationSettings.typeEnabled(...)` and returns early if the
 * user has muted that category at the app level, on top of the system-level
 * mute the worker checks at fire time.
 */
object NotificationHandler {

    private fun wm() = WorkManager.getInstance(Clear30Application.instance)

    // MARK: - Abandoned onboarding

    /**
     * Nudge the user to come back if they bail mid-onboarding. iOS scheduled
     * 1h, 24h, 72h "come back" pushes from first launch — mirrored here.
     * Called once on first-launch from [org.clear30.AppRootViewModel.patchUserInfo].
     */
    fun scheduleAbandonedOnboarding(userInfo: UserInfo, program: Program) {
        val schedule = listOf(
            Triple(1L, TimeUnit.HOURS, "Pick up where you left off 🍃" to "Your Clear30 setup is waiting."),
            Triple(24L, TimeUnit.HOURS, "Ready to start your reset?" to "A fresh start is one tap away."),
            Triple(72L, TimeUnit.HOURS, "We're still here for you" to "Take two minutes to finish setting up."),
        )
        schedule.forEachIndexed { i, (delay, unit, copy) ->
            val (title, body) = copy
            val req = OneTimeWorkRequestBuilder<NotificationPostWorker>()
                .setInitialDelay(delay, unit)
                .addTag(NotificationPostWorker.TAG_ABANDONED_ONBOARDING)
                .setInputData(workDataOf(
                    NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_CHECK_IN,
                    NotificationPostWorker.KEY_TITLE to title,
                    NotificationPostWorker.KEY_BODY to body,
                    NotificationPostWorker.KEY_NOTIF_ID to (NOTIF_ID_BASE_ABANDONED + i),
                ))
                .build()
            wm().enqueueUniqueWork("abandoned_onboarding_$i", ExistingWorkPolicy.REPLACE, req)
        }
    }

    /** Called from [org.clear30.views.newuser.AllNewUserViewModel.handlePayment] when onboarding finishes. */
    fun removeAbandonedOnboarding() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_ABANDONED_ONBOARDING)
    }

    // MARK: - Daily content

    /**
     * Schedule one notification per future-dated [ProgramMessage] that carries
     * a `notificationTitle` + `notificationBody`. Replaces any previously
     * scheduled content notifications (idempotent — safe to re-call after every
     * `ProgramMessageHandler.fetchAndApply`).
     */
    fun scheduleContent(userInfo: UserInfo, messages: List<ProgramMessage>) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.CONTENT)) {
            wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
            return
        }

        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)

        val nowMs = System.currentTimeMillis()
        messages.forEach { msg ->
            val title = msg.notificationTitle ?: return@forEach
            val body = msg.notificationBody ?: return@forEach
            val fireAt = msg.unlockOn.toEpochMilliseconds()
            if (fireAt <= nowMs) return@forEach
            val notifId = msg.messageID ?: msg.title.hashCode()

            val req = OneTimeWorkRequestBuilder<NotificationPostWorker>()
                .setInitialDelay(fireAt - nowMs, TimeUnit.MILLISECONDS)
                .addTag(NotificationPostWorker.TAG_CONTENT)
                .setInputData(workDataOf(
                    NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_CONTENT,
                    NotificationPostWorker.KEY_TITLE to title,
                    NotificationPostWorker.KEY_BODY to body,
                    NotificationPostWorker.KEY_NOTIF_ID to notifId,
                ))
                .build()
            wm().enqueueUniqueWork("content_$notifId", ExistingWorkPolicy.REPLACE, req)
        }
    }

    // MARK: - Check-in reminder

    /**
     * Daily "remember to check in" at `hour:00` local. iOS used an exact
     * UNCalendarNotificationTrigger; Android uses a PeriodicWorkRequest (24h)
     * with initialDelay to the next occurrence — minute-level fidelity is fine
     * for a daily reminder and is far simpler than the AlarmManager dance.
     */
    fun scheduleCheckInReminder(userInfo: UserInfo) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.CHECK_IN)) {
            wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CHECK_IN)
            return
        }
        val hour = userInfo.checkInNotificationHour ?: 20  // default 8pm, matches iOS default

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val targetToday = LocalTime(hour, 0).atDate(now.date)
        val nextTarget: LocalDateTime =
            if (targetToday > now) targetToday
            else LocalTime(hour, 0).atDate(now.date.plus(1, kotlinx.datetime.DateTimeUnit.DAY))
        val initialDelayMs = nextTarget.toInstant(tz).toEpochMilliseconds() - System.currentTimeMillis()

        val req = PeriodicWorkRequestBuilder<NotificationPostWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(NotificationPostWorker.TAG_CHECK_IN)
            .setInputData(workDataOf(
                NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_CHECK_IN,
                NotificationPostWorker.KEY_TITLE to "Time to check in 🍃",
                NotificationPostWorker.KEY_BODY to "How did today go? Tap to log your check-in.",
                NotificationPostWorker.KEY_NOTIF_ID to NOTIF_ID_CHECK_IN,
            ))
            .build()
        wm().enqueueUniquePeriodicWork("check_in_reminder", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun removeCheckInReminder() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CHECK_IN)
    }

    // MARK: - Bulk

    /** iOS `UNUserNotificationCenter.removeAllPendingNotificationRequests()`. */
    fun removePending() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_ABANDONED_ONBOARDING)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CHECK_IN)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_POP_IN)
    }

    // Stable id buckets so we can reuse the notification slot on update.
    private const val NOTIF_ID_BASE_ABANDONED = 10_000
    private const val NOTIF_ID_CHECK_IN = 20_000
}
