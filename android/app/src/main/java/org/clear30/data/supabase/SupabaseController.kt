package org.clear30.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.clear30.BuildConfig
import org.clear30.data.model.AssessmentQuestionType

/**
 * SupabaseController — ported from SupabaseController.swift.
 *
 * The iOS controller wrapped the Supabase Swift SDK; this uses supabase-kt
 * (jan-tennert). The generic RPC / edge-function helpers are `suspend` (the iOS
 * versions used Task + completion callbacks); domain methods built on top —
 * spread across the various Supabase*.swift files — are ported incrementally and
 * launched from a coroutine scope at their call sites.
 *
 * Secrets come from BuildConfig (see local.properties), replacing the iOS
 * ConfidentialKit obfuscation.
 */
object SupabaseController {

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Postgrest)
        install(Auth) { autoLoadFromStorage = true }
        install(Functions)
        install(Realtime)
        install(Storage)
    }

    /** Lenient decoder for backend payloads (Swift `JSONDecoder.supabaseDecoder`). */
    val decoder = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        serializersModule = SerializersModule {
            // Forward-compat: unknown AssessmentQuestionType discriminators
            // (server-side A/B test of a new question kind) fall back to
            // [AssessmentQuestionType.Unknown] instead of throwing — keeps the
            // assessment payload loadable on older clients.
            polymorphic(AssessmentQuestionType::class) {
                defaultDeserializer { AssessmentQuestionType.Unknown.serializer() }
            }
        }
    }

    data class SupabaseFunctionError(
        val code: String? = null,
        val message: String,
    )

    // MARK: - RPC (public schema)

    /** rpc with no params. */
    suspend fun callFunction(function: SupabaseFunction): SupabaseFunctionError? = runCatching {
        client.postgrest.rpc(function.rawValue)
    }.fold({ null }, { it.toError() })

    /** rpc with params. */
    suspend inline fun <reified T : Any> callFunction(
        function: SupabaseFunction,
        params: T,
    ): SupabaseFunctionError? = runCatching {
        client.postgrest.rpc(function.rawValue, params)
    }.fold({ null }, { it.toError() })

    /** rpc returning a decoded value. */
    suspend inline fun <reified U> callFunction(
        function: SupabaseFunction,
        returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        client.postgrest.rpc(function.rawValue).decodeAs<U>()
    }.fold({ it to null }, { null to it.toError() })

    /** rpc with params returning a decoded value. */
    suspend inline fun <reified T : Any, reified U> callFunction(
        function: SupabaseFunction,
        params: T,
        returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        client.postgrest.rpc(function.rawValue, params).decodeAs<U>()
    }.fold({ it to null }, { null to it.toError() })

    // MARK: - Edge functions

    suspend inline fun <reified T : Any> callEdgeFunction(
        function: SupabaseEdgeFunction,
        params: T,
    ): SupabaseFunctionError? = runCatching {
        client.functions.invoke(function.rawValue, params)
    }.fold({ null }, { it.toError() })

    suspend inline fun <reified T : Any, reified U> callEdgeFunction(
        function: SupabaseEdgeFunction,
        params: T,
        returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        client.functions.invoke(function.rawValue, params).body<U>()
    }.fold({ it to null }, { null to it.toError() })

    /**
     * Map a thrown exception to the typed error (Swift `extractSupabaseError`).
     * supabase-kt throws RestException/HttpRequestException subclasses; we read
     * the message generically to stay version-agnostic.
     */
    fun Throwable.toError(): SupabaseFunctionError =
        SupabaseFunctionError(code = null, message = message ?: toString())
}

/**
 * RPC function names — ported as a value class wrapping the rawValue (like iOS's
 * `SupabaseFunction` enum). Named constants are filled in as SupabaseFunctions.swift
 * is ported; arbitrary names work via the constructor in the meantime.
 */
@JvmInline
value class SupabaseFunction(val rawValue: String) {
    companion object {
        // User
        val createUser = SupabaseFunction("create_user")
        val deleteAccount = SupabaseFunction("delete_user")
        // Program
        val getLatestUpdate = SupabaseFunction("program_get_latest_update")
        val submitAssessmentV2 = SupabaseFunction("program_submit_assessment_response_v2")
        val getFeedback = SupabaseFunction("program_get_feedback")
        val getMessages = SupabaseFunction("program_get_messages")
        val getMessagesQA = SupabaseFunction("program_get_messages_qa")
        val getTutorialMessagesV2 = SupabaseFunction("get_tutorial_messages_v2")
        val fetchMessagesByIDs = SupabaseFunction("fetch_messages_by_ids")
        // Group
        val addGroupMember = SupabaseFunction("add_member")
        val getUserGroup = SupabaseFunction("get_user_group")
        val getGroup = SupabaseFunction("get")
        val addCheckInActivity = SupabaseFunction("add_checkin_activity")
        val removeGroupMember = SupabaseFunction("remove_member")
        // Notifications
        val schedulePopInRequest = SupabaseFunction("popin_request_schedule")
        val clearPopInRequest = SupabaseFunction("popin_request_clear")
        // SMS
        val scheduleSms = SupabaseFunction("sms_schedule")
        val clearSms = SupabaseFunction("sms_clear")
        // Fred
        val sendDrFredMessage = SupabaseFunction("dr_fred_send_message")
        // Other
        val submitFeedback = SupabaseFunction("submit_feedback")
        val checkCode = SupabaseFunction("payment_check_code")
        val checkEmail = SupabaseFunction("payment_check_email_json")
        val checkSale = SupabaseFunction("payment_check_sale")
        val checkReferralCodeJson = SupabaseFunction("check_referral_code_json")
        val checkGuardianCode = SupabaseFunction("check_code")
        val incrementAssessmentSocialProofCount = SupabaseFunction("increment_assessment_social_proof_count")
        val getSupabaseExperiments = SupabaseFunction("get_user_experiments")
    }
}

/** Edge function names — see [SupabaseFunction]. */
@JvmInline
value class SupabaseEdgeFunction(val rawValue: String)

/** Known Postgres error codes (Swift `SupabaseErrorCode`). */
object SupabaseErrorCode {
    const val NOT_IN_GROUP = "P0001"
}

/** Column names on the `users` table (Swift `SupabaseUserProps`). */
object SupabaseUserProps {
    const val NAME = "name"
    const val EMOJI = "emoji"
    const val DAY_INFO = "day_info"
    const val FCM_TOKEN = "fcm_token"
    const val PHONE_NUMBER = "phone_number"
    const val SMS_SETTINGS = "sms_settings"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val YOUR_WHY = "your_why"
    const val LAST_SMOKED = "last_smoked"
    const val CUSTOM_CHECK_INS = "custom_check_ins"
    const val CONTENT_INFO = "content_info"
    const val PROGRAM_BREAKS = "program_breaks"
    const val TIMEZONE = "timezone"
    const val APP_VERSION = "app_version"
}
