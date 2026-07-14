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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.atDate
import kotlinx.datetime.plus
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
     * `ProgramMessageHandler.ensureContent`).
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
            else LocalTime(hour, 0).atDate(now.date.plus(DatePeriod(days = 1)))
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

    // MARK: - Achievement notifications

    /**
     * Post an immediate "you just earned X" notification. Fired by
     * [org.clear30.data.AchievementEngine] when the user crosses an achievement
     * threshold from a check-in. The notification routes through the
     * `achievement` channel so users can silence it independently from the
     * other categories. Short-circuits when notifications (or this category)
     * are muted in [UserInfo.notificationSettings].
     */
    fun postAchievementEarned(userInfo: UserInfo, achievementKey: String, achievementName: String) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.ACHIEVEMENT)) return

        val req = androidx.work.OneTimeWorkRequestBuilder<NotificationPostWorker>()
            .addTag(NotificationPostWorker.TAG_ACHIEVEMENT)
            .setInputData(workDataOf(
                NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_ACHIEVEMENT,
                NotificationPostWorker.KEY_TITLE to "Achievement earned 🏆",
                NotificationPostWorker.KEY_BODY to achievementName,
                NotificationPostWorker.KEY_NOTIF_ID to (NOTIF_ID_ACHIEVEMENT + achievementKey.hashCode()),
            ))
            .build()
        wm().enqueue(req)
    }

    // MARK: - Health milestones

    /**
     * Schedule one notification per health category at its next milestone
     * unlock (iOS `NotificationHandlerHealth.scheduleHealthNotifications`).
     * Full replace of any pending health work — idempotent; called after
     * check-ins, timeline mutations, and on tab load. Fire time = the
     * milestone's unlock day at 10:00 local (iOS uses the per-category start
     * time + minute_offset — this anchors on the content-unlock convention
     * instead; WorkManager is inexact under Doze anyway, day granularity is
     * the contract).
     */
    fun scheduleHealthNotifications(userInfo: UserInfo, program: Program) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.HEALTH)) {
            wm().cancelAllWorkByTag(NotificationPostWorker.TAG_HEALTH)
            return
        }
        // Health UI isn't visible on/before Day 0 of a break (iOS guard).
        program.currentBreakNotStartSoon?.let { if (it.currentBreakDay <= 0) return }

        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_HEALTH)

        val currentDay = program.currentDay
        val tz = TimeZone.currentSystemDefault()
        val nowMs = System.currentTimeMillis()

        program.healthProgress
            .filter { it.unlockedOnDay + it.setbackDays > currentDay }
            .groupBy { it.category }
            .forEach { (category, milestones) ->
                val next = milestones.minByOrNull { it.unlockedOnDay + it.setbackDays } ?: return@forEach
                val title = next.notificationTitle?.replace("_CLIENTNAME_", userInfo.name) ?: return@forEach
                val body = next.notificationBody?.replace("_CLIENTNAME_", userInfo.name) ?: return@forEach

                // Unlock day at 10:00 local.
                val daysAhead = (next.unlockedOnDay + next.setbackDays) - currentDay
                val fireLocal = LocalTime(10, 0).atDate(
                    Clock.System.now().toLocalDateTime(tz).date.plus(DatePeriod(days = daysAhead)),
                )
                val fireAt = fireLocal.toInstant(tz).toEpochMilliseconds()
                if (fireAt <= nowMs) return@forEach

                val req = OneTimeWorkRequestBuilder<NotificationPostWorker>()
                    .setInitialDelay(fireAt - nowMs, TimeUnit.MILLISECONDS)
                    .addTag(NotificationPostWorker.TAG_HEALTH)
                    .setInputData(workDataOf(
                        NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_HEALTH,
                        NotificationPostWorker.KEY_TITLE to title,
                        NotificationPostWorker.KEY_BODY to body,
                        NotificationPostWorker.KEY_NOTIF_ID to (NOTIF_ID_HEALTH + category.ordinal),
                    ))
                    .build()
                wm().enqueueUniqueWork("health_${category.rawValue}", ExistingWorkPolicy.REPLACE, req)
            }
    }

    // MARK: - Bulk

    /** iOS `UNUserNotificationCenter.removeAllPendingNotificationRequests()`. */
    fun removePending() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_ABANDONED_ONBOARDING)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CHECK_IN)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_POP_IN)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_HEALTH)
    }

    // Stable id buckets so we can reuse the notification slot on update.
    private const val NOTIF_ID_BASE_ABANDONED = 10_000
    private const val NOTIF_ID_CHECK_IN = 20_000
    private const val NOTIF_ID_ACHIEVEMENT = 30_000
    private const val NOTIF_ID_HEALTH = 40_000
}
