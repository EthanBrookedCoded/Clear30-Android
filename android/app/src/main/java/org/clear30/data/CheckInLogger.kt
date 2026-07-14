package org.clear30.data

import kotlinx.coroutines.CoroutineScope
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.clear30.data.model.CheckInDefaults
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.DateSpan
import org.clear30.data.model.LoggedCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.UserInfo
import org.clear30.data.model.weedCheckIn
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.addGroupCheckInActivity
import org.clear30.data.supabase.updateDayInfo
import org.clear30.data.supabase.updateLastSmoked
import org.clear30.util.now

/**
 * CheckInLogger — ported from CheckInLogger.swift (core write path). Records the
 * day's check-ins into `Program.dayInfo`, persists locally, syncs to Supabase,
 * and refreshes the home-screen widget.
 *
 * Last-smoked recompute matches the iOS behavior: after every check-in we scan
 * `Program.dayInfo` for the most recent "smoked" entry and lift its timestamp
 * into `Program.lastSmoked`, so the home "time clear" timer always reads off
 * the user's true last use. Reward generation and the health-setback recompute
 * run here too (the day's variable reward is stamped, the health timeline is
 * pushed back per slip).
 */
class CheckInLogger(
    private val program: Program,
    private val userInfo: UserInfo,
    private val scope: CoroutineScope,
) {
    /**
     * Log [checkIns] for [date], mirroring CheckInLogger.logCheckIns.
     *
     * Returns the day's generated [VariableReward] (already stamped onto the
     * day's `variableRewardType`) so the reward screen can render exactly the
     * reward that was picked, without re-running the generator during
     * composition (PARITY §18-D1: the generator mutates `program.dayInfo`, so
     * it must run exactly once per check-in, here).
     */
    fun logCheckIns(date: Instant, checkIns: List<LoggedCheckIn>): VariableReward? {
        val plainDate = PlainDate.from(date)

        checkIns.firstNotNullOfOrNull { it.method }?.let { program.latestCheckInMethod = it }

        val dayInfo = (program.dayInfo[plainDate] ?: ProgramDayInfo()).copy(loggedCheckIns = checkIns)
        program.updateDayInfo(plainDate, dayInfo)

        handleLastSmoked()

        // Recompute the health-timeline setback so a logged slip pushes every
        // milestone back (iOS updateProgramHealthSetbackDays).
        program.updateHealthSetbackDays()

        // Reward generation: pick + stamp the day's variable reward type (iOS
        // CheckInRewardVariableGenerator.storeRewardType). The static reward is
        // generated on demand by the reward sheet (it doesn't mutate state).
        val variableReward = CheckInRewardVariableGenerator(userInfo, program).generate(plainDate)

        // Analytics: fire a single event per check-in, tagged by outcome. Use the
        // weed check-in's completion specifically (iOS `weedCheckIn?.completion`) —
        // the first slot isn't guaranteed to be the weed entry once customs exist.
        val sober = checkIns.weedCheckIn?.completion
        val event = when (sober) {
            true -> LogEventType.loggedCheckIn
            false -> LogEventType.smokedCheckIn
            null -> LogEventType.unloggedCheckIn
        }
        Logger.logEvent(
            userInfo.loggingID,
            event,
            mapOf(LogEventExtraDataType.SOBER to (sober?.toString() ?: "null")),
        )

        // Confetti celebrations fire inside the animated reward views themselves
        // (CheckInRewardViews.kt — weed-free timer / money saved pops), matching
        // the iOS per-reward ConfettiPop placement. We deliberately don't fire a
        // global popup here: it would render behind the check-in dialog and stack
        // on backdated catch-up logs.

        persist(sober)

        return variableReward
    }

    /** Convenience for the standard weed check-in (sober = true/false). */
    fun logWeedCheckIn(sober: Boolean, date: Instant = now()) {
        logCheckIns(date, listOf(LoggedCheckIn(id = CheckInDefaults.weed.id, completion = sober)))
    }

    /**
     * Recompute `program.lastSmoked` from the live dayInfo — port of the iOS
     * `handleLastSmoked` flow. The "time clear" timer reads program.lastSmoked
     * directly, so this keeps it honest after every check-in (including edits
     * that flip a day from smoked→sober, which should push the timestamp
     * forward to the next-most-recent smoked entry).
     *
     * If the user has never logged a "smoked" check-in, lastSmoked is left
     * untouched (it was seeded from onboarding's lastSmokedDate).
     */
    private fun handleLastSmoked() {
        val latestSmoked = program.dayInfo.values
            .flatMap { it.loggedCheckIns }
            .filter { it.completion == false && it.timestamp != null }
            .maxByOrNull { it.timestamp!! }
            ?.timestamp
        if (latestSmoked != null) program.lastSmoked = latestSmoked
    }

    /**
     * Back-fill a run of days with generic weed check-ins — port of the iOS
     * `handleMultiCheckIn(dateSoberPairs:)` (CheckInLogger.swift:260-306). Used
     * by the onboarding start-date step (O11) to mark past days sober. Unlike
     * [logCheckIns] it REPLACES only the weed check-in on each day (custom
     * check-ins survive) and runs no reward generation — matching iOS.
     */
    suspend fun handleMultiCheckIn(dateSoberPairs: List<Pair<PlainDate, Boolean>>) {
        for ((plainDate, sober) in dateSoberPairs) {
            val existing = program.dayInfo[plainDate] ?: ProgramDayInfo()
            val checkIns = existing.loggedCheckIns.filterNot { CheckInMethod.isWeedCheckIn(it.id) } +
                LoggedCheckIn.genericWeedCheckIn(sober)
            program.updateDayInfo(plainDate, existing.copy(loggedCheckIns = checkIns))
        }

        // Reset the last-smoked timer off the most recent smoked day: iOS maps it
        // to `now - dayDiff`, keeping the current time-of-day.
        dateSoberPairs.filter { !it.second }.maxByOrNull { it.first.dateObject }?.let { (date, _) ->
            val dayDiff = date.dateObject.daysTo(now())
            resetLastSmoked(setTo = now().adding(days = -dayDiff), program = program)
        }

        program.updateHealthSetbackDays()

        try {
            Clear30Store.save(program)
        } catch (t: Throwable) {
            android.util.Log.e("CheckInLogger", "Failed to persist program after multi check-in", t)
        }

        try {
            SupabaseController.updateDayInfo(program.dayInfo)
        } catch (t: Throwable) {
            android.util.Log.e("CheckInLogger", "Failed to sync multi check-in to Supabase", t)
        }

        NotificationHandler.scheduleHealthNotifications(userInfo, program)

        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.checkedIn,
            mapOf(
                LogEventExtraDataType.TYPE to "multi-checkin",
                LogEventExtraDataType.DATE to dateSoberPairs.joinToString(", ") { it.first.dateString },
            ),
        )
    }

    companion object {
        /**
         * Move `program.lastSmoked` to [setTo] — port of the static iOS
         * `resetLastSmoked(setTo:program:)` (CheckInLogger.swift:312-345). Pushes
         * the new value, records the elapsed sober span, refreshes the widget.
         * The CALLER is responsible for `Clear30Store.save(program)`.
         */
        suspend fun resetLastSmoked(setTo: Instant, program: Program) {
            val previous = program.lastSmoked
            program.lastSmoked = setTo

            try {
                SupabaseController.updateLastSmoked(setTo)
            } catch (t: Throwable) {
                android.util.Log.e("CheckInLogger", "Failed to sync lastSmoked reset", t)
            }

            if (previous < setTo) {
                val spans = program.lastSmokedSpans ?: mutableListOf<DateSpan>().also { program.lastSmokedSpans = it }
                spans.add(DateSpan(startDate = previous, endDate = setTo))
            }

            runCatching {
                org.clear30.widget.StatsWidget()
                    .updateAll(org.clear30.Clear30Application.instance)
            }
        }
    }

    private fun persist(sober: Boolean? = null) {
        scope.launch {
            // The whole async pipeline is defensive (PARITY §18-D1): an uncaught
            // throw from any step would kill the calling scope (the Today tab's
            // rememberCoroutineScope) and crash the app after the user already
            // saw their check-in succeed. Each step is isolated so a failure in
            // one never blocks the next.

            // 1. Persist locally first — this is the source of truth and must
            //    succeed before we attempt the network round-trip.
            try {
                Clear30Store.save(program)
            } catch (t: Throwable) {
                android.util.Log.e("CheckInLogger", "Failed to persist program after check-in", t)
            }

            // 2. Refresh the home-screen widget (iOS WidgetCenter.reloadAllTimelines).
            //    updateAll is an extension on GlanceAppWidget — needs the import
            //    above; the FQN-only form Kotlin couldn't resolve and failed the
            //    runCatching type inference.
            runCatching {
                org.clear30.widget.StatsWidget()
                    .updateAll(org.clear30.Clear30Application.instance)
            }

            // 3. Push the affected columns up to Supabase. Each write is
            //    individually try-caught inside the helpers, so a transient
            //    failure in one column doesn't roll back the others. We push
            //    only what the check-in actually mutated: day_info and
            //    last_smoked. (`latestCheckInMethod` stays device-local — there
            //    is no such column on prod `users`.) The outer catch guards
            //    against throws before/around the helpers (e.g. serialization).
            try {
                SupabaseController.updateDayInfo(program.dayInfo)
                SupabaseController.updateLastSmoked(program.lastSmoked)
            } catch (t: Throwable) {
                android.util.Log.e("CheckInLogger", "Failed to sync check-in to Supabase", t)
            }

            // 4. Evaluate achievement criteria against the new state and write
            //    any newly-earned achievements to user_achievements. Skips the
            //    network call (and the local append) when nothing was earned.
            try {
                val achievementData = Clear30Store.loadAchievementData()
                AchievementEngine.syncNewlyEarned(achievementData, userInfo, program)
            } catch (t: Throwable) {
                android.util.Log.e("CheckInLogger", "Achievement sync failed after check-in", t)
            }

            // 5. If the user is in an accountability group, mirror this check-in into
            //    the group activity feed (groups.add_checkin_activity). Best-effort.
            try {
                if (!userInfo.groupID.isNullOrEmpty()) {
                    SupabaseController.addGroupCheckInActivity(sober)
                }
            } catch (t: Throwable) {
                android.util.Log.e("CheckInLogger", "Group check-in activity sync failed", t)
            }

            // 6. Reschedule health milestone notifications — a logged slip pushes
            //    every milestone back (iOS CheckInLogger.swift:98,294).
            runCatching { NotificationHandler.scheduleHealthNotifications(userInfo, program) }
        }
    }
}
