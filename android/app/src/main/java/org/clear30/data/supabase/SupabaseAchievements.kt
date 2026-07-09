package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.clear30.data.model.AchievementDefinition
import org.clear30.data.model.AchievementRarity
import org.clear30.data.model.AchievementStats
import org.clear30.data.model.UserAchievement

/**
 * Achievements fetch — ported from SupabaseAchievements.swift (read path).
 *
 * Every table lives in the non-public `achievements` schema (definitions /
 * rarities / user_achievements / stats), so each call targets it via
 * `from(schema, table)` — the default `public` schema has none of them, which
 * is why the grid came up empty. (`achievements` is in config.toml's exposed
 * `schemas`.) Rarities + stats are fetched separately and joined client-side
 * by `rarity_id` / `achievement_key`, exactly as iOS does, so the cards know
 * their rarity gradient + glyph + "X% have this".
 */
private const val ACHIEVEMENTS_SCHEMA = "achievements"

/** All rarities (achievements.rarities), ordered by display_order (Uncommon→Legendary). */
suspend fun SupabaseController.getAchievementRarities(): List<AchievementRarity> = runCatching {
    client.postgrest.from(ACHIEVEMENTS_SCHEMA, "rarities")
        .select { order("display_order", Order.ASCENDING) }
        .decodeList<AchievementRarity>()
}.onFailure { android.util.Log.w("SupabaseAchievements", "rarities failed: ${it.message}") }
    .getOrDefault(emptyList())

suspend fun SupabaseController.getAchievementDefinitions(): List<AchievementDefinition> = runCatching {
    client.postgrest.from(ACHIEVEMENTS_SCHEMA, "definitions")
        .select {
            filter { eq("is_active", true) }
            order("display_order", Order.ASCENDING)
        }
        .decodeList<AchievementDefinition>()
}.onFailure { android.util.Log.w("SupabaseAchievements", "definitions failed: ${it.message}") }
    .getOrDefault(emptyList())

suspend fun SupabaseController.getUserAchievements(userID: String): List<UserAchievement> = runCatching {
    client.postgrest.from(ACHIEVEMENTS_SCHEMA, "user_achievements")
        .select {
            filter { eq("user_id", userID) }
            order("earned_at", Order.DESCENDING)
        }
        .decodeList<UserAchievement>()
}.onFailure { android.util.Log.w("SupabaseAchievements", "user achievements failed: ${it.message}") }
    .getOrDefault(emptyList())

/** Aggregate earn stats (achievements.stats) for the "X% have this" detail badge. */
suspend fun SupabaseController.getAchievementStats(): List<AchievementStats> = runCatching {
    client.postgrest.from(ACHIEVEMENTS_SCHEMA, "stats")
        .select()
        .decodeList<AchievementStats>()
}.onFailure { android.util.Log.w("SupabaseAchievements", "stats failed: ${it.message}") }
    .getOrDefault(emptyList())

/**
 * Attach each definition's [AchievementRarity] (by `rarity_id`) and
 * [AchievementStats] (by `achievement_key`) in place — the client-side join the
 * iOS `AchievementManager` does after the three separate fetches, so the cards
 * can read `def.rarity` / `def.stats` directly.
 */
fun List<AchievementDefinition>.withRarityAndStats(
    rarities: List<AchievementRarity>,
    stats: List<AchievementStats>,
): List<AchievementDefinition> {
    val rarityById = rarities.associateBy { it.id }
    val statsByKey = stats.associateBy { it.achievementKey }
    forEach { def ->
        def.rarity = rarityById[def.rarityId]
        def.stats = statsByKey[def.key]
    }
    return this
}

/**
 * Fetch + persist the latest definitions (+ rarity + stats) + earned
 * achievements into the local [org.clear30.data.model.AchievementData] so the
 * [org.clear30.data.AchievementEngine] has criteria to evaluate after every
 * check-in, even when the user never opens the Profile tab.
 */
suspend fun refreshAchievementsCache(userID: String) {
    if (userID.isEmpty()) return
    val rarities = SupabaseController.getAchievementRarities()
    val stats = SupabaseController.getAchievementStats()
    val defs = SupabaseController.getAchievementDefinitions().withRarityAndStats(rarities, stats)
    val earned = SupabaseController.getUserAchievements(userID)
    if (defs.isEmpty() && earned.isEmpty()) return  // probably a network failure
    val ad = org.clear30.data.Clear30Store.loadAchievementData()
    ad.definitions = defs.toMutableList()
    // Preserve any locally-tracked isVisited flags across the refresh so freshly
    // synced achievements stay "New" until the user opens them.
    val previouslyVisited = ad.earnedAchievements.filter { it.isVisited == true }.map { it.achievementKey }.toSet()
    ad.earnedAchievements = earned.map { ua ->
        if (ua.achievementKey in previouslyVisited) ua.apply { isVisited = true } else ua
    }.toMutableList()
    org.clear30.data.Clear30Store.save(ad)
}
