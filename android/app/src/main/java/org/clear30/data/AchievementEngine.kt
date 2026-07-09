package org.clear30.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.clear30.data.model.AchievementCheckType
import org.clear30.data.model.AchievementData
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserAchievement
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.UserAchievementInsert
import org.clear30.data.supabase.addUserAchievement
import org.clear30.util.adding
import org.clear30.util.now

/**
 * Evaluates [AchievementDefinition]s against the live program state and
 * persists any newly-earned achievements. Ported from the iOS achievement
 * engine — the per-checkType logic mirrors the Swift evaluator (streak /
 * cumulative / milestone / count); REDUCTION is more involved and left as a
 * TODO until its iOS reference is available.
 *
 * Called from [CheckInLogger.persist] after every check-in, so a user reaching
 * 30 days clear sees their achievement light up without restarting the app or
 * waiting for a server cron.
 */
object AchievementEngine {

    /** Pull the integer `target` from checkParams, trying the common iOS keys. */
    private fun target(def: AchievementDefinition): Int? {
        val keys = listOf("min_days", "min_total", "day", "value", "target", "count")
        for (k in keys) {
            val v = def.checkParams[k] as? JsonPrimitive ?: continue
            v.intOrNull?.let { return it }
        }
        return null
    }

    /** Read a single int checkParam by exact key (REDUCTION uses percentage/period_days). */
    private fun intParam(def: AchievementDefinition, key: String): Int? =
        (def.checkParams[key] as? JsonPrimitive)?.intOrNull

    /**
     * REDUCTION — ported from iOS `ReductionChecker`. Compares smoking-day frequency
     * over the trailing `period_days` window against the prior equal-length baseline
     * window; earned when the reduction is at least `percentage`.
     *
     * Frequency = smokingDays / checkedInDays * 100, where a "check-in" is any
     * `dayInfo` entry in the window and a smoking day is `sober == false` (iOS divides
     * by actual check-ins so missing days aren't treated as sober). Returns false when
     * either window has no check-ins or the baseline frequency is zero.
     */
    private fun reductionEarned(def: AchievementDefinition, program: Program): Boolean {
        val percentage = intParam(def, "percentage") ?: return false
        val periodDays = intParam(def, "period_days") ?: return false
        if (periodDays <= 0) return false

        val today = PlainDate.from(now())
        val periodStart = PlainDate.from(now().adding(days = -periodDays))
        val baselineEnd = PlainDate.from(now().adding(days = -periodDays - 1))
        val baselineStart = PlainDate.from(now().adding(days = -periodDays * 2 - 1))

        fun frequency(start: PlainDate, end: PlainDate): Double? {
            val entries = program.dayInfo.filterKeys { it >= start && it <= end }
            if (entries.isEmpty()) return null
            val smoking = entries.values.count { it.sober == false }
            return smoking.toDouble() / entries.size.toDouble() * 100.0
        }

        val periodFreq = frequency(periodStart, today) ?: return false
        val baselineFreq = frequency(baselineStart, baselineEnd) ?: return false
        if (baselineFreq <= 0.0) return false

        val reduction = ((baselineFreq - periodFreq) / baselineFreq) * 100.0
        return reduction.coerceAtLeast(0.0) >= percentage.toDouble()
    }

    /** Consecutive sober days ending today (or yesterday if today is unlogged). */
    private fun currentSoberStreak(program: Program): Int {
        var streak = 0
        var date = now()
        if (program.dayInfo[PlainDate.from(date)]?.sober != true) date = date.adding(days = -1)
        while (program.dayInfo[PlainDate.from(date)]?.sober == true) {
            streak++; date = date.adding(days = -1)
        }
        return streak
    }

    private fun totalSoberDays(program: Program): Int =
        program.dayInfo.values.count { it.sober == true }

    private fun daysTracked(program: Program): Int = program.dayInfo.size

    /** True if [def]'s criteria are currently satisfied by the user's state. */
    fun isEarned(def: AchievementDefinition, program: Program, @Suppress("UNUSED_PARAMETER") userInfo: UserInfo): Boolean {
        // REDUCTION reads its own params (percentage/period_days), so it bypasses the
        // shared integer-`target` lookup the threshold check-types rely on.
        if (def.checkType == AchievementCheckType.REDUCTION) return reductionEarned(def, program)
        val t = target(def) ?: return false
        return when (def.checkType) {
            AchievementCheckType.STREAK -> currentSoberStreak(program) >= t
            AchievementCheckType.CUMULATIVE -> totalSoberDays(program) >= t
            AchievementCheckType.MILESTONE -> program.currentDay >= t
            // COUNT achievements are ACTIVITY counts (claire_conversation, meditation,
            // community_post, …), NOT day counts. The app doesn't track per-activity
            // totals yet, and evaluating them against `daysTracked` falsely earns a
            // whole batch at once (e.g. "Claire BFF" — 15 Claire chats — fires after
            // 15 tracked days despite zero chats). Until real activity counters exist,
            // COUNT never auto-earns.
            AchievementCheckType.COUNT -> false
            AchievementCheckType.REDUCTION -> reductionEarned(def, program)
        }
    }

    /**
     * Evaluate all known definitions; for every active definition the user
     * hasn't been credited with yet whose criteria are now satisfied, append a
     * UserAchievement locally and write it to `user_achievements` on Supabase.
     */
    suspend fun syncNewlyEarned(
        achievementData: AchievementData,
        userInfo: UserInfo,
        program: Program,
    ) {
        if (achievementData.definitions.isEmpty()) return  // nothing to evaluate
        if (userInfo.userID.isEmpty()) return              // no signed-in account

        val alreadyEarned = achievementData.earnedAchievements.map { it.achievementKey }.toSet()
        val newlyEarned = achievementData.definitions
            .asSequence()
            .filter { it.isActive }
            .filter { it.key !in alreadyEarned }
            .filter { isEarned(it, program, userInfo) }
            .toList()

        if (newlyEarned.isEmpty()) return

        val nowIso = now().toString()
        newlyEarned.forEach { def ->
            achievementData.earnedAchievements.add(
                UserAchievement(
                    id = null,
                    userId = userInfo.userID,
                    achievementKey = def.key,
                    earnedAt = nowIso,
                    customData = emptyMap(),
                    isSynced = false,
                    isVisited = false,
                ),
            )
            // The "I just hit a milestone" analytic — fires per achievement,
            // not in aggregate, so funnel analyses can attribute by key.
            Logger.logEvent(
                userInfo.loggingID,
                LogEventType.earnedAchievement,
                mapOf(
                    LogEventExtraDataType.ACHIEVEMENT_KEY to def.key,
                    LogEventExtraDataType.ACHIEVEMENT_NAME to def.name,
                ),
            )
            // No celebration popup / notification: a freshly-restored or backdated
            // account crosses many thresholds at once, which spammed a stack of
            // "🏆 …" overlays. Achievements are still recorded silently and show in
            // the Profile grid. (Re-introduce a tasteful single celebration later.)
        }
        Clear30Store.save(achievementData)

        // Push each to the backend. Flag isSynced individually so a network
        // failure on one doesn't prevent the others from being attempted.
        newlyEarned.forEach { def ->
            val err = SupabaseController.addUserAchievement(
                UserAchievementInsert(
                    userId = userInfo.userID,
                    achievementKey = def.key,
                    earnedAt = nowIso,
                    customData = JsonObject(emptyMap()),
                ),
            )
            if (err == null) {
                achievementData.earnedAchievements
                    .firstOrNull { it.achievementKey == def.key && it.isSynced != true }
                    ?.isSynced = true
            }
        }
        Clear30Store.save(achievementData)
    }
}
