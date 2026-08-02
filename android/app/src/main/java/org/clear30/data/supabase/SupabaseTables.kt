package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.SymptomInfo
import org.clear30.data.model.SymptomInfos
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * Library tables — ported from SupabaseTables.swift `getMeditationResources`. The
 * craving/sleep meditation libraries live in the `library` schema as
 * `{ id, type, data }` rows where `data` is the meditation JSON (`{ url, name }`).
 */
private const val LIBRARY_SCHEMA = "library"
private val resourceJson = Json { ignoreUnknownKeys = true }

@Serializable
data class MeditationResource(val id: Int, val type: String, val data: JsonObject) {
    val meditation: ProgramMeditation?
        get() = if (type == "meditation") runCatching { resourceJson.decodeFromJsonElement<ProgramMeditation>(data) }.getOrNull() else null
}

/** Fetch the craving_resources / sleep_resources meditation library. */
suspend fun SupabaseController.getMeditationResources(table: String): Result<List<ProgramMeditation>> = runCatching {
    client.postgrest.from(LIBRARY_SCHEMA, table)
        .select { }
        .decodeList<MeditationResource>()
        .mapNotNull { it.meditation }
}

/**
 * Real symptom infos from the public `get_symptom_infos` RPC (iOS `symptomInfo`).
 * The RPC returns a JSON object keyed by "emoji Name" → {color1,color2,tips,reddits,
 * prompts}; we decode it straight into [SymptomInfos.symptomInfos]. Returns null on
 * any failure or an empty payload so the caller can fall back to the built-in set.
 */
suspend fun SupabaseController.fetchSymptomInfos(): SymptomInfos? = runCatching {
    val map = client.postgrest.rpc("get_symptom_infos").decodeAs<Map<String, SymptomInfo>>()
    if (map.isEmpty()) null else SymptomInfos(symptomInfos = map.toMutableMap())
}.getOrNull()

// MARK: - Feature ideas (Feature Wishlist) — comms.feature_ideas / feature_idea_votes

private const val COMMS_SCHEMA = "comms"

@Serializable
data class FeatureIdea(
    val id: Int,
    val title: String,
    val body: String = "",
    @SerialName("hex_color") val hexColor: String? = null,
    val score: Int = 0,
    @SerialName("user_id") val userID: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val tag: String? = null,
)

@Serializable
data class FeatureIdeaVote(
    val id: Int? = null,
    @SerialName("feature_idea_id") val featureIdeaID: Int,
    @SerialName("user_id") val userID: String,
    val vote: Int,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class FeatureVoteUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("feature_idea_id") val featureIdeaId: Int,
    val vote: Int,
)

/** All feature ideas, highest-scored first (Swift getFeatureIdeas). Sort client-side
 *  so a server-side order clause can't empty the list if the column shape differs. */
suspend fun SupabaseController.getFeatureIdeas(): Result<List<FeatureIdea>> = runCatching {
    client.postgrest.from(COMMS_SCHEMA, "feature_ideas")
        .select { }
        .decodeList<FeatureIdea>()
        .sortedByDescending { it.score }
}

/** The user's existing votes (Swift getFeatureIdeaVotes). */
suspend fun SupabaseController.getFeatureIdeaVotes(userID: String): Result<List<FeatureIdeaVote>> = runCatching {
    client.postgrest.from(COMMS_SCHEMA, "feature_idea_votes")
        .select { filter { eq("user_id", userID) } }
        .decodeList<FeatureIdeaVote>()
}

/** Up/down-vote a feature idea (upsert on user_id+feature_idea_id). */
suspend fun SupabaseController.submitFeatureIdeaVote(userID: String, featureIdeaID: Int, vote: Int): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMS_SCHEMA, "feature_idea_votes")
        .upsert(FeatureVoteUpsert(userID, featureIdeaID, vote)) { onConflict = "user_id,feature_idea_id" }
}.fold({ null }, { it.toError() })

/** Clear the user's vote on a feature idea (Swift removeFeatureIdeaVote). */
suspend fun SupabaseController.removeFeatureIdeaVote(userID: String, featureIdeaID: Int): SupabaseFunctionError? = runCatching {
    client.postgrest.from(COMMS_SCHEMA, "feature_idea_votes")
        .delete { filter { eq("user_id", userID); eq("feature_idea_id", featureIdeaID) } }
}.fold({ null }, { it.toError() })

