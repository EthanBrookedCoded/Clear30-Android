package org.clear30.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Date helpers ported from the Swift `Date` extensions used across the app
 * (isSameDay, isToday, adding(days:/seconds:), daysTo). iOS `Date` -> kotlinx
 * [Instant]; all calendar math uses the device's [TimeZone].
 */
private val tz get() = TimeZone.currentSystemDefault()

fun now(): Instant = Clock.System.now()

fun Instant.isSameDay(other: Instant): Boolean {
    val a = toLocalDateTime(tz).date
    val b = other.toLocalDateTime(tz).date
    return a == b
}

val Instant.isToday: Boolean get() = isSameDay(now())

fun Instant.adding(days: Int = 0, seconds: Long = 0): Instant {
    var result = this
    if (days != 0) result = result.plus(days, DateTimeUnit.DAY, tz)
    if (seconds != 0L) result = result.plus(seconds, DateTimeUnit.SECOND)
    return result
}

/** Whole days from this instant to [other] (calendar days, signed). */
fun Instant.daysTo(other: Instant): Int =
    toLocalDateTime(tz).date.daysUntil(other.toLocalDateTime(tz).date)

/** Start of this instant's calendar day (Swift `Date.justDay`). */
val Instant.justDay: Instant get() = toLocalDateTime(tz).date.atStartOfDayIn(tz)

/** Round to the nearest hour (Swift `Date.nearestHour`). */
val Instant.nearestHour: Instant
    get() {
        val hourMs = 3_600_000L
        val ms = toEpochMilliseconds()
        return Instant.fromEpochMilliseconds(((ms + hourMs / 2) / hourMs) * hourMs)
    }
