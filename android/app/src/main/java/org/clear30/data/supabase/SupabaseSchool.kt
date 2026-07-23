package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import org.clear30.data.model.SchoolData
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * School / pilot backend calls — ported from the school-related parts of
 * SupabaseFunctions.swift (checkEmail, checkReferralCodeJson, getAssessmentStatus)
 * and SupabaseJSON.swift (`.schoolData`), plus the mid-pilot submit from
 * MidPilotAssessmentViewModel.swift and the coach-referral event insert from
 * PostAssessmentViewModel.swift.
 *
 * Schemas: `get_school_data` and `payment_check_email_json` have public-schema
 * entry points (verified in prod); `check_referral_code_json` lives in `payment`,
 * the assessment-status/mid-pilot functions in `schools`, and coach-referral
 * events are a direct insert into `comms.coach_referral_events`.
 */

/** `payment_check_email_json` result (Swift `SupabaseEmailCheckResults`). */
@Serializable
data class SupabaseEmailCheckResults(
    val org: String,
    val school_id: String? = null,
)

/** `check_referral_code_json` result (Swift `SupabaseReferralCodeResults`). */
@Serializable
data class SupabaseReferralCodeResults(
    val is_free: Boolean,
    val group_id: String? = null,
)

/** `schools.get_assessment_status` result (Swift `SupabaseAssessmentStatus`). */
@Serializable
data class SupabaseAssessmentStatus(
    val mid_pilot_completed: Boolean,
)

/**
 * Mid-pilot assessment payload (Swift `MidPilotResponses`). Multi-choice fields
 * carry the selected option STRINGS, not indices.
 */
@Serializable
data class MidPilotResponses(
    val withdrawal_symptoms: List<String>,
    val wellbeing_sleep: Int,
    val wellbeing_anxiety: Int,
    val wellbeing_focus: Int,
    val helpfulness: Int,
    val unexpected_value: String,
    val commitment: Int,
    val campus_wellness: List<String>,
    val recommendation: Int,
)

@Serializable
private data class SchoolDataParams(val school_id: String)

/**
 * Fetch a school's content bundle (Swift `getJSON(.schoolData(schoolID:))`).
 * Returns null on error AND when the school has no content — `get_school_data`
 * returns SQL NULL then, which fails the decode; iOS likewise surfaced nil.
 */
suspend fun SupabaseController.getSchoolData(schoolID: String): SchoolData? {
    val (data, error) = callFunction(
        SupabaseFunction.getSchoolData,
        SchoolDataParams(schoolID),
        SchoolData::class.java,
    )
    if (error != null) {
        android.util.Log.w("SupabaseSchool", "getSchoolData($schoolID) failed: ${error.message}")
    }
    return data
}

/**
 * Look up the signed-in user's email domain against the allowlist (Swift
 * `checkEmail`). The server reads the auth email itself — no params. RAISEs
 * (→ error) when the domain isn't allow-listed, so null is the common case.
 */
suspend fun SupabaseController.checkEmail(): SupabaseEmailCheckResults? =
    callFunction(SupabaseFunction.checkEmail, SupabaseEmailCheckResults::class.java).first

@Serializable
private data class ReferralCodeParams(val input_code: String)

/**
 * Validate a promo code from a referral deep link (Swift `checkCode` →
 * `payment_check_code`, public schema). Valid = the code row exists in
 * `payment.promo_codes`; the function also increments its `uses` counter.
 * Distinct from [checkReferralCodeJson] (`payment.referral_codes`), which
 * backs the manual onboarding entry and inserts unknown codes as non-free.
 */
suspend fun SupabaseController.checkCode(code: String): Boolean =
    callFunction(
        SupabaseFunction.checkCode,
        ReferralCodeParams(code),
        Boolean::class.java,
    ).first ?: false

/** Validate an onboarding referral code (Swift `checkReferralCodeJson`). */
suspend fun SupabaseController.checkReferralCodeJson(code: String): SupabaseReferralCodeResults? =
    callFunction(
        SupabaseFunction.checkReferralCodeJson,
        ReferralCodeParams(code),
        SupabaseReferralCodeResults::class.java,
        schema = "payment",
    ).first

/** School-user assessment status, used to rehydrate the mid-pilot flag on sign-in. */
suspend fun SupabaseController.getAssessmentStatus(): SupabaseAssessmentStatus? =
    callFunction(
        SupabaseFunction.getAssessmentStatus,
        SupabaseAssessmentStatus::class.java,
        schema = "schools",
    ).first

@Serializable
private data class SubmitMidPilotParams(
    val p_school_id: String,
    val p_responses: MidPilotResponses,
)

/** Submit the mid-pilot assessment (Swift `MidPilotAssessmentViewModel.submitAssessment`). */
suspend fun SupabaseController.submitMidPilotAssessment(
    schoolID: String,
    responses: MidPilotResponses,
): SupabaseFunctionError? = callFunction(
    SupabaseFunction.submitMidPilotAssessment,
    SubmitMidPilotParams(schoolID, responses),
    schema = "schools",
)

@Serializable
private data class CoachReferralEventInsert(
    val user_id: String,
    val event_type: String,
    val placement: String,
    val coach_slug: String,
)

/**
 * Log a coach-referral funnel event (Swift `PostAssessmentViewModel.logCoachReferralEvent`
 * / `CoachReferralBookingSheet`). Fire-and-forget: failures are logged, never surfaced.
 */
suspend fun SupabaseController.logCoachReferralEvent(
    userID: String,
    eventType: String,
    placement: String = "post_assessment",
    coachSlug: String = "phil-stille",
) {
    runCatching {
        client.postgrest.from("comms", "coach_referral_events")
            .insert(CoachReferralEventInsert(userID, eventType, placement, coachSlug))
    }.onFailure {
        android.util.Log.w("SupabaseSchool", "logCoachReferralEvent($eventType) failed: ${it.message}")
    }
}
