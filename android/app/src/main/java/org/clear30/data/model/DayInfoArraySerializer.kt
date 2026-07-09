package org.clear30.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializes the user's `day_info` as the FLAT POSITIONAL ARRAY the backend (and
 * iOS) use: `[dateString, dayInfoObject, dateString, dayInfoObject, ...]`.
 *
 * Swift's JSONEncoder encodes a `[PlainDate: ProgramDayInfo]` dictionary — whose
 * key isn't String/Int — as exactly this alternating array, and `groups.get()`
 * reads it back with `jsonb_array_length()` + positional indexing
 * (`day_info->>(i*2)` / `day_info->(i*2+1)`). A plain `Map<PlainDate, …>` encoded
 * as a JSON OBJECT (because [PlainDateSerializer] is a STRING-kind serializer)
 * makes `get()` raise "cannot get array length of a non-array", which silently
 * empties the group and leaves the user stuck on the create/join screen.
 *
 * JSON-only: relies on the [JsonEncoder]/[JsonDecoder] the Supabase serializer uses.
 */
object DayInfoArraySerializer : KSerializer<Map<PlainDate, ProgramDayInfo>> {
    override val descriptor: SerialDescriptor = JsonArray.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Map<PlainDate, ProgramDayInfo>) {
        val jsonEncoder = encoder as JsonEncoder
        val arr = buildJsonArray {
            value.forEach { (date, info) ->
                add(JsonPrimitive(date.dateString))
                add(jsonEncoder.json.encodeToJsonElement(ProgramDayInfo.serializer(), info))
            }
        }
        jsonEncoder.encodeJsonElement(arr)
    }

    override fun deserialize(decoder: Decoder): Map<PlainDate, ProgramDayInfo> {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val out = LinkedHashMap<PlainDate, ProgramDayInfo>()
        when (element) {
            is JsonArray -> {
                var i = 0
                while (i + 1 < element.size) {
                    val date = PlainDate.parse(element[i].jsonPrimitive.content)
                    out[date] = jsonDecoder.json.decodeFromJsonElement(ProgramDayInfo.serializer(), element[i + 1])
                    i += 2
                }
            }
            // Legacy rows exist as a `{ "YYYY-MM-DD": {...} }` object — read them
            // too (we always WRITE the array form).
            is JsonObject -> element.forEach { (key, value) ->
                out[PlainDate.parse(key)] =
                    jsonDecoder.json.decodeFromJsonElement(ProgramDayInfo.serializer(), value)
            }
            else -> Unit
        }
        return out
    }
}
