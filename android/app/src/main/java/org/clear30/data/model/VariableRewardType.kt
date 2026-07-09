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

    /** General rewards that get a weight boost on a smoked check-in (iOS `hasSmokedBoost`). */
    val hasSmokedBoost: Boolean
        get() = this in setOf(weeklyDaysCheckedIn, weeklyDaysSober, reminderOfWhy, personalBest)

    /** General rewards that get a weight boost on a sober check-in (iOS `hasDidntSmokeBoost`). */
    val hasDidntSmokeBoost: Boolean
        get() = this in setOf(weeklyDaysCheckedIn, weeklyDaysSober, reminderOfWhy, personalBest)

    /**
     * Selection weight (iOS `baseWeight`): exclusive rewards (100) outrank boosted
     * general rewards (60), which outrank standard general rewards (40).
     */
    val baseWeight: Double
        get() = when {
            isExclusiveForDidntSmoke || isExclusiveForSmoked -> 100.0
            hasSmokedBoost || hasDidntSmokeBoost -> 60.0
            else -> 40.0
        }

    /** Whether this reward type is echoed into the calendar day-info (iOS `shouldShowInCalendarDayInfo`). */
    val shouldShowInCalendarDayInfo: Boolean
        get() = this != calendarFillAnimation
}
