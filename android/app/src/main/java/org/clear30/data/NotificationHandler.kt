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
     * Schedule one notification per future-dated [ProgramMessage] — the full
     * iOS `NotificationHandlerContent.scheduleContent` port (N3):
     *
     *  - Fire time is anchored to the user's **smoke time** from the break
     *    assessment ("4:20 PM"): a random minute in the hour BEFORE it on the
     *    message's unlock day (iOS `smokeTimeBasedDate`, verified against
     *    source per §17-Q20). With no smoke-time answer: unlockOn ± up to 1h
     *    (iOS `randomDate`).
     *  - Copy falls back from `notificationTitle`/`notificationBody` to the
     *    message `title`/`subtitle`, with `_CLIENTNAME_` substitution.
     *  - Within a day, assessment-response messages win: base messages only
     *    notify on days that have no assessment message (iOS pre-filter).
     *  - School messages never notify; capped at 50.
     *
     * Replaces any previously scheduled content notifications (idempotent —
     * safe to re-call after every `ProgramMessageHandler.ensureContent`).
     */
    fun scheduleContent(userInfo: UserInfo, messages: List<ProgramMessage>, program: Program) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.CONTENT)) {
            wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
            return
        }

        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)

        // Smoke time parsed once outside the loop (iOS).
        val smokeTime = program.currentBreak
            ?.getMultiAssessmentResponses(org.clear30.data.model.AssessmentQuestionID.SMOKE_TIME.raw)
            ?.firstOrNull()
            ?.let(::parseTimeString)

        val tz = TimeZone.currentSystemDefault()
        val nowMs = System.currentTimeMillis()
        val future = messages.filter { !it.isSchoolMessage && it.unlockOn.toEpochMilliseconds() > nowMs }
        val filtered = future.filter { msg ->
            val isAssessment = msg.questionID != null && msg.questionResponse != null
            isAssessment || future.none { other ->
                other !== msg && other.questionID != null && other.questionResponse != null &&
                    other.unlockOn.toLocalDateTime(tz).date == msg.unlockOn.toLocalDateTime(tz).date
            }
        }

        filtered.take(MAX_CONTENT_NOTIFICATIONS).forEach { msg ->
            val title = (msg.notificationTitle ?: msg.title).replace("_CLIENTNAME_", userInfo.name)
            val body = (msg.notificationBody ?: msg.subtitle).replace("_CLIENTNAME_", userInfo.name)
            val fireAt = smokeTimeBasedFireMs(msg.unlockOn, smokeTime, tz)
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

    /**
     * iOS `smokeTimeBasedDate`: with a smoke time, fire on the unlock DAY at a
     * random minute inside [smokeTime − 1h, smokeTime]; otherwise unlockOn plus
     * a random offset in ±1h.
     */
    private fun smokeTimeBasedFireMs(
        unlockOn: kotlinx.datetime.Instant,
        smokeTime: Pair<Int, Int>?,
        tz: TimeZone,
    ): Long {
        if (smokeTime != null) {
            val (hour, minute) = smokeTime
            if (hour in 0..23 && minute in 0..59) {
                val day = unlockOn.toLocalDateTime(tz).date
                val smokeMs = LocalTime(hour, minute).atDate(day).toInstant(tz).toEpochMilliseconds()
                return smokeMs - 3_600_000L + (0..60).random() * 60_000L
            }
        }
        return unlockOn.toEpochMilliseconds() + (-3_600_000L..3_600_000L).random()
    }

    /** iOS `parseTimeString` — "1:00 AM" / "11:00 PM" → 24h (hour, minute). */
    private fun parseTimeString(timeString: String): Pair<Int, Int>? {
        val parts = timeString.trim().split(" ")
        if (parts.size != 2) return null
        val hm = parts[0].split(":")
        if (hm.size != 2) return null
        val hour = hm[0].toIntOrNull() ?: return null
        val minute = hm[1].toIntOrNull() ?: return null
        var finalHour = hour
        when (parts[1].uppercase()) {
            "AM" -> if (hour == 12) finalHour = 0
            "PM" -> if (hour != 12) finalHour = hour + 12
        }
        return finalHour to minute
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

    // MARK: - Slipped nudge

    /**
     * Post-slip ("you smoked") nudge — iOS `NotificationHandlerSlipped.scheduleSlipped`.
     * Fires only for users currently in an active break who log a smoke, 90 min –
     * 3 hours after the slip; warm, normalizing copy pointing back into the app
     * (the Slipped support sheet). Reuses the check-in toggle (free, on by
     * default) — the slip nudge is part of the same return-to-the-app loop.
     * A stable unique-work name + notification id means logging another slip
     * simply re-times the pending nudge rather than stacking.
     */
    fun scheduleSlipped(userInfo: UserInfo, program: Program) {
        val settings = userInfo.notificationSettings ?: return
        if (!settings.typeEnabled(ToggleSettingsOption.CHECK_IN)) return

        // Only for users currently in an active break.
        if (program.currentBreak == null) return

        val (title, body) = SLIPPED_COPY.random()

        // iOS `slippedFireDate`: 90 min – 3 hours after the slip.
        val delayMinutes = (90..180).random().toLong()

        val req = OneTimeWorkRequestBuilder<NotificationPostWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(NotificationPostWorker.TAG_SLIPPED)
            .setInputData(workDataOf(
                NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_CHECK_IN,
                NotificationPostWorker.KEY_TITLE to title,
                NotificationPostWorker.KEY_BODY to body,
                NotificationPostWorker.KEY_NOTIF_ID to NOTIF_ID_SLIPPED,
                // iOS taps open the Slipped support sheet; route to the Support
                // tab (where the Slipped card lives) via the existing deep link.
                NotificationPostWorker.KEY_DEEP_LINK to "clear30://support",
            ))
            .build()
        wm().enqueueUniqueWork("slipped_nudge", ExistingWorkPolicy.REPLACE, req)
    }

    /** iOS `NotificationHandler.removePending(type: .slipped)` — a sober check-in cancels the stale nudge. */
    fun removeSlipped() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_SLIPPED)
    }

    /**
     * iOS `SlippedNotificationCopy.all` (NotificationHandlerSlipped.swift:26-67)
     * — copy is local for v1, mirroring the Slipped sheet's local copy pools.
     */
    private val SLIPPED_COPY: List<Pair<String, String>> = listOf(
        "Hey 🤍 if you're feeling frustrated, don't" to
            "You don't have to carry that right now. Come back in, we made something for you.",
        "So you used 🌱 it happens" to
            "No need to judge it. See if you can meet this moment with a little kindness. There's something inside for you.",
        "Today didn't go to plan 🫶" to
            "That's part of the path. Nothing to fix right now. Come back in, we set something aside for you.",
        "One of those days, huh 😮‍💨" to
            "Yeah, those come and go. You're still on the path. There's something in the app for moments like this.",
        "Hey, about earlier 🤍" to
            "Whatever came up, you can meet it with awareness. Come back in when you can, we've got something for you.",
        "Be gentle with yourself 🫶" to
            "Using once doesn't erase anything. This is a moment to notice, not judge. Come back in when you're ready.",
        "Hey, come back in 🤍" to
            "Whatever you're feeling is welcome here. Let's just take the next step. We put something together for you.",
        "If you're feeling off about using 🌱" to
            "That feeling will pass too with movement. You don't have to hold onto it. There's something inside for you.",
        "Be kind to yourself 🤍" to
            "Nothing is ruined. You're still learning your mind. Come back in when you can, we've got you.",
        "This is part of the process 🌱" to
            "Not a setback, just something to understand. Come back in, we set something aside for this moment.",
    )

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
     * Schedule one notification per health category at its next step's real
     * unlock instant (iOS `NotificationHandlerHealth.scheduleHealthNotifications`
     * — `nextStepDate` = category start + setback + step days + minute offset).
     * Full replace of any pending health work — idempotent; called after
     * check-ins, timeline mutations, and on tab load.
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

        val nowMs = System.currentTimeMillis()
        program.healthProgress.forEach { healthProgress ->
            val nextStep = healthProgress.nextStep ?: return@forEach
            val nextStepDate = healthProgress.nextStepDate ?: return@forEach
            val fireAt = nextStepDate.toEpochMilliseconds()
            if (fireAt <= nowMs) return@forEach

            // iOS generateHealthNotificationContent — step copy or nothing.
            val title = nextStep.notificationTitle?.replace("_CLIENTNAME_", userInfo.name) ?: return@forEach
            val body = nextStep.notificationBody?.replace("_CLIENTNAME_", userInfo.name) ?: return@forEach

            val req = OneTimeWorkRequestBuilder<NotificationPostWorker>()
                .setInitialDelay(fireAt - nowMs, TimeUnit.MILLISECONDS)
                .addTag(NotificationPostWorker.TAG_HEALTH)
                .setInputData(workDataOf(
                    NotificationPostWorker.KEY_CHANNEL to Clear30Application.CHANNEL_HEALTH,
                    NotificationPostWorker.KEY_TITLE to title,
                    NotificationPostWorker.KEY_BODY to body,
                    NotificationPostWorker.KEY_NOTIF_ID to (NOTIF_ID_HEALTH + healthProgress.category.order),
                ))
                .build()
            wm().enqueueUniqueWork("health_${healthProgress.category.name}", ExistingWorkPolicy.REPLACE, req)
        }
    }

    // MARK: - Bulk

    /** iOS `removePending(type: .content)` — drop only the scheduled content pushes
     *  (used when a subscription lapses; the other categories keep firing). */
    fun removePendingContent() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
    }

    /** iOS `UNUserNotificationCenter.removeAllPendingNotificationRequests()`. */
    fun removePending() {
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_ABANDONED_ONBOARDING)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CONTENT)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_CHECK_IN)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_POP_IN)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_HEALTH)
        wm().cancelAllWorkByTag(NotificationPostWorker.TAG_SLIPPED)
    }

    // iOS `maxNotis` — content notifications scheduled per pass.
    private const val MAX_CONTENT_NOTIFICATIONS = 50

    // Stable id buckets so we can reuse the notification slot on update.
    private const val NOTIF_ID_BASE_ABANDONED = 10_000
    private const val NOTIF_ID_CHECK_IN = 20_000
    private const val NOTIF_ID_ACHIEVEMENT = 30_000
    private const val NOTIF_ID_HEALTH = 40_000
    private const val NOTIF_ID_SLIPPED = 50_000
}
