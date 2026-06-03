package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire format for inserting a user-earned achievement — matches the iOS
 * `syncUserAchievement` payload. The id is server-assigned, so we never send
 * one; `earned_at` is an ISO instant string (`Instant.toString()`).
 */
@Serializable
data class UserAchievementInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("achievement_key") val achievementKey: String,
    @SerialName("earned_at") val earnedAt: String,
    @SerialName("custom_data") val customData: JsonObject = JsonObject(emptyMap()),
)

/** Insert a single earned achievement into `user_achievements`. */
suspend fun SupabaseController.addUserAchievement(insert: UserAchievementInsert): SupabaseController.SupabaseFunctionError? = runCatching {
    client.postgrest.from("user_achievements").insert(insert)
}.fold({ null }, { it.toError() })
