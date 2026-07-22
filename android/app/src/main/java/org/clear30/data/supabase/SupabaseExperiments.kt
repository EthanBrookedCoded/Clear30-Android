package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.ExperimentConfig
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.ExperimentOption
import org.clear30.data.model.UserInfo

/**
 * Wire format for the `experiments.get_user_experiments(p_user_id text)` RPC.
 * The function `RETURNS TABLE(experiment_id text, variant text, payload jsonb)`
 * — one row per assigned experiment. `experiment_id` (e.g. `"new-onboarding"`)
 * doubles as both the flag key we look up and the exposure attribution id
 * (matching iOS `ExperimentControllerAbstracted.ExperimentRow`); the variant is
 * `"show"` / `"hide"` / `"off"` / `"show-foo"`.
 */
@Serializable
data class SupabaseExperimentEntry(
    @SerialName("experiment_id") val experimentId: String,
    val variant: String,
    val payload: JsonObject? = null,
)

/**
 * RPC: `experiments.get_user_experiments(p_user_id)`. Lives in the **experiments**
 * schema and REQUIRES `p_user_id` (the loggingID) — iOS
 * `ExperimentControllerAbstracted.swift:99-103`. Calling it on the default
 * public schema with no params (the previous bug) threw on every load, so the
 * whole DB-driven experiment set silently resolved to fallbacks on Android.
 */
suspend fun SupabaseController.getExperiments(loggingID: String): List<SupabaseExperimentEntry> = runCatching {
    client.postgrest.rpc(
        SupabaseFunction.getSupabaseExperiments.rawValue,
        buildJsonObject { put("p_user_id", loggingID) },
    ) { schema = "experiments" }.decodeList<SupabaseExperimentEntry>()
}.getOrDefault(emptyList())

/**
 * Refresh the experiment table from the backend, mirroring
 * `ExperimentController.refreshExperiments` (Swift). For each returned entry:
 *  1. write the (key → ExperimentConfig) into the controller's local map
 *  2. log a single exposure event so attribution can attribute the user to the
 *     variant they're now running (`Logger.logExperimentExposure`)
 *  3. persist the controller so the assignment survives a relaunch with no
 *     network — the local map is consulted via `showFeature(...)` / `getFeature(...)`.
 */
suspend fun refreshExperiments(controller: ExperimentController, userInfo: UserInfo) {
    // iOS bails on an empty loggingID (ExperimentControllerAbstracted.swift:87).
    if (userInfo.loggingID.isEmpty()) return
    val entries = SupabaseController.getExperiments(userInfo.loggingID)
    if (entries.isEmpty()) return

    entries.forEach { entry ->
        val key = ExperimentKey.from(entry.experimentId)
        val option = ExperimentOption.from(entry.variant)
        // ExperimentConfig.payload is Map<String, JsonElement?> (matching iOS
        // `[String: AnyCodable?]`); a server-returned JsonObject only has
        // non-null JsonElement values, so widen explicitly into the nullable map.
        val payload: Map<String, JsonElement?> =
            buildMap { entry.payload?.forEach { (k, v) -> put(k, v) } }
        val newConfig = ExperimentConfig(option, payload)

        // Match iOS (:120-138): Supabase wins on key collision, but only write +
        // log exposure when the config actually changed, and only log exposure on
        // a NEW variant assignment (not a bare payload change) — otherwise every
        // app-load refresh would re-fire an exposure event for the same variant.
        val existing = controller.experiments[key]
        if (existing == newConfig) return@forEach
        val isNewVariant = existing == null || existing.option != option
        controller.experiments[key] = newConfig
        if (isNewVariant) {
            Logger.logEvent(
                loggingID = userInfo.loggingID,
                event = LogEventType.exposure,
                extraData = mapOf(
                    LogEventExtraDataType.FLAG_KEY to key.rawValue,
                    LogEventExtraDataType.VARIANT to option.rawValue,
                    LogEventExtraDataType.EXPERIMENT_KEY to entry.experimentId,
                ),
            )
        }
    }
    Clear30Store.save(controller)
}
