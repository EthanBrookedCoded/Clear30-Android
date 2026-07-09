package org.clear30.data

import kotlinx.datetime.Instant
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.util.isToday

/** Which static reward to favor on a sober check-in (iOS `StaticRewardOption`). */
enum class StaticRewardOption { TIMER_ONLY, MONEY_ONLY, BOTH }

/** One stat card on a smoked-day reward (iOS `SmokedStatsCard`). */
sealed interface SmokedStatsCard {
    data class MoneySaved(val amount: Int) : SmokedStatsCard
    data class DaysWithoutWeed(val days: Int) : SmokedStatsCard
    data class ProgressThroughBreak(val current: Int, val max: Int, val name: String) : SmokedStatsCard
}

/** The payload of a generated static reward (iOS `StaticRewardData`). */
sealed interface StaticRewardData {
    data class WeedFreeTimer(val lastSmoked: Instant) : StaticRewardData
    data class MoneySaved(val amount: Int) : StaticRewardData
    data class TimerAndMoney(val lastSmoked: Instant, val amount: Int) : StaticRewardData
    data class SmokedStats(val cards: List<SmokedStatsCard>) : StaticRewardData
}

/** A generated static reward (iOS `StaticReward`). */
data class StaticReward(val data: StaticRewardData) {
    /** Smoked-stats only echo into the calendar when exactly two cards qualified. */
    val shouldShowInCalendarDayInfo: Boolean
        get() = when (val d = data) {
            is StaticRewardData.SmokedStats -> d.cards.size == 2
            else -> true
        }
}

/**
 * CheckInRewardStaticGenerator — ported from CheckInRewardStaticGenerator.swift.
 *
 * Produces the deterministic post-check-in reward: a weed-free timer or money-saved
 * card on a sober day, or up to two stat cards (money / days-without-weed / break
 * progress) on a smoked day. Day-0 / first-launch users are rigged to the timer so
 * their very first reward always feels like progress.
 *
 * The richer [org.clear30.data.model.VariableRewardType] reward generator (weighted,
 * personalized pop-ins) is a much larger port and remains a follow-up; this static
 * generator is the reward every check-in is guaranteed to surface.
 */
object CheckInRewardStaticGenerator {

    fun generate(
        userInfo: UserInfo,
        program: Program,
        checkInDate: PlainDate,
        option: StaticRewardOption = StaticRewardOption.BOTH,
    ): StaticReward? {
        val sober = program.dayInfo[checkInDate]?.sober ?: return null

        // Rigging: day-0 or first-launch sober users always get the timer.
        val programDay = program.currentBreak?.currentBreakDay ?: program.currentDay
        if ((programDay == 0 || userInfo.firstAppOpen.isToday) && sober) {
            return StaticReward(StaticRewardData.WeedFreeTimer(program.lastSmoked))
        }

        return if (sober) didntSmokeReward(program, checkInDate, option) else smokedStatsReward(program)
    }

    private fun moneySaved(program: Program): Int? =
        program.currentBreak?.let { program.getTotalMoneySavedOverBreak(it) }

    private fun didntSmokeReward(program: Program, date: PlainDate, option: StaticRewardOption): StaticReward {
        val timer = StaticReward(StaticRewardData.WeedFreeTimer(program.lastSmoked))
        return when (option) {
            StaticRewardOption.TIMER_ONLY -> timer
            StaticRewardOption.MONEY_ONLY -> {
                val m = moneySaved(program)
                if (m == null || m <= 0) timer else StaticReward(StaticRewardData.MoneySaved(m))
            }
            StaticRewardOption.BOTH -> {
                val m = moneySaved(program)
                when {
                    m == null || m <= 5 -> timer
                    date.seed % 2 == 0 -> timer
                    else -> StaticReward(StaticRewardData.MoneySaved(m))
                }
            }
        }
    }

    private fun smokedStatsReward(program: Program): StaticReward? {
        val cards = buildList {
            moneySaved(program)?.takeIf { it > 0 }?.let { add(SmokedStatsCard.MoneySaved(it)) }
            program.numDaysSober.takeIf { it > 0 }?.let { add(SmokedStatsCard.DaysWithoutWeed(it)) }
            program.currentBreak?.takeIf { it.currentBreakDay > 0 }?.let {
                add(SmokedStatsCard.ProgressThroughBreak(it.currentBreakDay, it.type.raw, it.name))
            }
        }.take(2)
        return if (cards.isEmpty()) null else StaticReward(StaticRewardData.SmokedStats(cards))
    }
}
