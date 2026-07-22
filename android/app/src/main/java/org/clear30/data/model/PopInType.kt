package org.clear30.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * PopInType — ported from PopIns.swift. Swift modeled this as an enum with one
 * associated value (`messageToSelf(sent:)`); Kotlin uses a sealed hierarchy with
 * data objects for the valueless cases.
 *
 * WIRE FORMAT (see [PopInTypeSerializer]): Swift's `Codable` enum-with-associated-
 * values encodes EXTERNALLY-TAGGED — `{"day0":{}}`, `{"messageToSelf":{"sent":true}}`
 * — and that is exactly what the backend `day_info` column stores. The default
 * kotlinx sealed-class form (`{"type":"day0"}`) does NOT round-trip with it, so a
 * cross-platform restore (iOS row → Android decode, or Android's own
 * `RestartedBreak` write → iOS decode) would throw and lose the ENTIRE day_info.
 * [PopInTypeSerializer] always writes the Swift form and reads BOTH forms.
 */
@Serializable(with = PopInTypeSerializer::class)
sealed class PopInType {
    @Serializable @SerialName("daysSober") data object DaysSober : PopInType()
    @Serializable @SerialName("daysCheckedIn") data object DaysCheckedIn : PopInType()
    @Serializable @SerialName("customCheckInDays") data object CustomCheckInDays : PopInType()
    @Serializable @SerialName("breakProgress") data object BreakProgress : PopInType()
    @Serializable @SerialName("symptomsOvercome") data object SymptomsOvercome : PopInType()
    @Serializable @SerialName("moneySaved") data object MoneySaved : PopInType()
    @Serializable @SerialName("timeSinceLastSmoke") data object TimeSinceLastSmoke : PopInType()
    @Serializable @SerialName("streak") data object Streak : PopInType()
    @Serializable @SerialName("customCheckInStreak") data object CustomCheckInStreak : PopInType()
    @Serializable @SerialName("messages") data object Messages : PopInType()
    @Serializable @SerialName("meditations") data object Meditations : PopInType()
    @Serializable @SerialName("journals") data object Journals : PopInType()
    @Serializable @SerialName("claireMessages") data object ClaireMessages : PopInType()
    @Serializable @SerialName("endOfWeekSummary") data object EndOfWeekSummary : PopInType()
    @Serializable @SerialName("threeDaySummary") data object ThreeDaySummary : PopInType()
    @Serializable @SerialName("day0") data object Day0 : PopInType()
    @Serializable @SerialName("day1") data object Day1 : PopInType()
    @Serializable @SerialName("day2") data object Day2 : PopInType()
    @Serializable @SerialName("checkInReminder") data object CheckInReminder : PopInType()
    @Serializable @SerialName("motivationalQuote") data object MotivationalQuote : PopInType()
    @Serializable @SerialName("restartedBreak") data object RestartedBreak : PopInType()

    @Serializable @SerialName("messageToSelf")
    data class MessageToSelf(val sent: Boolean) : PopInType()

    val messageToSelfSent: Boolean get() = (this as? MessageToSelf)?.sent ?: false
}

/**
 * Round-trips [PopInType] with the SWIFT `Codable` externally-tagged JSON the
 * backend `day_info` column uses (`{"day0":{}}`, `{"messageToSelf":{"sent":true}}`).
 *
 * - **Encode** ALWAYS emits the Swift form, so an Android-written `day_info`
 *   (e.g. `RestartedBreak`) decodes on iOS.
 * - **Decode** accepts BOTH the Swift form AND the legacy kotlinx internal-tag
 *   form (`{"type":"restartedBreak"}`) that earlier Android builds persisted
 *   locally, so no on-device history is lost after this change lands.
 * - An unrecognized case name throws (same as before) — the iOS and Android
 *   case sets are ported 1:1, so real data never hits that path.
 */
object PopInTypeSerializer : KSerializer<PopInType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PopInType")

    // case name (== Swift rawValue / the old @SerialName) → the valueless case.
    private val byName: Map<String, PopInType> = mapOf(
        "daysSober" to PopInType.DaysSober,
        "daysCheckedIn" to PopInType.DaysCheckedIn,
        "customCheckInDays" to PopInType.CustomCheckInDays,
        "breakProgress" to PopInType.BreakProgress,
        "symptomsOvercome" to PopInType.SymptomsOvercome,
        "moneySaved" to PopInType.MoneySaved,
        "timeSinceLastSmoke" to PopInType.TimeSinceLastSmoke,
        "streak" to PopInType.Streak,
        "customCheckInStreak" to PopInType.CustomCheckInStreak,
        "messages" to PopInType.Messages,
        "meditations" to PopInType.Meditations,
        "journals" to PopInType.Journals,
        "claireMessages" to PopInType.ClaireMessages,
        "endOfWeekSummary" to PopInType.EndOfWeekSummary,
        "threeDaySummary" to PopInType.ThreeDaySummary,
        "day0" to PopInType.Day0,
        "day1" to PopInType.Day1,
        "day2" to PopInType.Day2,
        "checkInReminder" to PopInType.CheckInReminder,
        "motivationalQuote" to PopInType.MotivationalQuote,
        "restartedBreak" to PopInType.RestartedBreak,
    )
    private val nameByCase: Map<PopInType, String> = byName.entries.associate { (k, v) -> v to k }

    override fun serialize(encoder: Encoder, value: PopInType) {
        val json = (encoder as? JsonEncoder) ?: throw SerializationException("PopInType is JSON-only")
        val element = when (value) {
            is PopInType.MessageToSelf ->
                JsonObject(mapOf("messageToSelf" to buildJsonObject { put("sent", value.sent) }))
            else -> {
                val name = nameByCase[value] ?: throw SerializationException("Unknown PopInType case: $value")
                JsonObject(mapOf(name to JsonObject(emptyMap())))
            }
        }
        json.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): PopInType {
        val json = (decoder as? JsonDecoder) ?: throw SerializationException("PopInType is JSON-only")
        val obj = json.decodeJsonElement().jsonObject

        // Legacy kotlinx internal-tag form: {"type":"restartedBreak"} /
        // {"type":"messageToSelf","sent":true}.
        (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.let { typeVal ->
            val name = typeVal.content
            if (name == "messageToSelf") {
                return PopInType.MessageToSelf((obj["sent"] as? JsonPrimitive)?.booleanOrNull ?: false)
            }
            return byName[name] ?: throw SerializationException("Unknown PopInType: $name")
        }

        // Swift externally-tagged form: {"restartedBreak":{}} /
        // {"messageToSelf":{"sent":true}}.
        val (key, payload) = obj.entries.firstOrNull()
            ?: throw SerializationException("Empty PopInType object")
        if (key == "messageToSelf") {
            val sent = (payload as? JsonObject)?.get("sent")
                ?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
            return PopInType.MessageToSelf(sent)
        }
        return byName[key] ?: throw SerializationException("Unknown PopInType: $key")
    }
}
