package org.clear30.data.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.Serializable
import org.clear30.BuildConfig
import org.clear30.data.model.ToggleSettings
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError

/**
 * SupabaseController domain methods — ported from SupabaseFunctions.swift.
 *
 * iOS used Task + completion callbacks; these are `suspend` and return the
 * typed error (or null). Most are thin wrappers over [updateUser] (patch the
 * caller's row in `public.users`) or the generic RPC helpers.
 */

private val authUserId: String?
    get() = SupabaseController.client.auth.currentUserOrNull()?.id

/** Update columns on the current user's `users` row (Swift `updateUser`). */
suspend fun SupabaseController.updateUser(updates: JsonObject): SupabaseFunctionError? {
    val authId = authUserId ?: return SupabaseFunctionError(message = "No auth session")
    return runCatching {
        client.postgrest.from("users").update(updates) {
            filter { eq("auth_id", authId) }
        }
    }.fold({ null }, { it.toError() })
}

private fun cols(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.toMap())

suspend fun SupabaseController.signOut(): SupabaseFunctionError? =
    runCatching { client.auth.signOut() }.fold({ null }, { it.toError() })

/** Current user's `users.id` (Swift `getUserID`). */
suspend fun SupabaseController.getUserID(): String? {
    @Serializable data class Row(val id: String)
    val authId = authUserId ?: return null
    return runCatching {
        client.postgrest.from("users")
            .select(Columns.list("id")) { filter { eq("auth_id", authId) } }
            .decodeSingle<Row>().id
    }.getOrNull()
}

suspend fun SupabaseController.getUserAuthID(): String? =
    getUserID() ?: authUserId?.lowercase()

// User setters

suspend fun SupabaseController.updateFCMToken(fcmToken: String): SupabaseFunctionError? =
    updateUser(cols(SupabaseUserProps.FCM_TOKEN to JsonPrimitive(fcmToken)))

suspend fun SupabaseController.updateSMSSettings(smsSettings: ToggleSettings): SupabaseFunctionError? =
    updateUser(cols(SupabaseUserProps.SMS_SETTINGS to decoder.encodeToJsonElement(ToggleSettings.serializer(), smsSettings)))

suspend fun SupabaseController.updateNotificationSettings(settings: ToggleSettings): SupabaseFunctionError? =
    updateUser(cols(SupabaseUserProps.NOTIFICATION_SETTINGS to decoder.encodeToJsonElement(ToggleSettings.serializer(), settings)))

suspend fun SupabaseController.updateYourWhy(yourWhy: String): SupabaseFunctionError? =
    updateUser(cols(SupabaseUserProps.YOUR_WHY to JsonPrimitive(yourWhy)))

/** Replace the user's if-then slip plans (iOS `updateTriggerResponses`). */
suspend fun SupabaseController.updateTriggerResponses(responses: List<SupabaseTriggerResponse>): SupabaseFunctionError? =
    updateUser(
        cols(
            SupabaseUserProps.TRIGGER_RESPONSES to decoder.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(SupabaseTriggerResponse.serializer()),
                responses,
            ),
        ),
    )

// Normative feedback

@Serializable
private data class NormativeFeedbackParams(
    val userId: String,
    val lastSmokedDate: String,
    val startDate: String,
)

/**
 * Generate the onboarding normative-feedback payload (iOS `getNormativeFeedback`
 * → the `program_generate_normative_feedback` EDGE function; requires
 * `supabase functions serve` when running locally). Dates travel in the
 * assessment "yyyy-MM-dd" format.
 */
suspend fun SupabaseController.getNormativeFeedback(
    userID: String,
    lastSmoked: Instant,
    startDate: Instant,
): Pair<org.clear30.data.model.ProgramNormativeFeedback?, SupabaseFunctionError?> =
    callEdgeFunction(
        SupabaseEdgeFunction("program_generate_normative_feedback"),
        NormativeFeedbackParams(
            userId = userID,
            lastSmokedDate = org.clear30.data.model.PlainDate.from(lastSmoked).dateString,
            startDate = org.clear30.data.model.PlainDate.from(startDate).dateString,
        ),
        org.clear30.data.model.ProgramNormativeFeedback::class.java,
    )

suspend fun SupabaseController.updateUserMetadata(timezone: String, appVersion: String): SupabaseFunctionError? =
    updateUser(cols(
        SupabaseUserProps.TIMEZONE to JsonPrimitive(timezone),
        SupabaseUserProps.APP_VERSION to JsonPrimitive(appVersion),
    ))

// Notifications / Pop-ins

suspend fun SupabaseController.schedulePopInRequest(date: Instant): SupabaseFunctionError? =
    callFunction(SupabaseFunction.schedulePopInRequest, mapOf("scheduled_for" to date.toString()))

suspend fun SupabaseController.clearPopInRequest(): SupabaseFunctionError? =
    callFunction(SupabaseFunction.clearPopInRequest)

/** Account deletion (Swift `deleteAccount`). Fires the `delete_user` RPC. */
suspend fun SupabaseController.deleteAccount(): SupabaseFunctionError? =
    callFunction(SupabaseFunction.deleteAccount)

// SMS (DEBUG short-circuits like the iOS #if DEBUG paths)

@Serializable
private data class ScheduleSMSParams(val message: String, val offset_minutes: Int)

suspend fun SupabaseController.scheduleSMS(message: String, offset: Int): SupabaseFunctionError? {
    if (BuildConfig.DEBUG) { println("💬 schedule SMS (+$offset min): ${message.lineSequence().first()}"); return null }
    return callFunction(SupabaseFunction.scheduleSms, mapOf("sms_data" to ScheduleSMSParams(message, offset)))
}

suspend fun SupabaseController.clearSMS(): SupabaseFunctionError? {
    if (BuildConfig.DEBUG) { println("🆑 Clearing all SMS"); return null }
    return callFunction(SupabaseFunction.clearSms)
}

suspend fun SupabaseController.clearSMS(message: String): SupabaseFunctionError? {
    if (BuildConfig.DEBUG) { println("🚮 Unscheduling: ${message.lineSequence().first()}"); return null }
    return callFunction(SupabaseFunction.clearSms, mapOf("message" to message))
}

/** Onboarding feedback params (Swift `SubmitFeedbackParams`). */
@Serializable
data class SubmitFeedbackParams(
    val rating: Int,
    val comment: String? = null,
)

/** Submit the onboarding "how are we doing?" rating (Swift `submitFeedback`). */
suspend fun SupabaseController.submitFeedback(params: SubmitFeedbackParams): SupabaseFunctionError? =
    callFunction(SupabaseFunction.submitFeedback, params)

/**
 * Free-form feedback (Feedback Monster). Ported 1:1 from iOS `submitFeedback`: a
 * DIRECT insert into `comms.feedback` (user_id, feedback, type). The DB insert is
 * what the backend's Slack trigger watches — so this is what makes feedback show
 * up in the connected channel.
 *
 * The previous Android path invoked a `submit_feedback` EDGE FUNCTION that does
 * not exist in the project (the backend only has `feedback_handle_webhook`), so
 * every submission 404'd and was silently swallowed — which is why nothing ever
 * reached the table or Slack. `comms.feedback` grants insert to anon/authenticated.
 */
@Serializable
private data class FeedbackInsert(
    val user_id: String,
    val feedback: String,
    val type: String = "text",
    // NOT NULL on prod with a now() default; send it explicitly so the insert can't
    // fail on an env where the default migration isn't applied.
    val timestamp: String = org.clear30.util.now().toString(),
)

suspend fun SupabaseController.submitTextFeedback(userID: String, feedback: String, type: String = "text"): SupabaseFunctionError? = runCatching {
    client.postgrest.from("comms", "feedback").insert(FeedbackInsert(userID, feedback, type))
}.onFailure { android.util.Log.w("SupabaseFeedback", "comms.feedback insert failed: ${it.message}") }
    .fold({ null }, { it.toError() })

// Note on "Feedback Monster sync": the `gaveFeedback` flag stays device-local, on
// purpose. `comms.feedback` grants SELECT to admins only (RLS "Admin only"), so a
// client cannot read its own rows to restore the "full" monster on a new device,
// and iOS itself keeps the flag in SwiftData only (no `users.gave_feedback`
// column). The actual sync — the submission reaching the backend + Slack — is
// [submitTextFeedback] above (direct insert; INSERT policy is `true` → the
// `feedback_notify` trigger posts to Slack). True cross-device restore would need
// an additive `users.gave_feedback` column on the shared backend (see notes).
