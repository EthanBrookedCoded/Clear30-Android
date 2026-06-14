package org.clear30.data

import kotlinx.coroutines.CoroutineScope
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.clear30.data.model.CheckInDefaults
import org.clear30.data.model.LoggedCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.updateDayInfo
import org.clear30.data.supabase.updateLastSmoked
import org.clear30.data.supabase.updateLatestCheckInMethod
import org.clear30.util.adding
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
 * remain TODO (deeper subsystems — see android/TODO.md).
 */
class CheckInLogger(
    private val program: Program,
    private val userInfo: UserInfo,
    private val scope: CoroutineScope,
) {
    /** Log [checkIns] for [date], mirroring CheckInLogger.logCheckIns. */
    fun logCheckIns(date: Instant, checkIns: List<LoggedCheckIn>) {
        val plainDate = PlainDate.from(date)

        checkIns.firstNotNullOfOrNull { it.method }?.let { program.latestCheckInMethod = it }

        val dayInfo = (program.dayInfo[plainDate] ?: ProgramDayInfo()).copy(loggedCheckIns = checkIns)
        program.updateDayInfo(plainDate, dayInfo)

        handleLastSmoked()
        // TODO(port): updateProgramHealthSetbackDays + reward generation
        //   (CheckInRewardVariableGenerator / health-setback module — large; see TODO.md).

        // Analytics: fire a single event per check-in, tagged by outcome.
        val sober = checkIns.firstOrNull()?.completion
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

        // Celebrate streak milestones with a confetti popup. We sniff the
        // current sober streak *after* the dayInfo write so a flip from
        // "not logged" → "sober" picks up the new total immediately. The
        // AchievementEngine fires its own confetti for the underlying
        // achievement; PopupManager's coalescing window prevents stacking.
        if (sober == true) maybeCelebrateStreak()

        persist()
    }

    /** Streak day numbers that trigger a celebration overlay. */
    private val celebrationMilestones = setOf(1, 7, 14, 30, 60, 90, 100, 180, 365)

    private fun maybeCelebrateStreak() {
        val streak = currentSoberStreak()
        if (streak !in celebrationMilestones) return
        val label = when (streak) {
            1 -> "Day 1 in the books"
            7 -> "1 week clear"
            14 -> "2 weeks clear"
            30 -> "30 days clear"
            60 -> "2 months clear"
            90 -> "3 months clear"
            100 -> "100 days clear"
            180 -> "6 months clear"
            365 -> "1 year clear"
            else -> "$streak days clear"
        }
        PopupManager.request(PopupManager.Payload.Confetti(message = label))
    }

    /** Local copy of the streak calc — kept private so we don't depend on Profile UI. */
    private fun currentSoberStreak(): Int {
        var streak = 0
        var date = now()
        if (program.dayInfo[PlainDate.from(date)]?.sober != true) {
            date = date.adding(days = -1)
        }
        while (program.dayInfo[PlainDate.from(date)]?.sober == true) {
            streak++
            date = date.adding(days = -1)
        }
        return streak
    }

    /** Convenience for the standard weed check-in (sober = true/false). */
    fun logWeedCheckIn(sober: Boolean, date: Instant = now()) =
        logCheckIns(date, listOf(LoggedCheckIn(id = CheckInDefaults.weed.id, completion = sober)))

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

    private fun persist() {
        scope.launch {
            // 1. Persist locally first — this is the source of truth and must
            //    succeed before we attempt the network round-trip.
            Clear30Store.save(program)

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
            //    only what the check-in actually mutated: day_info,
            //    last_smoked, and (when applicable) the latest check-in method.
            SupabaseController.updateDayInfo(program.dayInfo)
            SupabaseController.updateLastSmoked(program.lastSmoked)
            program.latestCheckInMethod?.let { SupabaseController.updateLatestCheckInMethod(it) }

            // 4. Evaluate achievement criteria against the new state and write
            //    any newly-earned achievements to user_achievements. Skips the
            //    network call (and the local append) when nothing was earned.
            val achievementData = Clear30Store.loadAchievementData()
            AchievementEngine.syncNewlyEarned(achievementData, userInfo, program)
        }
    }
}
