package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
 * Wire format for the `get_user_experiments` RPC. Each row carries the flag id
 * (e.g. `"new-onboarding"`), the assigned variant (`"show"` / `"hide"` /
 * `"off"` / `"show-foo"`), an optional payload of typed JSON values, and the
 * underlying experiment id (so we can attribute exposure to the right test
 * rather than the per-flag id).
 */
@Serializable
data class SupabaseExperimentEntry(
    @SerialName("flag_key") val flagKey: String,
    val variant: String,
    val payload: JsonObject? = null,
    @SerialName("experiment_key") val experimentKey: String? = null,
)

/** RPC: `get_user_experiments`. */
suspend fun SupabaseController.getExperiments(): List<SupabaseExperimentEntry> = runCatching {
    client.postgrest.rpc(SupabaseFunction.getSupabaseExperiments.rawValue)
        .decodeList<SupabaseExperimentEntry>()
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
    val entries = SupabaseController.getExperiments()
    if (entries.isEmpty()) return

    entries.forEach { entry ->
        val key = ExperimentKey.from(entry.flagKey)
        val option = ExperimentOption.from(entry.variant)
        // ExperimentConfig.payload is Map<String, JsonElement?> (matching iOS
        // `[String: AnyCodable?]`); a server-returned JsonObject only has
        // non-null JsonElement values, so widen explicitly into the nullable map.
        val payload: Map<String, JsonElement?> =
            buildMap { entry.payload?.forEach { (k, v) -> put(k, v) } }
        controller.experiments[key] = ExperimentConfig(option, payload)

        Logger.logEvent(
            loggingID = userInfo.loggingID,
            event = LogEventType.exposure,
            extraData = mapOf(
                LogEventExtraDataType.FLAG_KEY to key.rawValue,
                LogEventExtraDataType.VARIANT to option.rawValue,
                LogEventExtraDataType.EXPERIMENT_KEY to (entry.experimentKey ?: entry.flagKey),
            ),
        )
    }
    Clear30Store.save(controller)
}
