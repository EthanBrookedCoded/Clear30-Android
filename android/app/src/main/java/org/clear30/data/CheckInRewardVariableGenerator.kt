package org.clear30.data

import androidx.compose.ui.graphics.Brush
import kotlin.math.abs
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.BreakReasonType
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.DateSpan
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.UserInfo
import org.clear30.data.model.VariableRewardType
import org.clear30.util.adding
import org.clear30.util.isToday
import org.clear30.util.now

/**
 * Which flavor of the "days" reward card to render (iOS
 * `RewardDaysWithoutWeedLarge.DaysWithoutWeedLargeType`). Custom variants carry
 * the custom check-in's label + gradient so the card takes its color.
 */
sealed interface DaysWithoutWeedType {
    data object Sober : DaysWithoutWeedType
    data object CheckIns : DaysWithoutWeedType
    data object Streak : DaysWithoutWeedType
    data class Custom(val label: String, val gradient: Brush) : DaysWithoutWeedType
    data class CustomStreak(val label: String, val gradient: Brush) : DaysWithoutWeedType
}

/** The data payload of a generated variable reward (iOS `VariableRewardData`, full set). */
sealed interface VariableRewardData {
    data class DaysWithoutWeed(val days: Int, val type: DaysWithoutWeedType, val affirmation: String) : VariableRewardData
    data class BreakProgress(val current: Int, val max: Int, val name: String, val affirmation: String) : VariableRewardData
    data class PersonalBest(val current: DateSpan, val best: DateSpan, val affirmation: String) : VariableRewardData
    data class MilestoneCountdown(val milestone: Int, val unit: String, val timeRemaining: DateSpan, val affirmation: String) : VariableRewardData
    data class AchievementBasic(val number: Int, val label: String, val intro: String, val title: String) : VariableRewardData
    data class CalendarAnimation(val day: Int, val month: String) : VariableRewardData
    data class DecreaseInUse(val percentage: Int) : VariableRewardData
    data class Growth(val percent: Int, val title: String, val subtitle: String) : VariableRewardData
    data class MotivationalQuote(val emoji: String, val quote: String) : VariableRewardData
    data class ReminderOfWhy(val whys: List<String>, val affirmation: String) : VariableRewardData
}

/** A weighted variable-reward candidate (iOS `VariableReward`). */
data class VariableReward(val type: VariableRewardType, val weight: Double, val data: VariableRewardData)

/**
 * CheckInRewardVariableGenerator — full port of CheckInRewardVariableGenerator.swift.
 *
 * Builds the day's candidate rewards based on smoke status (didn't-smoke
 * exclusives + boosted generals, or smoked exclusives + boosted generals),
 * applies the day-0/1/2 rigging, filters the previous day's type and the
 * excluded types (`calendarFillAnimation`), then does the same seed-weighted
 * random selection iOS uses and stamps the chosen type onto the day's
 * `variableRewardType` (which the calendar reads).
 *
 * Divergence from iOS: the debug-only `overrideRewardType` path is not ported
 * (iOS ships it hardcoded to nil).
 */
class CheckInRewardVariableGenerator(
    private val userInfo: UserInfo,
    private val program: Program,
) {
    private val excludedRewardTypes = setOf(VariableRewardType.calendarFillAnimation)

    // Achievement and milestone thresholds in hours (iOS `achievementThresholds`).
    private val achievementThresholds = listOf(
        1, 3, 6, 12, 24, 48, 72, 120, 168, 240, 336, 360, 480, 504,
        600, 720, 960, 1200, 1440, 1680, 1920, 2160, 2400,
    )

    // Significant days for calendar animation (kept for parity; type is excluded).
    @Suppress("unused")
    private val significantDays = listOf(1, 3, 7, 14, 21, 30, 60, 90)

    // Motivational quotes for smoked rewards — SLIP framing. Only shown to
    // quit/break users in an active break (see `useSlipQuotes`). Voice: restorative
    // not permissive, forward/learning, no "fail", behavior-not-identity.
    private val motivationalQuotes = listOf(
        "💛" to "$CLIENT_NAME, you smoked and you still showed up here. That's the part that counts.",
        "🌱" to "$CLIENT_NAME, using doesn't undo your growth as a person. Be easy on yourself.",
        "🧠" to "$CLIENT_NAME, every time you use teaches you something when you embrace growth over punishment. You know a little more now than you did yesterday.",
        "🤝" to "$CLIENT_NAME, good days and hard ones, we're here for all of it.",
        "⏭️" to "$CLIENT_NAME, you can't change what happened, but the next move is yours.",
        "🛟" to "$CLIENT_NAME, this is exactly what we're here for. The door stays open.",
        "🎯" to "$CLIENT_NAME, you're not your habits. You're the person deciding what to do next.",
        "🌅" to "$CLIENT_NAME, today doesn't decide tomorrow. You get to choose what comes next.",
        "🪞" to "$CLIENT_NAME, using is something you did, not who you are.",
        "💪" to "$CLIENT_NAME, coming back in after using is the brave part, and you just did it.",
        "🧩" to "$CLIENT_NAME, now you know a little more about why you used. That's worth keeping.",
        "☁️" to "$CLIENT_NAME, be as kind to yourself right now as you'd be to a friend who slipped.",
        "🌿" to "$CLIENT_NAME, change isn't a straight line. The dips are part of how it works.",
        "⚓" to "$CLIENT_NAME, nothing you've built is gone. We pick it back up whenever you're ready.",
        "🔁" to "$CLIENT_NAME, you're not starting over. You're continuing, and we're with you.",
    )

    // Motivational quotes for smoked rewards — NEUTRAL / moderation framing.
    // Shown to moderating users (and anyone not in an active break); never frames
    // the smoke as a mistake.
    private val moderationMotivationalQuotes = listOf(
        "💛" to "$CLIENT_NAME, you showed up and checked in. That's how awareness starts to build.",
        "🌿" to "$CLIENT_NAME, these moments can help you understand your patterns. That is growth.",
        "📊" to "$CLIENT_NAME, every choice is information. What did you notice about using that can help you grow?",
        "🧠" to "$CLIENT_NAME, it may have been intentional or not. Either way, understanding what was going on leading up to it can give you more awareness around your choices.",
        "⚓" to "$CLIENT_NAME, you're learning your patterns in real time. That's how things start to shift when you stay open to it.",
        "🌱" to "$CLIENT_NAME, was that choice intentional, reactive, or somewhere in between?",
        "🛟" to "$CLIENT_NAME, whatever today looked like, it's something you can learn from for tomorrow.",
        "🔍" to "$CLIENT_NAME, this is a chance to get curious. What were you needing in that moment when you used?",
    )

    private val personalBestAffirmations = listOf(
        "Getting closer! 🎯", "You're building back! 💪", "Progress in motion! ⚡",
        "Step by step! 👣", "Every minute counts! ⏰", "Building momentum! 🚀", "On your way back! ➡️",
    )

    private val growthProgressMessages = listOf(
        "Progress isn't always linear." to "Keep building!",
        "Every step forward matters." to "Growth takes time!",
        "Your journey has ups and downs." to "That's normal!",
        "Healing isn't a straight line." to "You're still moving forward!",
    )

    private val encouragingMessages = listOf(
        "Amazing work! ✨", "Great job! 💪", "So proud! 🎉", "You're unstoppable! 🚀",
        "Every step counts! 👟", "Keep shining! 🌟", "You're glowing! 💫", "You've got this! 🏆",
        "Awesome progress! 🔥", "Real growth! 🌳", "Building momentum! ⚡", "Crushing it! 🛠️",
        "Effort adds up! 🧩", "Celebrate this! 💪",
    )

    fun generate(checkInDate: PlainDate): VariableReward? {
        // Step 1: Data collection & validation.
        val didntSmoke = program.dayInfo[checkInDate]?.sober ?: return null

        val seed = PlainDate.from(now()).seed
        val previousType = program.dayInfo[checkInDate.adding(days = -1)]?.variableRewardType

        // Rigging: force specific rewards on days 0-2 / first launch. (iOS returns
        // the rigged reward without stamping the day's variableRewardType — kept.)
        generateRiggedReward(checkInDate, didntSmoke)?.let { return it }

        // Step 2: candidates by smoke status.
        val candidates = buildList {
            if (didntSmoke) {
                addAll(didntSmokeRewards(checkInDate, seed))
                addAll(generalRewards(checkInDate, seed, boostForSmoked = false))
            } else {
                addAll(smokedRewards(checkInDate, seed))
                addAll(generalRewards(checkInDate, seed, boostForSmoked = true))
            }
        }.filterNotNull()
            // Step 3: filter previous day's type and excluded types.
            .filter { it.type != previousType && it.type !in excludedRewardTypes }

        // Step 4: weighted random selection (same math as iOS).
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.weight }
        if (totalWeight <= 0.0) return null

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

    private fun didntSmokeRewards(date: PlainDate, seed: Int): List<VariableReward?> = listOf(
        generateAchievementDaysWithoutWeed(),
        generateAchievementHoursWithoutWeed(),
        generateMilestoneCountdown(seed),
        generateCalendarFillAnimation(date),
        generateStreak(date, seed),
        generateDecreaseInUse(),
    )

    private fun generateAchievementDaysWithoutWeed(): VariableReward? {
        val totalHours = hoursWithoutWeed()
        for (threshold in achievementThresholds.reversed()) {
            if (totalHours >= threshold) {
                val days = threshold / 24
                if (days > 0) { // only day-based achievements
                    val weight = calculateAchievementWeight(
                        threshold = threshold,
                        current = totalHours,
                        baseWeight = VariableRewardType.achievementDaysWithoutWeed.baseWeight,
                    )
                    return VariableReward(
                        VariableRewardType.achievementDaysWithoutWeed,
                        weight,
                        VariableRewardData.AchievementBasic(
                            number = days,
                            label = if (days == 1) "day" else "days",
                            intro = "Wow ${userInfo.name}!!",
                            title = "Your weed free time hit more than $days full ${if (days == 1) "day" else "days"}!",
                        ),
                    )
                }
            }
        }
        return null
    }

    private fun generateAchievementHoursWithoutWeed(): VariableReward? {
        val totalHours = hoursWithoutWeed()
        if (totalHours >= 24) return null // day-based takes over after a day
        for (threshold in achievementThresholds.reversed()) {
            if (threshold < 24 && totalHours >= threshold) {
                val weight = calculateAchievementWeight(
                    threshold = threshold,
                    current = totalHours,
                    baseWeight = VariableRewardType.achievementHoursWithoutWeed.baseWeight,
                )
                return VariableReward(
                    VariableRewardType.achievementHoursWithoutWeed,
                    weight,
                    VariableRewardData.AchievementBasic(
                        number = threshold,
                        label = "hour${if (threshold == 1) "" else "s"}",
                        intro = "Wow ${userInfo.name}!!",
                        title = "Your weed free time hit more than $threshold full hour${if (threshold == 1) "" else "s"}!",
                    ),
                )
            }
        }
        return null
    }

    private fun generateMilestoneCountdown(seed: Int, rigged: Boolean = false): VariableReward? {
        val totalSeconds = secondsWithoutWeed()

        // Next threshold above the current weed-free time.
        val thresholdInSeconds = achievementThresholds.map { it * 3600L }
            .firstOrNull { it > totalSeconds } ?: return null
        val thresholdInHours = (thresholdInSeconds / 3600L).toInt()

        val secondsUntilMilestone = thresholdInSeconds - totalSeconds
        val hoursUntilMilestone = (secondsUntilMilestone / 3600L).toInt()

        // Only show if within 5 hours of the milestone (unless rigged).
        if (hoursUntilMilestone > 5 && !rigged) return null

        val weight = calculateProximityWeight(
            hoursUntil = hoursUntilMilestone,
            baseWeight = VariableRewardType.milestoneCountdown.baseWeight,
        )
        val (number, unit) = if (thresholdInHours < 24) thresholdInHours to "hour" else (thresholdInHours / 24) to "day"
        val affirmation = pick(encouragingMessages, seed)

        // Time remaining span: started when the weed-free clock started counting
        // toward this threshold, ends at the milestone (iOS DateSpan math).
        val nowInstant = now()
        val timeRemaining = DateSpan(
            startDate = nowInstant.adding(seconds = -thresholdInSeconds + secondsUntilMilestone),
            endDate = nowInstant.adding(seconds = secondsUntilMilestone),
        )

        return VariableReward(
            VariableRewardType.milestoneCountdown,
            weight,
            VariableRewardData.MilestoneCountdown(number, unit, timeRemaining, affirmation),
        )
    }

    private fun generateCalendarFillAnimation(date: PlainDate): VariableReward {
        // Generated for parity, but `excludedRewardTypes` filters it out (iOS does
        // the same — the type is on ice).
        val weight = VariableRewardType.calendarFillAnimation.baseWeight + 25.0
        return VariableReward(
            VariableRewardType.calendarFillAnimation,
            weight,
            VariableRewardData.CalendarAnimation(date.day, MONTH_NAMES[date.month - 1]),
        )
    }

    private fun generateStreak(date: PlainDate, seed: Int): VariableReward? {
        val streakDays = minOf(7, calculateCurrentStreak(date))
        if (streakDays < 2) return null
        val weight = VariableRewardType.streak.baseWeight + if (streakDays >= 4) 25.0 else 0.0
        return VariableReward(
            VariableRewardType.streak,
            weight,
            VariableRewardData.DaysWithoutWeed(streakDays, DaysWithoutWeedType.Streak, pick(encouragingMessages, seed)),
        )
    }

    private fun generateDecreaseInUse(): VariableReward? {
        val currentBreak = program.currentBreak ?: return null
        val decreasePercentage = getDeltaSmokingFrequency(currentBreak) ?: return null
        if (decreasePercentage <= 0) return null
        val weight = VariableRewardType.decreaseInUse.baseWeight + if (decreasePercentage >= 50) 35.0 else 15.0
        return VariableReward(
            VariableRewardType.decreaseInUse,
            weight,
            VariableRewardData.DecreaseInUse(decreasePercentage),
        )
    }

    // MARK: - Smoked exclusive

    private fun smokedRewards(date: PlainDate, seed: Int): List<VariableReward?> = listOf(
        generateGrowthProgress(date),
        generateMotivationalQuote(seed),
    )

    private fun generateGrowthProgress(date: PlainDate): VariableReward? {
        val programDay = program.currentBreak?.currentBreakDay ?: 0
        val programLength = program.currentBreak?.type?.raw ?: 0
        if (programLength <= 0) return null
        val growthPercent = programDay.toDouble() / programLength.toDouble()
        if (growthPercent <= 0.01) return null
        val weight = VariableRewardType.growthProgress.baseWeight + if (growthPercent >= 0.5) 20.0 else 0.0
        val (title, subtitle) = growthProgressMessages[abs(date.seed) % growthProgressMessages.size]
        return VariableReward(
            VariableRewardType.growthProgress,
            weight,
            VariableRewardData.Growth((growthPercent * 100).toInt(), title, subtitle),
        )
    }

    /** Slip framing is only appropriate when the smoke is a slip against an active break. */
    private val useSlipQuotes: Boolean get() = program.currentBreak != null

    private fun generateMotivationalQuote(seed: Int): VariableReward {
        val pool = if (useSlipQuotes) motivationalQuotes else moderationMotivationalQuotes
        val (emoji, quote) = pool[abs(seed) % pool.size]
        val name = userInfo.name.ifBlank { "friend" }
        return VariableReward(
            VariableRewardType.motivationalQuote,
            VariableRewardType.motivationalQuote.baseWeight,
            VariableRewardData.MotivationalQuote(emoji, quote.replace(CLIENT_NAME, name)),
        )
    }

    // MARK: - General (both categories)

    private fun generalRewards(date: PlainDate, seed: Int, boostForSmoked: Boolean): List<VariableReward?> =
        listOf(
            generateWeeklyDaysCheckedIn(date, seed, boostForSmoked),
            generateWeeklyDaysSober(date, seed, boostForSmoked),
        ) +
            generateCustomWeeklyDaysCompleted(date, seed) +
            generateCustomWeeklyDayStreak(date, seed) +
            listOf(
                generateBreakProgress(seed),
                generateReminderOfWhy(seed, boostForSmoked),
                generatePersonalBest(seed, boostForSmoked),
            )

    private fun generateWeeklyDaysCheckedIn(date: PlainDate, seed: Int, boostForSmoked: Boolean, rigged: Boolean = false): VariableReward? {
        val weekDays = weeklyDaysCheckedIn(date, days = 7)
        if (weekDays < 3 && !rigged) return null
        var weight = VariableRewardType.weeklyDaysCheckedIn.baseWeight
        if (boostForSmoked) weight *= 2.0
        return VariableReward(
            VariableRewardType.weeklyDaysCheckedIn,
            weight,
            VariableRewardData.DaysWithoutWeed(weekDays, DaysWithoutWeedType.CheckIns, pick(encouragingMessages, seed)),
        )
    }

    private fun generateWeeklyDaysSober(date: PlainDate, seed: Int, boostForSmoked: Boolean, rigged: Boolean = false): VariableReward? {
        val soberDays = weeklyDaysSober(date, days = 7)
        if (soberDays < 2 && !rigged) return null
        var weight = VariableRewardType.weeklyDaysSober.baseWeight
        if (boostForSmoked) weight *= 2.0
        return VariableReward(
            VariableRewardType.weeklyDaysSober,
            weight,
            VariableRewardData.DaysWithoutWeed(soberDays, DaysWithoutWeedType.Sober, pick(encouragingMessages, seed)),
        )
    }

    private fun generateCustomWeeklyDaysCompleted(date: PlainDate, seed: Int): List<VariableReward> =
        program.customCheckIns.mapNotNull { customCheckIn ->
            val completedDays = weeklyDaysCustomCheckIn(date, days = 7, checkIn = customCheckIn)
            if (completedDays < 2) return@mapNotNull null
            VariableReward(
                VariableRewardType.customWeeklyDaysCompleted,
                VariableRewardType.customWeeklyDaysCompleted.baseWeight,
                VariableRewardData.DaysWithoutWeed(
                    completedDays,
                    DaysWithoutWeedType.Custom(customCheckIn.completeOption.name, customCheckIn.gradient),
                    pick(encouragingMessages, seed),
                ),
            )
        }

    private fun generateCustomWeeklyDayStreak(date: PlainDate, seed: Int): List<VariableReward> =
        program.customCheckIns.mapNotNull { customCheckIn ->
            val streakDays = minOf(7, calculateCustomCheckInStreak(date, customCheckIn))
            if (streakDays < 3) return@mapNotNull null
            VariableReward(
                VariableRewardType.customWeeklyDayStreak,
                VariableRewardType.customWeeklyDayStreak.baseWeight + if (streakDays >= 4) 15.0 else 0.0,
                VariableRewardData.DaysWithoutWeed(
                    streakDays,
                    DaysWithoutWeedType.CustomStreak(customCheckIn.completeOption.name, customCheckIn.gradient),
                    pick(encouragingMessages, seed),
                ),
            )
        }

    private fun generateBreakProgress(seed: Int): VariableReward? {
        val currentBreak = program.currentBreak ?: return null
        val progress = currentBreak.currentBreakDay
        val maxDays = currentBreak.type.raw
        if (progress <= 0) return null
        val weight = VariableRewardType.breakProgress.baseWeight + if (progress >= maxDays / 2) 20.0 else 0.0
        return VariableReward(
            VariableRewardType.breakProgress,
            weight,
            VariableRewardData.BreakProgress(progress, maxDays, userInfo.name, pick(encouragingMessages, seed)),
        )
    }

    private fun generateReminderOfWhy(seed: Int, boostForSmoked: Boolean): VariableReward? {
        val whys = breakReasons().map { it.asNoun }
        if (whys.isEmpty()) return null
        var weight = VariableRewardType.reminderOfWhy.baseWeight
        if (boostForSmoked) weight *= 2.0
        return VariableReward(
            VariableRewardType.reminderOfWhy,
            weight,
            VariableRewardData.ReminderOfWhy(whys, pick(encouragingMessages, seed)),
        )
    }

    private fun generatePersonalBest(seed: Int, boostForSmoked: Boolean): VariableReward? {
        val spans = program.lastSmokedSpans?.takeIf { it.isNotEmpty() } ?: return null
        val longestSpan = spans.maxBy { it.totalSeconds }
        val currentSpan = DateSpan(startDate = program.lastSmoked, endDate = now())

        // Only show if the current run hasn't reached the personal best yet and
        // is at least 25% of the way there.
        if (currentSpan.totalSeconds >= longestSpan.totalSeconds) return null
        if (currentSpan.totalSeconds < (longestSpan.totalSeconds * 0.25).toInt()) return null

        var weight = VariableRewardType.personalBest.baseWeight
        if (boostForSmoked) weight *= 2.0
        return VariableReward(
            VariableRewardType.personalBest,
            weight,
            VariableRewardData.PersonalBest(currentSpan, longestSpan, pick(personalBestAffirmations, seed)),
        )
    }

    // MARK: - Rigged rewards (days 0-2 / first launch)

    private fun generateRiggedReward(checkInDate: PlainDate, didntSmoke: Boolean): VariableReward? {
        val programDay = program.currentBreak?.currentBreakDay ?: program.currentDay

        val todaySober = program.dayInfo[checkInDate]?.sober ?: false
        val yesterdaySober = program.dayInfo[checkInDate.adding(days = -1)]?.sober ?: false
        val twoDaysAgoSober = program.dayInfo[checkInDate.adding(days = -2)]?.sober ?: false

        val isFirstLaunch = userInfo.firstAppOpen.isToday
        return when {
            // Day 0 (or first app launch)
            programDay == 0 || isFirstLaunch -> {
                if (todaySober) generateWeeklyDaysSober(checkInDate, checkInDate.seed, boostForSmoked = false, rigged = true)
                else generateWeeklyDaysCheckedIn(checkInDate, checkInDate.seed, boostForSmoked = false, rigged = true)
            }
            // Day 1
            programDay == 1 -> {
                if (todaySober) generateMilestoneCountdown(checkInDate.seed, rigged = true)
                else generateMotivationalQuote(checkInDate.seed)
            }
            // Day 2
            programDay == 2 -> {
                if (todaySober) {
                    when {
                        yesterdaySober ->
                            generateAchievementDaysWithoutWeed() ?: generateStreak(checkInDate, checkInDate.seed)
                        twoDaysAgoSober ->
                            generateDecreaseInUse() ?: generateMilestoneCountdown(checkInDate.seed)
                        else -> generateMilestoneCountdown(checkInDate.seed)
                    }
                } else {
                    generateDecreaseInUse()
                        ?: if (listOf(todaySober, yesterdaySober, twoDaysAgoSober).count { it } >= 2) {
                            generateWeeklyDaysSober(checkInDate, checkInDate.seed, boostForSmoked = false, rigged = true)
                        } else {
                            generateWeeklyDaysCheckedIn(checkInDate, checkInDate.seed, boostForSmoked = false, rigged = true)
                        }
                }
            }
            else -> null
        }
    }

    // MARK: - Helper functions

    private fun calculateAchievementWeight(threshold: Int, current: Int, baseWeight: Double): Double {
        var weight = baseWeight
        // Within 24 hours of the achievement.
        if (current in threshold until threshold + 24) weight += 50.0
        // Major milestone bonus — culturally-significant thresholds (1d/3d/7d/21d/30d).
        if (threshold in listOf(24, 72, 168, 504, 720)) weight += 50.0
        return weight
    }

    private fun calculateProximityWeight(hoursUntil: Int, baseWeight: Double): Double = baseWeight + when (hoursUntil) {
        in 0..1 -> 55.0
        in 2..6 -> 30.0
        in 7..24 -> 15.0
        else -> 0.0
    }

    private fun hoursWithoutWeed(): Int = (secondsWithoutWeed() / 3600L).toInt()

    private fun secondsWithoutWeed(): Long =
        ((now().toEpochMilliseconds() - program.lastSmoked.toEpochMilliseconds()) / 1000L).coerceAtLeast(0L)

    private fun weeklyDaysCheckedIn(endDate: PlainDate, days: Int): Int =
        (0 until days).count { program.dayInfo[endDate.adding(days = -it)]?.sober != null }

    private fun weeklyDaysSober(endDate: PlainDate, days: Int): Int =
        (0 until days).count { program.dayInfo[endDate.adding(days = -it)]?.sober == true }

    private fun weeklyDaysCustomCheckIn(endDate: PlainDate, days: Int, checkIn: CustomCheckIn): Int =
        (0 until days).count { i ->
            program.dayInfo[endDate.adding(days = -i)]
                ?.loggedCheckIns?.firstOrNull { it.id == checkIn.id }?.completion == true
        }

    private fun calculateCurrentStreak(endDate: PlainDate): Int {
        var streak = 0
        var d = endDate
        while (program.dayInfo[d]?.sober == true) {
            streak++
            d = d.adding(days = -1)
        }
        return streak
    }

    private fun calculateCustomCheckInStreak(endDate: PlainDate, checkIn: CustomCheckIn): Int {
        var streak = 0
        var d = endDate
        while (program.dayInfo[d]?.loggedCheckIns?.firstOrNull { it.id == checkIn.id }?.completion == true) {
            streak++
            d = d.adding(days = -1)
        }
        return streak
    }

    /** The break-reason selections mapped to their enum (iOS `getBreakReasons`). */
    private fun breakReasons(): List<BreakReasonType> {
        val response = program.currentBreak?.getAssessmentResponse(AssessmentQuestionID.BREAK_REASON.raw)
            ?: return emptyList()
        val options = response.question.options
        return response.responses
            .mapNotNull { options.getOrNull(it) }
            .mapNotNull { BreakReasonType.from(it) }
    }

    /**
     * % decrease in smoking frequency vs the assessment baseline (iOS
     * `getDeltaSmokingFrequency`, ProgramAssessment.swift:439): the current
     * smoked-day rate over the break vs the initial weekly frequency.
     */
    private fun getDeltaSmokingFrequency(programBreak: ProgramBreak): Int? {
        val initialWeeklyUsage = programBreak.getInitialWeeklyUsage() ?: return null
        val initialWeeklyFrequency = (initialWeeklyUsage / 7.0f) * 100.0f
        if (initialWeeklyFrequency <= 0f) return null

        val start = PlainDate.from(programBreak.startDate)
        val today = PlainDate.from(now())
        val entries = program.dayInfo.filterKeys { it in start..today }.values
        val numDaysCheckedIn = entries.count { it.sober != null }.toFloat()
        val numDaysSober = entries.count { it.sober == true }.toFloat()

        val currentFrequency =
            if (numDaysCheckedIn != 0f) ((numDaysCheckedIn - numDaysSober) / numDaysCheckedIn) * 100.0f
            else initialWeeklyFrequency
        return (((initialWeeklyFrequency - currentFrequency) / initialWeeklyFrequency) * 100.0f).toInt()
    }

    private fun pick(list: List<String>, seed: Int): String = list[abs(seed) % list.size]

    companion object {
        private const val CLIENT_NAME = "_CLIENTNAME_"
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}
