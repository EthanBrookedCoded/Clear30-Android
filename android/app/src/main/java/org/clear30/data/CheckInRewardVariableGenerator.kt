package org.clear30.data

import kotlin.math.abs
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.UserInfo
import org.clear30.data.model.VariableRewardType
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.now

/** The data payload of a generated variable reward (iOS `VariableRewardData`, subset). */
sealed interface VariableRewardData {
    data class DaysWithoutWeed(val days: Int, val affirmation: String) : VariableRewardData
    data class Streak(val days: Int, val affirmation: String) : VariableRewardData
    data class MilestoneCountdown(val milestoneHours: Int, val hoursRemaining: Int, val affirmation: String) : VariableRewardData
    data class DecreaseInUse(val percentage: Int) : VariableRewardData
    data class BreakProgress(val current: Int, val max: Int, val name: String, val affirmation: String) : VariableRewardData
    data class PersonalBest(val currentDays: Int, val bestDays: Int, val affirmation: String) : VariableRewardData
    data class WeeklyDays(val count: Int, val sober: Boolean, val affirmation: String) : VariableRewardData
    data class ReminderOfWhy(val whys: List<String>, val affirmation: String) : VariableRewardData
    data class MotivationalQuote(val emoji: String, val quote: String) : VariableRewardData
    data class Growth(val percent: Int, val title: String, val subtitle: String) : VariableRewardData
}

/** A weighted variable-reward candidate (iOS `VariableReward`). */
data class VariableReward(val type: VariableRewardType, val weight: Double, val data: VariableRewardData)

/**
 * CheckInRewardVariableGenerator — ported (engine + a faithful subset of types) from
 * CheckInRewardVariableGenerator.swift. Builds the day's candidate rewards based on
 * smoke status, filters the previous day's type + excluded types, then does the same
 * seed-weighted random selection iOS uses, and stamps the chosen type onto the day's
 * `variableRewardType` (which the calendar reads).
 *
 * Faithful to iOS for: selection math, weights/boosts, prev-type filtering, and the
 * affirmation/quote copy. The exhaustive per-type proximity weighting and the custom-
 * check-in reward variants are approximated/omitted; the full animated reward UI is a
 * separate view port.
 */
class CheckInRewardVariableGenerator(
    private val userInfo: UserInfo,
    private val program: Program,
) {
    // Achievement thresholds in hours (iOS `achievementThresholds`).
    private val achievementThresholds = listOf(1, 3, 6, 12, 24, 48, 72, 120, 168, 240, 336, 360, 480, 504, 600, 720, 960, 1200, 1440, 1680, 1920, 2160, 2400)

    private val motivationalQuotes = listOf(
        "🌅" to "today's choice doesn't define tomorrow's potential. Your next move matters more.",
        "⏭️" to "you can't change what happened, but you own what happens next.",
        "🎭" to "today was practice for tomorrow. Every rep counts.",
        "🧠" to "your brain is already learning from today.",
        "🏠" to "you're still building the life you want, one day at a time.",
        "🔥" to "the person you're becoming is still in there. Feel them deep down.",
        "🌱" to "change is messy... that's how you know it's real.",
        "⚡" to "your future self is rooting for you right now.",
        "💪" to "the strongest part of you showed up today by checking in.",
        "🎯" to "you're not your habits... you're the person choosing what to do about them.",
    )
    private val personalBestAffirmations = listOf(
        "Getting closer! 🎯", "You're building back! 💪", "Progress in motion! ⚡",
        "Step by step! 👣", "Every minute counts! ⏰", "Building momentum! 🚀", "On your way back! ➡️",
    )
    private val growthMessages = listOf(
        "Progress isn't always linear." to "Keep building!",
        "Every step forward matters." to "Growth takes time!",
        "Your journey has ups and downs." to "That's normal!",
        "Healing isn't a straight line." to "You're still moving forward!",
    )
    private val encouraging = listOf(
        "Amazing work! ✨", "Great job! 💪", "So proud! 🎉", "You're unstoppable! 🚀",
        "Every step counts! 👟", "Keep shining! 🌟", "You've got this! 🏆", "Awesome progress! 🔥",
        "Building momentum! ⚡", "Crushing it! 🛠️", "Celebrate this! 💪",
    )

    fun generate(checkInDate: PlainDate): VariableReward? {
        val sober = program.dayInfo[checkInDate]?.sober ?: return null
        val seed = PlainDate.from(now()).seed
        val previousType = program.dayInfo[checkInDate.adding(days = -1)]?.variableRewardType

        val candidates = buildList {
            if (sober) {
                addAll(didntSmokeRewards(checkInDate, seed))
            } else {
                addAll(smokedRewards(seed))
            }
            addAll(generalRewards(checkInDate, seed))
        }.filterNotNull().filter {
            it.type != previousType && it.type != VariableRewardType.calendarFillAnimation
        }

        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.weight }
        if (totalWeight <= 0.0) return null

        // Same deterministic seed-weighted pick as iOS.
        val randomValue = (abs(seed) % (totalWeight * 100).toInt().coerceAtLeast(1)) / 100.0
        var cumulative = 0.0
        for (reward in candidates) {
            cumulative += reward.weight
            if (randomValue <= cumulative) {
                store(checkInDate, reward.type)
                return reward
            }
        }
        return candidates.first().also { store(checkInDate, it.type) }
    }

    private fun store(date: PlainDate, type: VariableRewardType) {
        val updated = (program.dayInfo[date] ?: ProgramDayInfo()).copy(variableRewardType = type)
        program.updateDayInfo(date, updated)
    }

    // MARK: - Didn't-smoke exclusive

    private fun didntSmokeRewards(date: PlainDate, seed: Int): List<VariableReward?> {
        val days = daysWithoutWeed()
        val hours = hoursWithoutWeed()
        val streak = soberStreak(date)
        return listOf(
            days.takeIf { it > 0 }?.let {
                VariableReward(VariableRewardType.achievementDaysWithoutWeed, VariableRewardType.achievementDaysWithoutWeed.baseWeight,
                    VariableRewardData.DaysWithoutWeed(it, pick(encouraging, seed)))
            },
            streak.takeIf { it > 0 }?.let {
                VariableReward(VariableRewardType.streak, VariableRewardType.streak.baseWeight + if (it >= 4) 25.0 else 0.0,
                    VariableRewardData.Streak(it, pick(encouraging, seed + 1)))
            },
            nextThreshold(hours)?.let { threshold ->
                VariableReward(VariableRewardType.milestoneCountdown, VariableRewardType.milestoneCountdown.baseWeight,
                    VariableRewardData.MilestoneCountdown(threshold, (threshold - hours).coerceAtLeast(0), pick(encouraging, seed + 2)))
            },
            decreasePercentage()?.takeIf { it > 0 }?.let {
                VariableReward(VariableRewardType.decreaseInUse, VariableRewardType.decreaseInUse.baseWeight + if (it >= 50) 35.0 else 15.0,
                    VariableRewardData.DecreaseInUse(it))
            },
        )
    }

    // MARK: - Smoked exclusive

    private fun smokedRewards(seed: Int): List<VariableReward?> {
        val (emoji, quote) = motivationalQuotes[abs(seed) % motivationalQuotes.size]
        val name = userInfo.name.ifBlank { "friend" }
        val (title, subtitle) = growthMessages[abs(seed + 1) % growthMessages.size]
        return listOf(
            VariableReward(VariableRewardType.motivationalQuote, VariableRewardType.motivationalQuote.baseWeight,
                VariableRewardData.MotivationalQuote(emoji, "$name, $quote")),
            VariableReward(VariableRewardType.growthProgress, VariableRewardType.growthProgress.baseWeight,
                VariableRewardData.Growth(0, title, subtitle)),
        )
    }

    // MARK: - General (both)

    private fun generalRewards(date: PlainDate, seed: Int): List<VariableReward?> {
        val weeklySober = weeklyCount(date, soberOnly = true)
        val weeklyCheckedIn = weeklyCount(date, soberOnly = false)
        val best = longestSoberStreak()
        val current = soberStreak(date)
        return listOf(
            weeklySober.takeIf { it > 0 }?.let {
                VariableReward(VariableRewardType.weeklyDaysSober, VariableRewardType.weeklyDaysSober.baseWeight,
                    VariableRewardData.WeeklyDays(it, sober = true, pick(encouraging, seed + 3)))
            },
            weeklyCheckedIn.takeIf { it > 0 }?.let {
                VariableReward(VariableRewardType.weeklyDaysCheckedIn, VariableRewardType.weeklyDaysCheckedIn.baseWeight,
                    VariableRewardData.WeeklyDays(it, sober = false, pick(encouraging, seed + 4)))
            },
            program.currentBreak?.takeIf { it.currentBreakDay > 0 }?.let { br ->
                VariableReward(VariableRewardType.breakProgress, VariableRewardType.breakProgress.baseWeight + if (br.currentBreakDay >= br.type.raw / 2) 20.0 else 0.0,
                    VariableRewardData.BreakProgress(br.currentBreakDay, br.type.raw, br.name, pick(encouraging, seed + 5)))
            },
            if (current in 1 until best) {
                VariableReward(VariableRewardType.personalBest, VariableRewardType.personalBest.baseWeight,
                    VariableRewardData.PersonalBest(current, best, pick(personalBestAffirmations, seed)))
            } else null,
            reasonsForBreak().takeIf { it.isNotEmpty() }?.let { whys ->
                VariableReward(VariableRewardType.reminderOfWhy, VariableRewardType.reminderOfWhy.baseWeight,
                    VariableRewardData.ReminderOfWhy(whys, pick(encouraging, seed + 6)))
            },
        )
    }

    // MARK: - Metrics

    private fun daysWithoutWeed(): Int = program.lastSmoked.daysTo(now()).coerceAtLeast(0)

    private fun hoursWithoutWeed(): Int =
        ((now().toEpochMilliseconds() - program.lastSmoked.toEpochMilliseconds()) / 3_600_000L).toInt().coerceAtLeast(0)

    private fun nextThreshold(hours: Int): Int? = achievementThresholds.firstOrNull { it > hours }

    private fun soberStreak(end: PlainDate): Int {
        var streak = 0
        var d = end
        if (program.dayInfo[d]?.sober != true) d = d.adding(days = -1)
        while (program.dayInfo[d]?.sober == true) { streak++; d = d.adding(days = -1) }
        return streak
    }

    private fun longestSoberStreak(): Int {
        val dates = program.dayInfo.keys.sorted()
        var best = 0
        var run = 0
        var prev: PlainDate? = null
        for (d in dates) {
            val sober = program.dayInfo[d]?.sober == true
            run = if (sober && prev != null && prev.adding(days = 1) == d) run + 1 else if (sober) 1 else 0
            if (run > best) best = run
            prev = d
        }
        return best
    }

    private fun weeklyCount(end: PlainDate, soberOnly: Boolean): Int =
        (0 until 7).count { i ->
            val di = program.dayInfo[end.adding(days = -i)]
            if (soberOnly) di?.sober == true else di != null
        }

    /** Reuse the same window logic as the REDUCTION achievement (7-day vs prior 7-day). */
    private fun decreasePercentage(): Int? {
        val today = PlainDate.from(now())
        fun freq(start: PlainDate, end: PlainDate): Double? {
            val entries = program.dayInfo.filterKeys { it >= start && it <= end }
            if (entries.isEmpty()) return null
            return entries.values.count { it.sober == false }.toDouble() / entries.size * 100.0
        }
        val period = freq(PlainDate.from(now().adding(days = -7)), today) ?: return null
        val baseline = freq(PlainDate.from(now().adding(days = -15)), PlainDate.from(now().adding(days = -8))) ?: return null
        if (baseline <= 0.0) return null
        return (((baseline - period) / baseline) * 100.0).coerceAtLeast(0.0).toInt()
    }

    private fun reasonsForBreak(): List<String> {
        val response = program.currentBreak?.getAssessmentResponse(AssessmentQuestionID.BREAK_REASON.raw) ?: return emptyList()
        val options = response.question.options
        return response.responses.mapNotNull { options.getOrNull(it) }
    }

    private fun pick(list: List<String>, seed: Int): String = list[abs(seed) % list.size]
}
