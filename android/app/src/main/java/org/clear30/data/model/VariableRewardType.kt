package org.clear30.data.model

import kotlinx.serialization.Serializable

/**
 * VariableRewardType — ported from CheckInRewardVariableType.swift (data-layer
 * subset). The enum cases + exclusivity flags are needed by ProgramDayInfo; the
 * view-specific members in that Swift file are merged in during the views
 * segment, so the Swift file is retained for now.
 */
@Serializable
enum class VariableRewardType {
    // General (both categories)
    weeklyDaysCheckedIn,
    weeklyDaysSober,
    customWeeklyDaysCompleted,
    customWeeklyDayStreak,
    breakProgress,
    reminderOfWhy,
    personalBest,

    // Didn't-smoke exclusive
    achievementDaysWithoutWeed,
    achievementHoursWithoutWeed,
    milestoneCountdown,
    calendarFillAnimation,
    streak,
    decreaseInUse,

    // Smoked exclusive
    growthProgress,
    motivationalQuote;

    val isExclusiveForDidntSmoke: Boolean
        get() = this in setOf(
            achievementDaysWithoutWeed, achievementHoursWithoutWeed, milestoneCountdown,
            calendarFillAnimation, streak, decreaseInUse,
        )

    val isExclusiveForSmoked: Boolean
        get() = this in setOf(growthProgress, motivationalQuote)
}
