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
        val t = target(def) ?: return false
        return when (def.checkType) {
            AchievementCheckType.STREAK -> currentSoberStreak(program) >= t
            AchievementCheckType.CUMULATIVE -> totalSoberDays(program) >= t
            AchievementCheckType.MILESTONE -> program.currentDay >= t
            AchievementCheckType.COUNT -> daysTracked(program) >= t
            // TODO(port): REDUCTION compares recent-usage frequency to a baseline;
            //   needs the iOS reference to know which baseline window. Skipped
            //   for now — returning false is the safe choice (never falsely earn).
            AchievementCheckType.REDUCTION -> false
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
            // Celebrate. The popup queue coalesces duplicates inside its 800ms
            // window so a "30 days clear" + "1 month streak" pair both surface
            // (different captions) — only an identical-payload re-fire is
            // dropped.
            PopupManager.request(
                PopupManager.Payload.Confetti(message = def.name, emoji = "🏆"),
            )
            // Post a system notification so the achievement is visible even
            // if the user has the app backgrounded when the check-in syncs
            // (e.g. they checked in, swiped away, and AchievementEngine
            // finishes the achievement-evaluation network round-trip).
            NotificationHandler.postAchievementEarned(userInfo, def.key, def.name)
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
