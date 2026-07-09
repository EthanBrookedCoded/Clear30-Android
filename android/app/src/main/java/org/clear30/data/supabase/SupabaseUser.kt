package org.clear30.data.supabase

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestionType
import org.clear30.data.model.DayInfoArraySerializer
import org.clear30.data.model.PlainDate
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.ToggleSettings
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * User creation + assessment submission — ported from the `createUser` /
 * `submitAssessmentWithID` methods in SupabaseFunctions.swift.
 *
 * Both back onto Postgres RPCs (`create_user`, `program_submit_assessment_response_v2`),
 * which read the authenticated `auth.uid()` server-side. `create_user` upserts the
 * `public.users` row (transferring phone/email from `auth.users`) and the submit
 * RPC inserts into `programs.program_assessment_responses`, filtering the payload
 * down to the assessment's known question IDs.
 *
 * The iOS path also runs Amplitude/RevenueCat sign-in and the normative-feedback +
 * break + `program.start` machinery; those are deliberately omitted here (see
 * [org.clear30.data.AssessmentSubmissionHandler]).
 */

/** New-user payload (Swift `SupabaseNewUser`); wrapped in `{ "user_data": ... }`. */
@Serializable
data class SupabaseNewUser(
    val id: String? = null,
    val name: String,
    val emoji: String,
    // Encoded as the backend's positional array, not a JSON object — see
    // [DayInfoArraySerializer]; otherwise groups.get() throws on jsonb_array_length.
    @Serializable(with = DayInfoArraySerializer::class)
    val day_info: Map<PlainDate, ProgramDayInfo>,
    val fcm_token: String? = null,
    val sms_settings: ToggleSettings? = null,
    val logging_id: String,
    val appstack_id: String? = null,
    // The backend stamps peer_support_migrated / _at itself on insert, so those
    // aren't sent. `platform` lets the create_user RPC tag the row 'android'.
    val timezone: String = TimeZone.currentSystemDefault().id,
    val platform: String = "android",
)

@Serializable
private data class CreateUserParams(val user_data: SupabaseNewUser)

@Serializable
private data class SubmitAssessmentParams(
    val assessment_id: String,
    val responses: Map<String, List<String>>,
)

/**
 * Create (or upsert) the user row, then resolve `users.id` (Swift `createUser`,
 * which calls the RPC and then `getUserID`). Returns (userID, error).
 */
suspend fun SupabaseController.createUser(user: SupabaseNewUser): Pair<String?, SupabaseFunctionError?> {
    val error = callFunction(SupabaseFunction.createUser, CreateUserParams(user))
    if (error != null) return null to error
    val userID = getUserID()
    return if (userID.isNullOrEmpty()) {
        null to SupabaseFunctionError(message = "Could not get user ID.")
    } else {
        userID to null
    }
}

/**
 * Ensure the caller has a `public.users` row, returning its id. If one already
 * exists this is just a `getUserID()` round-trip; only when it's missing do we
 * upsert a minimal row (empty day_info) via [createUser].
 *
 * Why: every groups/community RPC resolves `auth.uid()` → `users.id` through
 * `get_user_id()`. A brand-new account that slipped past onboarding's create_user
 * (its failure there is only logged) has no row, so `get_user_id()` is null and
 * the `groups.groups` insert policy + `add_member` both reject — i.e. "Start your
 * group" silently does nothing. Guarding on the missing row means we never clobber
 * an existing user's day_info, since `create_user`'s UPDATE branch only runs when
 * a row is already present (which we've ruled out here).
 */
suspend fun SupabaseController.ensureUserRow(
    name: String,
    emoji: String,
    loggingID: String,
    fcmToken: String? = null,
    smsSettings: ToggleSettings? = null,
): Pair<String?, SupabaseFunctionError?> {
    getUserID()?.takeIf { it.isNotEmpty() }?.let { return it to null }
    return createUser(
        SupabaseNewUser(
            name = name,
            emoji = emoji,
            day_info = emptyMap(),
            fcm_token = fcmToken,
            sms_settings = smsSettings,
            logging_id = loggingID,
        ),
    )
}

/**
 * Submit assessment responses (Swift `submitAssessmentWithID`). Formats the
 * `[ProgramAssessmentResponse]` into the `{ strippedPrompt: [values] }` map the
 * backend expects, then fires `program_submit_assessment_response_v2`. Returns
 * (responseID, error). The backend filters keys to the assessment's question IDs,
 * so prompts not part of the assessment are dropped server-side.
 */
suspend fun SupabaseController.submitAssessment(
    assessmentID: String,
    responses: List<ProgramAssessmentResponse>,
    comments: String? = null,
): Pair<Long?, SupabaseFunctionError?> {
    val formatted = formatAssessmentResponses(responses).toMutableMap()
    if (comments != null) formatted["Comments"] = listOf(comments)
    val params = SubmitAssessmentParams(assessment_id = assessmentID, responses = formatted)
    return callFunction(SupabaseFunction.submitAssessmentV2, params, Long::class.java)
}

/**
 * Transform collected responses into the backend's `{ key: [values] }` shape —
 * ported from the `formattedAssessmentResponses` loop in SupabaseFunctions.swift.
 *
 * - "Exclude"-tagged prompts (e.g. the name field) are never submitted.
 * - multiSpectrum questions fan out to one entry per spectrum option, mapped to
 *   Not / Somewhat / Yes.
 * - date/number questions stash their value as a string in `options[0]` (see
 *   AllAssessment's result handling), so we read it back from there.
 * - everything else maps the chosen option indices back to their option labels.
 */
fun formatAssessmentResponses(
    responses: List<ProgramAssessmentResponse>,
): Map<String, List<String>> {
    val out = linkedMapOf<String, List<String>>()
    for (qr in responses) {
        val q = qr.question
        val key = q.strippedPrompt
        if (key.contains("Exclude")) continue

        when (val type = q.type) {
            is AssessmentQuestionType.MultiSpectrum -> {
                for (i in q.options.indices) {
                    val r = qr.responses.getOrNull(i) ?: continue
                    out[q.options[i]] = listOf(if (r == 0) "Not" else if (r == 1) "Somewhat" else "Yes")
                }
            }
            // Date-style number questions carry their value as a string option;
            // daysUsing uses the multiple-choice path (matches iOS).
            is AssessmentQuestionType.Number -> {
                if (key == AssessmentQuestionID.DAYS_USING.raw) {
                    q.mapOptionIndices(qr.responses)?.let { out[key] = it }
                } else {
                    q.options.firstOrNull()?.let { out[key] = listOf(it) }
                }
            }
            else -> q.mapOptionIndices(qr.responses)?.let { out[key] = it }
        }
    }
    return out
}

/** Map response indices to their option labels, or null if out of bounds/empty. */
private fun org.clear30.data.model.ProgramAssessmentQuestion.mapOptionIndices(
    responses: List<Int>,
): List<String>? {
    if (options.isEmpty()) return null
    if (responses.any { it >= options.size }) return null
    return responses.map { options[it] }
}
