package org.clear30.data.supabase

import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.ProgramDayInfo

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
    val ser = MapSerializer(PlainDate.serializer(), ProgramDayInfo.serializer())
    return updateColumn(SupabaseUserProps.DAY_INFO, decoder.encodeToJsonElement(ser, dayInfo))
}

suspend fun SupabaseController.updateContentInfo(contentInfo: Map<PlainDate, ContentInfo>): SupabaseController.SupabaseFunctionError? {
    val ser = MapSerializer(PlainDate.serializer(), ContentInfo.serializer())
    return updateColumn(SupabaseUserProps.CONTENT_INFO, decoder.encodeToJsonElement(ser, contentInfo))
}

suspend fun SupabaseController.updateProgramBreaks(breaks: List<ProgramBreak>): SupabaseController.SupabaseFunctionError? {
    val ser = ListSerializer(ProgramBreak.serializer())
    return updateColumn(SupabaseUserProps.PROGRAM_BREAKS, decoder.encodeToJsonElement(ser, breaks))
}

suspend fun SupabaseController.updateCustomCheckIns(customCheckIns: List<CustomCheckIn>): SupabaseController.SupabaseFunctionError? {
    val ser = ListSerializer(CustomCheckIn.serializer())
    return updateColumn(SupabaseUserProps.CUSTOM_CHECK_INS, decoder.encodeToJsonElement(ser, customCheckIns))
}

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
    return firstErr
}
