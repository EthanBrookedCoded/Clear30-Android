package org.clear30.data.supabase

import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.DayInfoArraySerializer
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.sorted

/**
 * Program-state sync — pushes the locally-mutated `Program` fields back into
 * the user's row in Supabase. Ports the column-writes that lived in
 * `SupabaseController` extensions on iOS (`updateDayInfo`, `updateContentInfo`,
 * `updateProgramBreaks`, `updateCustomCheckIns`, `updateLastSmoked`).
 *
 * Each writer encodes its value through the controller's lenient decoder using
 * the explicit serializer — `PlainDate` has a primitive STRING serializer, so
 * Map<PlainDate, *> serialises as a JSON object with date-string keys
 * (compatible with how the backend stores `day_info` / `content_info` jsonb).
 */

private suspend fun SupabaseController.updateColumn(column: String, value: JsonElement): SupabaseController.SupabaseFunctionError? =
    updateUser(JsonObject(mapOf(column to value)))

suspend fun SupabaseController.updateDayInfo(dayInfo: Map<PlainDate, ProgramDayInfo>): SupabaseController.SupabaseFunctionError? {
    // day_info is a positional array on the backend (iOS encodes the
    // [PlainDate: ProgramDayInfo] dict that way and groups.get() reads it with
    // jsonb_array_length). Encoding it as a JSON object broke groups; use the
    // array serializer so check-in syncs write — and repair — the correct shape.
    return updateColumn(SupabaseUserProps.DAY_INFO, decoder.encodeToJsonElement(DayInfoArraySerializer, dayInfo))
}

/**
 * content_info is written in WIRE form — `{ date: { message_ids, progress } }` —
 * never the full local ContentInfo (message bodies/stage stay device-side; iOS
 * restores bodies from the ids). Writing the domain model here would break the
 * cross-device contract with iOS.
 */
suspend fun SupabaseController.updateContentInfo(contentInfo: Map<PlainDate, ContentInfo>): SupabaseController.SupabaseFunctionError? {
    val wire = contentInfo.entries.associate { (date, info) ->
        date.dateString to SupabaseContentInfo(
            message_ids = info.messages.sorted.mapNotNull { it.messageID },
            progress = info.progress,
        )
    }
    val ser = MapSerializer(String.serializer(), SupabaseContentInfo.serializer())
    return updateColumn(SupabaseUserProps.CONTENT_INFO, decoder.encodeToJsonElement(ser, wire))
}

/** program_breaks in wire form (`type` = "clear30"/"clear30-start-soon", snake_case). */
suspend fun SupabaseController.updateProgramBreaks(breaks: List<ProgramBreak>): SupabaseController.SupabaseFunctionError? {
    val ser = ListSerializer(SupabaseProgramBreak.serializer())
    return updateColumn(SupabaseUserProps.PROGRAM_BREAKS, decoder.encodeToJsonElement(ser, breaks.map { it.toData() }))
}

suspend fun SupabaseController.updateCustomCheckIns(customCheckIns: List<CustomCheckIn>): SupabaseController.SupabaseFunctionError? {
    val ser = ListSerializer(SupabaseCustomCheckIn.serializer())
    return updateColumn(SupabaseUserProps.CUSTOM_CHECK_INS, decoder.encodeToJsonElement(ser, customCheckIns.map { it.toData() }))
}

/** timezone + app_version metadata (iOS updateUserMetadata, part of pushProgramData). */
suspend fun SupabaseController.updateUserMetadata(): SupabaseController.SupabaseFunctionError? =
    updateUser(
        JsonObject(
            mapOf(
                SupabaseUserProps.TIMEZONE to JsonPrimitive(kotlinx.datetime.TimeZone.currentSystemDefault().id),
                SupabaseUserProps.APP_VERSION to JsonPrimitive(org.clear30.BuildConfig.VERSION_NAME),
            ),
        ),
    )

suspend fun SupabaseController.updateLastSmoked(lastSmoked: Instant): SupabaseController.SupabaseFunctionError? =
    updateColumn(SupabaseUserProps.LAST_SMOKED, JsonPrimitive(lastSmoked.toString()))

suspend fun SupabaseController.updateLatestCheckInMethod(method: CheckInMethod?): SupabaseController.SupabaseFunctionError? {
    val value: JsonElement = if (method == null) JsonNull
        else decoder.encodeToJsonElement(CheckInMethod.serializer(), method)
    // No dedicated column was confirmed yet; piggyback on the user table only
    // if/when the backend exposes one. Stub: write through updateUser as a
    // best-effort but expect the call to no-op for unknown columns.
    return updateColumn("latest_check_in_method", value)
}

/**
 * One-shot push of the live program state — call from the foreground/sync path
 * or right after a write that mutates several fields (e.g. assessment submission,
 * post-onboarding setup). Reports the first error encountered (others continue);
 * the loop tries each write so a transient failure in one column doesn't block
 * the rest.
 */
suspend fun SupabaseController.syncProgramState(program: Program): SupabaseController.SupabaseFunctionError? {
    var firstErr: SupabaseController.SupabaseFunctionError? = null
    fun rec(e: SupabaseController.SupabaseFunctionError?) { if (firstErr == null) firstErr = e }
    rec(updateDayInfo(program.dayInfo))
    rec(updateContentInfo(program.contentInfo))
    rec(updateProgramBreaks(program.breaks))
    rec(updateCustomCheckIns(program.customCheckIns))
    rec(updateLastSmoked(program.lastSmoked))
    rec(updateLatestCheckInMethod(program.latestCheckInMethod))
    rec(updateUserMetadata())
    return firstErr
}
