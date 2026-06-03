package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PopInType — ported from PopIns.swift. Swift modeled this as an enum with one
 * associated value (`messageToSelf(sent:)`); Kotlin uses a sealed hierarchy with
 * data objects for the valueless cases.
 */
@Serializable
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
