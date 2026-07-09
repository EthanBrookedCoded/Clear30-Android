package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.CustomCheckInOption
import org.clear30.data.model.DayInfoArraySerializer
import org.clear30.data.model.PlainDate
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.ProgramNormativeFeedback
import org.clear30.data.model.SupabaseMessageWithStage

/**
 * Account-restore fetchers + the backend WIRE MODELS for the program-state
 * columns on `public.users` — ported from SupabaseRestore.swift +
 * SupabaseModels.swift.
 *
 * These wire models are the cross-device contract shared with iOS:
 *   content_info    { "YYYY-MM-DD": { "message_ids": [int], "progress": double? } }
 *   program_breaks  [{ name, type, start_date, end_date_override?, ... }]
 *   custom_check_ins[{ id, name, incomplete_option{emoji,name}, complete_option{...}, hue }]
 * The local domain models (ProgramBreak/ContentInfo/CustomCheckIn) use camelCase
 * Kotlin names and richer nested types, so they must NEVER be serialized straight
 * into these columns — always convert through the toData()/restore helpers here.
 */

// MARK: - Wire models (SupabaseModels.swift)

@Serializable
data class SupabaseContentInfo(
    val message_ids: List<Int> = emptyList(),
    val progress: Double? = null,
)

@Serializable
data class SupabaseProgramBreak(
    val name: String,
    val type: String,
    val start_date: Instant,
    val end_date_override: Instant? = null,
    val assessment_response_id: Int? = null,
    val normative_feedback: ProgramNormativeFeedback? = null,
    val post_assessment_completed: Boolean? = null,
    val post_assessment_response_id: Int? = null,
    val money_saved_adjustment: Int? = null,
) {
    /**
     * Rebuild the domain break (iOS `reconstructBreak`). Unknown type strings
     * return null and are dropped by the caller — a "life" break can't exist.
     * Assessment responses are fetched separately by id.
     */
    fun toProgramBreak(): ProgramBreak? {
        val breakType = ProgramBreakType.from(type) ?: return null
        return ProgramBreak(
            name = name,
            type = breakType,
            startDate = start_date,
            // The stored override IS the first day out of the break, same as the
            // in-memory field — no ±1 here (iOS round-trips it through
            // overrideEndDate(lastDayOfBreak: override - 1), which stores +1 back).
            endDateOverride = end_date_override,
            assessmentResponseID = assessment_response_id,
            normativeFeedback = normative_feedback,
            postAssessmentResponseID = post_assessment_response_id,
            postAssessmentCompleted = post_assessment_completed ?: false,
            moneySavedAdjustment = money_saved_adjustment ?: 0,
        )
    }
}

@Serializable
data class SupabaseCustomCheckInOption(val emoji: String, val name: String)

@Serializable
data class SupabaseCustomCheckIn(
    val id: String,
    val name: String,
    val incomplete_option: SupabaseCustomCheckInOption,
    val complete_option: SupabaseCustomCheckInOption,
    val hue: Double,
) {
    fun toCustomCheckIn() = CustomCheckIn(
        id = id,
        name = name,
        incompleteOption = CustomCheckInOption(incomplete_option.emoji, incomplete_option.name),
        completeOption = CustomCheckInOption(complete_option.emoji, complete_option.name),
        hue = hue,
    )
}

/** An if-then ("trigger_response") plan from the post-slip sheet. */
@Serializable
data class SupabaseTriggerResponse(
    val id: String,
    val if_text: String,
    val feeling_text: String? = null,
    val then_text: String,
    val created_at: Instant,
)

/** The user's `public.users` row, program columns only (iOS SupabaseUserData). */
@Serializable
data class SupabaseUserData(
    val id: String,
    val name: String = "",
    val emoji: String? = null,
    val last_smoked: Instant? = null,
    val your_why: String? = null,
    @Serializable(with = DayInfoArraySerializer::class)
    val day_info: Map<PlainDate, ProgramDayInfo>? = null,
    val custom_check_ins: List<SupabaseCustomCheckIn>? = null,
    val content_info: Map<String, SupabaseContentInfo>? = null,
    val program_breaks: List<SupabaseProgramBreak>? = null,
    val trigger_responses: List<SupabaseTriggerResponse>? = null,
    val created_at: Instant? = null,
)

/** A saved assessment response row (programs.program_assessment_responses). */
@Serializable
data class SupabaseAssessmentResponse(
    val id: Int,
    val user_id: String,
    val assessment: String,
    val responses: Map<String, List<String>>,
)

// MARK: - Fetchers

/** SELECT the user row (iOS `fetchUserData` — plain PostgREST, no RPC). */
suspend fun SupabaseController.fetchUserData(userID: String): SupabaseUserData? = runCatching {
    client.postgrest.from("public", "users")
        .select { filter { eq("id", userID) } }
        .decodeList<SupabaseUserData>()
        .firstOrNull()
}.onFailure { android.util.Log.w("SupabaseRestore", "fetchUserData failed: ${it.message}") }
    .getOrNull()

/** Fetch a saved assessment response by id (iOS `fetchAssessmentResponses`). */
suspend fun SupabaseController.fetchAssessmentResponse(assessmentResponseID: Int): SupabaseAssessmentResponse? = runCatching {
    client.postgrest.from("programs", "program_assessment_responses")
        .select { filter { eq("id", assessmentResponseID) } }
        .decodeList<SupabaseAssessmentResponse>()
        .firstOrNull()
}.onFailure { android.util.Log.w("SupabaseRestore", "fetchAssessmentResponse failed: ${it.message}") }
    .getOrNull()

/**
 * On a fresh device the user row only stores message *ids* (in `content_info`), not
 * the full bodies, so restoration fetches the bodies by id via the `programs`-schema
 * `fetch_messages_by_ids` RPC. (The day-to-day path uses `program_get_messages`,
 * which already returns full bodies; this is the migration/restore primitive.)
 */
suspend fun SupabaseController.fetchMessagesByIDs(ids: List<Long>): List<SupabaseMessageWithStage> {
    if (ids.isEmpty()) return emptyList()
    return runCatching {
        val params = buildJsonObject {
            put("message_ids", JsonArray(ids.map { JsonPrimitive(it) }))
        }
        client.postgrest.rpc("fetch_messages_by_ids", params) { schema = "programs" }
            .decodeList<SupabaseMessageWithStage>()
    }.onFailure { android.util.Log.w("SupabaseRestore", "fetch_messages_by_ids failed: ${it.message}") }
        .getOrElse { emptyList() }
}

// MARK: - Domain → wire converters (iOS Program.toContentInfoData / ProgramBreak.toData)

fun ProgramBreak.toData() = SupabaseProgramBreak(
    name = name,
    type = type.id,
    start_date = startDate,
    end_date_override = endDateOverride,
    assessment_response_id = assessmentResponseID,
    normative_feedback = normativeFeedback,
    post_assessment_completed = postAssessmentCompleted,
    post_assessment_response_id = postAssessmentResponseID,
    money_saved_adjustment = moneySavedAdjustment,
)

fun CustomCheckIn.toData() = SupabaseCustomCheckIn(
    id = id,
    name = name,
    incomplete_option = SupabaseCustomCheckInOption(incompleteOption.emoji, incompleteOption.name),
    complete_option = SupabaseCustomCheckInOption(completeOption.emoji, completeOption.name),
    hue = hue,
)

