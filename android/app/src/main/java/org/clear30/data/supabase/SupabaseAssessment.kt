package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject

/**
 * Assessment social-proof, ported from iOS `getAssessmentSocialProofData` /
 * `incrementAssessmentSocialProofCount`. The live member count lives in
 * `library.one_offs` (key = "assessment_social_proof"); its `value` is a JSON blob
 * whose `peopleCount` the social-proof slide reads. The counter is bumped on appear.
 *
 * NOTE: `library` is a non-public schema, so the request MUST pass it explicitly
 * (`from(schema, table)` / `rpc(...) { schema = }`) or PostgREST silently 404s.
 */
private const val LIBRARY_SCHEMA = "library"

@Serializable
private data class SocialProofOneOff(val key: String, val value: String)

/** Live social-proof JSON value, or null if the row is absent (then the UI uses backup). */
suspend fun SupabaseController.getAssessmentSocialProofValue(): String? = runCatching {
    client.postgrest.from(LIBRARY_SCHEMA, "one_offs")
        .select {
            filter { eq("key", "assessment_social_proof") }
        }
        .decodeList<SocialProofOneOff>()
        .firstOrNull()
        ?.value
}.getOrNull()

/** Bumps the live social-proof counter; failures (e.g. no local row) are swallowed. */
suspend fun SupabaseController.incrementAssessmentSocialProofCount() {
    runCatching {
        client.postgrest.rpc("increment_assessment_social_proof_count", buildJsonObject {}) {
            schema = LIBRARY_SCHEMA
        }
    }
}
