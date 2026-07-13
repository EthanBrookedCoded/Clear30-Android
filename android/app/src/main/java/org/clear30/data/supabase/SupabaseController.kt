package org.clear30.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
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

    /** Lenient decoder for backend payloads (Swift `JSONDecoder.supabaseDecoder`). */
    val decoder = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        // This Json doubles as the PUSH ENCODER for every RPC param and column
        // write. Swift's Codable always encodes non-optional fields, so defaulted
        // fields are part of the cross-platform wire contract: without this,
        // `platform: "android"` is dropped from create_user (row lands as 'ios'),
        // and empty `loggedSymptoms` / `message_ids` are dropped from
        // day_info/content_info — which iOS decodes NON-optionally, so its
        // sign-in restore of Android-written data throws. Nulls stay omitted
        // (explicitNulls=false), matching Swift's nil handling.
        encodeDefaults = true
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

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // Decode ALL Postgrest/Functions payloads with the lenient [decoder].
        // supabase-kt's default Json is strict, so one unexpected/null column in a
        // `select(*)` (e.g. the community feed) throws and the swallowed error
        // silently empties the result. ignoreUnknownKeys + coerceInputValues +
        // explicitNulls=false make decodes resilient.
        defaultSerializer = KotlinXSerializer(decoder)
        install(Postgrest)
        install(Auth) { autoLoadFromStorage = true }
        install(Functions)
        install(Realtime)
        install(Storage)
    }

    init {
        android.util.Log.i(
            "SupabaseController",
            "Supabase → ${if (BuildConfig.SUPABASE_LOCAL) "LOCAL" else "PROD"} @ ${BuildConfig.SUPABASE_URL}",
        )
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

    /**
     * rpc with params. supabase-kt 3.x's `rpc(name, parameters)` only accepts
     * `JsonObject` (or `JsonElement`) directly — generic typed objects must be
     * serialized first. We use the shared [decoder] (which is a [Json] instance,
     * so it doubles as the encoder) so unknown fields tolerated on decode are
     * also stripped on encode for forward compatibility.
     */
    suspend inline fun <reified T : Any> callFunction(
        function: SupabaseFunction,
        params: T,
    ): SupabaseFunctionError? = runCatching {
        val paramsJson = decoder.encodeToJsonElement(decoder.serializersModule.serializer<T>(), params)
            as kotlinx.serialization.json.JsonObject
        client.postgrest.rpc(function.rawValue, paramsJson)
    }.fold({ null }, { it.toError() })

    /** rpc returning a decoded value. */
    suspend inline fun <reified U : Any> callFunction(
        function: SupabaseFunction,
        @Suppress("UNUSED_PARAMETER") returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        // supabase-kt's PostgrestResult.decodeAs<T> constrains T : Any, so
        // the wrapper has to match — otherwise the call-site type can be a
        // nullable T? which decodeAs refuses.
        client.postgrest.rpc(function.rawValue).decodeAs<U>()
    }.fold({ it to null }, { null to it.toError() })

    /** rpc with params returning a decoded value. */
    suspend inline fun <reified T : Any, reified U : Any> callFunction(
        function: SupabaseFunction,
        params: T,
        @Suppress("UNUSED_PARAMETER") returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        val paramsJson = decoder.encodeToJsonElement(decoder.serializersModule.serializer<T>(), params)
            as kotlinx.serialization.json.JsonObject
        client.postgrest.rpc(function.rawValue, paramsJson).decodeAs<U>()
    }.fold({ it to null }, { null to it.toError() })

    // MARK: - Edge functions

    suspend inline fun <reified T : Any> callEdgeFunction(
        function: SupabaseEdgeFunction,
        params: T,
    ): SupabaseFunctionError? = runCatching {
        val paramsJson = decoder.encodeToJsonElement(decoder.serializersModule.serializer<T>(), params)
        client.functions.invoke(function.rawValue, paramsJson)
    }.fold({ null }, { it.toError() })

    suspend inline fun <reified T : Any, reified U> callEdgeFunction(
        function: SupabaseEdgeFunction,
        params: T,
        @Suppress("UNUSED_PARAMETER") returnType: Class<U>,
    ): Pair<U?, SupabaseFunctionError?> = runCatching {
        val paramsJson = decoder.encodeToJsonElement(decoder.serializersModule.serializer<T>(), params)
        client.functions.invoke(function.rawValue, paramsJson).body<U>()
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
