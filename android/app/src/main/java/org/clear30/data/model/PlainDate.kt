package org.clear30.data.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * PlainDate — ported from PlainDate.swift. A calendar (y/m/d) date used as the
 * key for Program's `dayInfo` / `contentInfo` maps.
 *
 * Serialized as the "yyyy-MM-dd" string (a STRING-kind serializer) so the maps
 * keyed by PlainDate encode as JSON objects rather than positional arrays.
 */
@Serializable(with = PlainDateSerializer::class)
data class PlainDate(val year: Int, val month: Int, val day: Int) : Comparable<PlainDate> {

    val dateString: String get() = "%04d-%02d-%02d".format(year, month, day)

    val seed: Int get() = year * 10000 + month * 100 + day

    override fun compareTo(other: PlainDate): Int = when {
        year != other.year -> year - other.year
        month != other.month -> month - other.month
        else -> day - other.day
    }

    fun isSameDay(other: PlainDate): Boolean = this == other

    private fun toLocalDate() = LocalDate(year, month, day)

    fun adding(
        months: Int = 0, weeks: Int = 0, days: Int = 0,
        hours: Int = 0, minutes: Int = 0, seconds: Int = 0,
    ): PlainDate {
        var date = toLocalDate().plus(DatePeriod(months = months, days = weeks * 7 + days))
        val subDaySeconds = hours * 3600L + minutes * 60L + seconds
        if (subDaySeconds != 0L) {
            val shifted = date.atStartOfDayIn(TZ)
                .plus(subDaySeconds, kotlinx.datetime.DateTimeUnit.SECOND)
            date = shifted.toLocalDateTime(TZ).date
        }
        return from(date)
    }

    fun daysTo(other: PlainDate): Int = toLocalDate().daysUntil(other.toLocalDate())

    /** Start of this calendar day as an Instant (Swift `dateObject`). */
    val dateObject: Instant get() = toLocalDate().atStartOfDayIn(TZ)

    companion object {
        private val TZ get() = TimeZone.currentSystemDefault()

        fun from(date: LocalDate) = PlainDate(date.year, date.monthNumber, date.dayOfMonth)

        fun from(instant: Instant): PlainDate = from(instant.toLocalDateTime(TZ).date)

        /** Parse "yyyy-MM-dd" (tolerates trailing chars on the day, like the iOS init). */
        fun parse(dateString: String): PlainDate {
            val parts = dateString.split("-")
            require(parts.size == 3) { "Invalid date format: $dateString" }
            return PlainDate(parts[0].toInt(), parts[1].toInt(), parts[2].take(2).toInt())
        }
    }
}

object PlainDateSerializer : KSerializer<PlainDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PlainDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PlainDate) =
        encoder.encodeString(value.dateString)

    override fun deserialize(decoder: Decoder): PlainDate =
        PlainDate.parse(decoder.decodeString())
}
