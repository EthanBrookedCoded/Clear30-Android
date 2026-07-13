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
import org.clear30.data.model.weedCheckIn
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
    /** Log [checkIns] for [date], mirroring CheckInLogger.logCheckIns. */
    fun logCheckIns(date: Instant, checkIns: List<LoggedCheckIn>) {
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
        // generated on demand by the reward sheet.
        CheckInRewardVariableGenerator(userInfo, program).generate(plainDate)

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

        // The confetti celebration is rendered inline on the reward screen
        // (CheckInSheet → CheckInRewardContent) for a sober check-in — that's the
        // iOS `ConfettiCheckIn` placement, and it shows reliably inside the
        // check-in dialog window. We deliberately don't fire a global popup here:
        // it would render behind the dialog and stack on backdated catch-up logs.

        persist(sober)
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

    private fun persist(sober: Boolean? = null) {
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
            //    only what the check-in actually mutated: day_info and
            //    last_smoked. (`latestCheckInMethod` stays device-local — there
            //    is no such column on prod `users`.)
            SupabaseController.updateDayInfo(program.dayInfo)
            SupabaseController.updateLastSmoked(program.lastSmoked)

            // 4. Evaluate achievement criteria against the new state and write
            //    any newly-earned achievements to user_achievements. Skips the
            //    network call (and the local append) when nothing was earned.
            val achievementData = Clear30Store.loadAchievementData()
            AchievementEngine.syncNewlyEarned(achievementData, userInfo, program)

            // 5. If the user is in an accountability group, mirror this check-in into
            //    the group activity feed (groups.add_checkin_activity). Best-effort.
            if (!userInfo.groupID.isNullOrEmpty()) {
                SupabaseController.addGroupCheckInActivity(sober)
            }
        }
    }
}
