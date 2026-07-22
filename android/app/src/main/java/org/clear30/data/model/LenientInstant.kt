package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Instant (de)serializer tolerant of the OFFSET-LESS datetime strings iOS writes
 * to the backend. Swift encodes some `Date`s as `2026-07-21T04:00:00.000` — no
 * `Z`/offset — where the wall-clock IS already UTC (e.g. local-midnight in
 * America/New_York = `04:00` UTC). kotlinx `Instant.parse` REQUIRES an offset, so
 * the strict default throws on those values; because they sit on `program_breaks`
 * / `day_info` (check-in `timestamp`) / `last_smoked`, ONE such field fails the
 * ENTIRE `SupabaseUserData` decode → `fetchUserData` returns null → a
 * cross-platform restore (iOS row → Android) reports "No account found."
 *
 * This accepts BOTH forms: a normal offset-ful ISO-8601 instant, and a bare
 * `YYYY-MM-DDTHH:MM:SS(.fff)` which it reads as UTC (matching how the value
 * round-trips on iOS). It always WRITES offset-ful (`Instant.toString()`), so
 * Android-written rows stay iOS-readable.
 */
object LenientInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = parse(decoder.decodeString())

    fun parse(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrNull()
            // Offset-less "local" datetime — Swift wrote the UTC wall-clock without a `Z`.
            ?: runCatching { LocalDateTime.parse(raw).toInstant(TimeZone.UTC) }.getOrNull()
            // Rethrow the original strict error so a genuinely bad value is still surfaced.
            ?: Instant.parse(raw)
}
