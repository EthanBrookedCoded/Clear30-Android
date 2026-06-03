package org.clear30.data.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.until
import kotlinx.serialization.Serializable

/** Decomposed duration — ported from `TimeComponents` (DateSpan.swift). */
data class TimeComponents(
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/**
 * DateSpan — ported from DateSpan.swift. Breaks the interval between two
 * instants into months/days/hours/minutes/seconds and renders the two largest
 * units (e.g. "2 Months 4 Days").
 */
@Serializable
data class DateSpan(
    var startDate: Instant,
    var endDate: Instant,
) {
    private val tz get() = TimeZone.currentSystemDefault()

    val components: TimeComponents
        get() {
            var cursor = startDate
            val months = cursor.until(endDate, DateTimeUnit.MONTH, tz).toInt()
            cursor = cursor.plusUnit(months, DateTimeUnit.MONTH)
            val days = cursor.until(endDate, DateTimeUnit.DAY, tz).toInt()
            cursor = cursor.plusUnit(days, DateTimeUnit.DAY)
            val hours = cursor.until(endDate, DateTimeUnit.HOUR, tz).toInt()
            cursor = cursor.plusUnit(hours, DateTimeUnit.HOUR)
            val minutes = cursor.until(endDate, DateTimeUnit.MINUTE, tz).toInt()
            cursor = cursor.plusUnit(minutes, DateTimeUnit.MINUTE)
            val seconds = cursor.until(endDate, DateTimeUnit.SECOND, tz).toInt()
            return TimeComponents(months, days, hours, minutes, seconds)
        }

    val textRepresentation: String
        get() {
            val c = components
            val nonZero = buildList {
                if (c.months > 0) add(c.months to if (c.months == 1) "Month" else "Months")
                if (c.days > 0) add(c.days to if (c.days == 1) "Day" else "Days")
                if (c.hours > 0) add(c.hours to if (c.hours == 1) "Hour" else "Hours")
                if (c.minutes > 0) add(c.minutes to if (c.minutes == 1) "Minute" else "Minutes")
                if (c.seconds > 0) add(c.seconds to if (c.seconds == 1) "Second" else "Seconds")
            }
            return nonZero.take(2).joinToString(" ") { "${it.first} ${it.second}" }
        }

    val totalSeconds: Int
        get() {
            val c = components
            return c.months * 30 * 24 * 60 * 60 +
                c.days * 24 * 60 * 60 +
                c.hours * 60 * 60 +
                c.minutes * 60 +
                c.seconds
        }

    private fun Instant.plusUnit(value: Int, unit: DateTimeUnit): Instant =
        plus(value.toLong(), unit, tz)
}
