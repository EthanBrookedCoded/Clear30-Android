package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Achievement models — ported from AchievementData.swift.
 *
 * The iOS `[String: AnyCodable]` dynamic params become `Map<String, JsonElement>`
 * (kotlinx's arbitrary-JSON type). Supabase snake_case keys preserved via
 * @SerialName. Timestamps stay String, matching the iOS model.
 */
@Serializable
class AchievementData(
    var definitions: MutableList<AchievementDefinition> = mutableListOf(),
    var earnedAchievements: MutableList<UserAchievement> = mutableListOf(),
    var miniDisplayAchievementIDs: MutableList<String> = mutableListOf(),
    var lastDefinitionsUpdate: String? = null,
    var lastEarnedSync: String? = null,
    var activityCache: MutableMap<String, Int> = mutableMapOf(),
) {
    companion object {
        const val STORE_KEY = "achievement_data"
    }
}

@Serializable
data class AchievementDefinition(
    val id: Int,
    val key: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val subcategory: AchievementSubcategory,
    @SerialName("sf_symbol") val sfSymbol: String,
    @SerialName("rarity_id") val rarityId: Int,
    @SerialName("check_type") val checkType: AchievementCheckType,
    @SerialName("check_params") val checkParams: Map<String, JsonElement> = emptyMap(),
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("show_stats") var showStats: Boolean? = null,
    @SerialName("display_order") val displayOrder: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("notification_title") var notificationTitle: String? = null,
    @SerialName("notification_body") var notificationBody: String? = null,
    @SerialName("notification_delay_minutes") var notificationDelayMinutes: Int? = null,
    var rarity: AchievementRarity? = null,
    var stats: AchievementStats? = null,
) {
    override fun hashCode(): Int = id
    override fun equals(other: Any?): Boolean = other is AchievementDefinition && other.id == id
}

@Serializable
data class AchievementRarity(
    val id: Int,
    val name: String,
    @SerialName("gradient_start") val gradientStart: String,
    @SerialName("gradient_end") val gradientEnd: String,
    @SerialName("display_order") val displayOrder: Int,
    @SerialName("sf_symbol") val sfSymbol: String,
)

@Serializable
data class UserAchievement(
    val id: Int? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("achievement_key") val achievementKey: String,
    @SerialName("earned_at") val earnedAt: String,
    @SerialName("custom_data") val customData: Map<String, JsonElement> = emptyMap(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    // Local-only tracking (no Supabase column)
    var isSynced: Boolean? = null,
    var isVisited: Boolean? = null,
)

@Serializable
data class AchievementStats(
    @SerialName("achievement_key") val achievementKey: String,
    @SerialName("total_earned") val totalEarned: Int,
    @SerialName("percentage_earned") val percentageEarned: Double,
    @SerialName("last_calculated_at") val lastCalculatedAt: String,
    @SerialName("total_users_at_calculation") val totalUsersAtCalculation: Int,
)

@Serializable
enum class AchievementCategory {
    @SerialName("without_weed") WITHOUT_WEED,
    @SerialName("in_app_activity") IN_APP_ACTIVITY,
    @SerialName("seasonal") SEASONAL,
}

@Serializable
enum class AchievementSubcategory {
    @SerialName("time") TIME,
    @SerialName("money") MONEY,
    @SerialName("firsts") FIRSTS,
    @SerialName("continuous") CONTINUOUS,
}

@Serializable
enum class AchievementCheckType {
    @SerialName("streak") STREAK,
    @SerialName("cumulative") CUMULATIVE,
    @SerialName("milestone") MILESTONE,
    @SerialName("reduction") REDUCTION,
    @SerialName("count") COUNT,
}
