package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.UserAchievement

/**
 * Achievements fetch — ported from SupabaseAchievements.swift (read path). Reads
 * the achievement definitions and the user's earned achievements. (iOS scoped
 * these to the achievements schema; reconcile schema if non-public.) Sync/write
 * methods are layered on with achievement-earning logic.
 */
suspend fun SupabaseController.getAchievementDefinitions(): List<AchievementDefinition> = runCatching {
    client.postgrest.from("definitions").select().decodeList<AchievementDefinition>()
}.getOrDefault(emptyList())

suspend fun SupabaseController.getUserAchievements(userID: String): List<UserAchievement> = runCatching {
    client.postgrest.from("user_achievements")
        .select { filter { eq("user_id", userID) } }
        .decodeList<UserAchievement>()
}.getOrDefault(emptyList())

/**
 * Fetch + persist the latest definitions + earned achievements into the local
 * [org.clear30.data.model.AchievementData] so the [org.clear30.data.AchievementEngine]
 * has criteria to evaluate after every check-in, even when the user never
 * opens the Profile tab.
 */
suspend fun refreshAchievementsCache(userID: String) {
    if (userID.isEmpty()) return
    val defs = SupabaseController.getAchievementDefinitions()
    val earned = SupabaseController.getUserAchievements(userID)
    if (defs.isEmpty() && earned.isEmpty()) return  // probably a network failure
    val ad = org.clear30.data.Clear30Store.loadAchievementData()
    ad.definitions = defs.toMutableList()
    ad.earnedAchievements = earned.toMutableList()
    org.clear30.data.Clear30Store.save(ad)
}
