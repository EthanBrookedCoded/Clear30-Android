package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.clear30.data.model.NormativeData
import org.clear30.data.model.RemoteAssessmentQuestion

/**
 * Assessment support data — ports of iOS SupabaseTables.getNormativeData /
 * getRemoteAssessmentQuestions. Both are best-effort: failures fall back to
 * the baked-in defaults / no injected questions, like iOS.
 */

@Serializable
private data class OneOff(val key: String, val value: String)

private val oneOffJson = Json { ignoreUnknownKeys = true }

/** Live normative-usage table from `library.one_offs` (key `normative_data`);
 *  the row's value is a JSON string encoding `[NormativeData]`. */
suspend fun SupabaseController.getNormativeData(): List<NormativeData>? = runCatching {
    client.postgrest.from("library", "one_offs")
        .select { filter { eq("key", "normative_data") } }
        .decodeList<OneOff>()
        .firstOrNull()
        ?.let { oneOffJson.decodeFromString<List<NormativeData>>(it.value) }
}.getOrNull()

/** Enabled remote assessment questions, ordered like iOS. */
suspend fun SupabaseController.getRemoteAssessmentQuestions(): List<RemoteAssessmentQuestion> = runCatching {
    client.postgrest.from("programs", "remote_assessment_questions")
        .select {
            filter { eq("enabled", true) }
            order("after_question_id", Order.ASCENDING)
        }
        .decodeList<RemoteAssessmentQuestion>()
}.getOrDefault(emptyList())
